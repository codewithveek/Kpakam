package app.kpakam.features.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.R
import app.kpakam.core.draft.Draft
import app.kpakam.core.draft.DraftKind
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.IdGenerator
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Balances
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.ledger.JournalSource
import app.kpakam.core.money.Kobo
import app.kpakam.core.money.sumKobo
import app.kpakam.features.ledger.JournalRecord
import app.kpakam.features.ledger.LedgerRepository
import app.kpakam.features.quickentry.ConfirmDraftsUseCase
import app.kpakam.features.quickentry.ConfirmResult
import app.kpakam.features.quickentry.ConfirmableDraft
import app.kpakam.shared.ui.ToastCenter
import app.kpakam.shared.ui.UiText
import app.kpakam.shared.ui.ledgerErrorLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Who owes you" list state. [debtors] is how many customers owe money. */
data class CustomersUiState(
    val customers: List<Customer> = emptyList(),
    val totalOwed: Kobo = Kobo.ZERO,
    val debtors: Int = 0,
    val loading: Boolean = true,
)

/** The debt book list (F3). */
@HiltViewModel
class CustomersViewModel
    @Inject
    constructor(repository: CustomerRepository) : ViewModel() {
        val state: StateFlow<CustomersUiState> =
            repository.customers
                .map { list ->
                    val owing = list.map { it.cachedBalance }.filter { it.value > 0 }
                    CustomersUiState(list, owing.sumKobo(), owing.size, loading = false)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CustomersUiState())
    }

/** One customer's page. [balance] is derived from journal lines, never the stored number. */
data class CustomerDetailUiState(
    val customer: Customer? = null,
    val balance: Kobo = Kobo.ZERO,
    val entries: List<JournalRecord> = emptyList(),
    val reversed: Set<Ulid> = emptySet(),
    val loading: Boolean = true,
)

/** One customer's entries and derived balance, and "Record payment" (F3). */
@HiltViewModel
class CustomerDetailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        customers: CustomerRepository,
        ledger: LedgerRepository,
        private val confirm: ConfirmDraftsUseCase,
        private val ids: IdGenerator,
        private val toasts: ToastCenter,
    ) : ViewModel() {
        private val customerId: CustomerId? = savedState.get<String>(ARG_ID)?.let(CustomerId::parse)

        /** The amount typed in the payment bar. */
        val paymentText = MutableStateFlow("")

        val state: StateFlow<CustomerDetailUiState> =
            customerId
                ?.let { id ->
                    combine(customers.customer(id), ledger.journalsForCustomer(id)) { customer, records ->
                        CustomerDetailUiState(
                            customer,
                            Balances.customerBalance(records.map { it.journal }, id),
                            records,
                            records.mapNotNull { it.journal.reverses }.toSet(),
                            loading = false,
                        )
                    }
                }.let { it ?: emptyFlow() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CustomerDetailUiState())

        fun onPaymentChange(text: String) {
            paymentText.value = text.filter { it.isDigit() || it == '.' || it == ',' }.take(MAX_AMOUNT_CHARS)
        }

        /** Saves a repayment typed by the trader. */
        fun recordPayment() {
            val customer = state.value.customer ?: return
            val naira =
                paymentText.value
                    .replace(",", "")
                    .toDoubleOrNull()
                    ?.takeIf { it > 0 } ?: return
            val draft = Draft(DraftKind.REPAYMENT, totalNaira = naira, customerName = customer.name, confidence = 1.0)
            val item = ConfirmableDraft(draft, ids.next(), "manual:${ids.next().value}")
            viewModelScope.launch {
                val text =
                    when (
                        val result =
                            confirm.confirm(
                                listOf(item),
                                JournalSource(InferenceSource.MANUAL, MANUAL, null),
                            )
                    ) {
                        is ConfirmResult.Saved, ConfirmResult.AlreadySaved -> {
                            paymentText.value = ""
                            UiText.Plural(R.plurals.drafts_saved, 1)
                        }
                        is ConfirmResult.Invalid -> UiText.Res(ledgerErrorLabel(result.error))
                        is ConfirmResult.Failed -> UiText.Res(R.string.error_save_failed)
                    }
                toasts.show(text)
            }
        }

        companion object {
            const val ARG_ID = "customerId"
            private const val MANUAL = "MANUAL"
            private const val MAX_AMOUNT_CHARS = 12
        }
    }

private const val STOP_TIMEOUT_MS = 5_000L
