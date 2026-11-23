package app.kpakam.features.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ledger.Balances
import app.kpakam.core.money.Kobo
import app.kpakam.core.money.sumKobo
import app.kpakam.features.ledger.JournalRecord
import app.kpakam.features.ledger.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** "Who owes you" list state. */
data class CustomersUiState(val customers: List<Customer> = emptyList(), val totalOwed: Kobo = Kobo.ZERO)

/** The debt book list (F3). */
@HiltViewModel
class CustomersViewModel
    @Inject
    constructor(repository: CustomerRepository) : ViewModel() {
        val state: StateFlow<CustomersUiState> =
            repository.customers
                .map { list -> CustomersUiState(list, list.map { it.cachedBalance }.filter { it.value > 0 }.sumKobo()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CustomersUiState())
    }

/** One customer's page. [balance] is derived from journal lines, never the stored number. */
data class CustomerDetailUiState(
    val customer: Customer? = null,
    val balance: Kobo = Kobo.ZERO,
    val entries: List<JournalRecord> = emptyList(),
)

/** One customer's entries and derived balance (F3). */
@HiltViewModel
class CustomerDetailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        customers: CustomerRepository,
        ledger: LedgerRepository,
    ) : ViewModel() {
        private val customerId: CustomerId? = savedState.get<String>(ARG_ID)?.let(CustomerId::parse)

        val state: StateFlow<CustomerDetailUiState> =
            customerId
                ?.let { id ->
                    combine(customers.customer(id), ledger.journalsForCustomer(id)) { customer, records ->
                        CustomerDetailUiState(
                            customer,
                            Balances.customerBalance(records.map { it.journal }, id),
                            records,
                        )
                    }
                }.let { it ?: emptyFlow() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CustomerDetailUiState())

        companion object {
            const val ARG_ID = "customerId"
        }
    }

private const val STOP_TIMEOUT_MS = 5_000L
