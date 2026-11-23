package app.kpakam.features.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.core.draft.Draft
import app.kpakam.core.ids.CustomerId
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalDrafts
import app.kpakam.core.ledger.JournalKind
import app.kpakam.core.ledger.LedgerError
import app.kpakam.features.customers.CustomerRepository
import app.kpakam.shared.time.LagosDates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** One entry in the history list. */
data class HistoryItem(val record: JournalRecord, val customerName: String?, val canCorrect: Boolean)

/** Entries of one day. */
data class DayGroup(val day: String, val items: List<HistoryItem>)

/** A correction the trader is making. */
sealed interface CorrectionDialog {
    data class ConfirmUndo(val journalId: Ulid) : CorrectionDialog

    data class Fix(val journalId: Ulid, val draft: Draft) : CorrectionDialog
}

/** Feedback after a correction. */
sealed interface HistoryMessage {
    data object Undone : HistoryMessage

    data object Fixed : HistoryMessage

    data class Problem(val error: LedgerError?) : HistoryMessage
}

/** The history screen state. */
data class LedgerUiState(
    val days: List<DayGroup> = emptyList(),
    val dialog: CorrectionDialog? = null,
    val message: HistoryMessage? = null,
)

/** History of the last 7 days with undo and fix (F4). */
@HiltViewModel
class LedgerViewModel
    @Inject
    constructor(
        ledger: LedgerRepository,
        customers: CustomerRepository,
        private val corrections: CorrectionUseCase,
        clock: Clock,
    ) : ViewModel() {
        private val local = MutableStateFlow(LedgerUiState())

        val state: StateFlow<LedgerUiState> =
            combine(
                ledger.journalsBetween(LagosDates.daysAgo(clock, HISTORY_DAYS), LagosDates.today(clock)),
                customers.customers,
                local,
            ) { records, customerList, ui ->
                val names = customerList.associate { it.id to it.name }
                ui.copy(days = group(records, names))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LedgerUiState())

        fun askUndo(id: Ulid) = local.update { it.copy(dialog = CorrectionDialog.ConfirmUndo(id), message = null) }

        fun askFix(item: HistoryItem) {
            val draft = JournalDrafts.toDraft(item.record.journal, item.customerName) ?: return
            local.update { it.copy(dialog = CorrectionDialog.Fix(item.record.journal.id, draft), message = null) }
        }

        fun dismissDialog() = local.update { it.copy(dialog = null) }

        fun dismissMessage() = local.update { it.copy(message = null) }

        fun confirmUndo(id: Ulid) = runCorrection(HistoryMessage.Undone) { corrections.undo(id) }

        fun confirmFix(id: Ulid, draft: Draft) = runCorrection(HistoryMessage.Fixed) { corrections.fix(id, draft) }

        private fun runCorrection(success: HistoryMessage, action: suspend () -> CorrectionResult) {
            local.update { it.copy(dialog = null) }
            viewModelScope.launch {
                val message =
                    when (val result = action()) {
                        CorrectionResult.Done -> success
                        is CorrectionResult.Invalid -> HistoryMessage.Problem(result.error)
                        CorrectionResult.NotFound, is CorrectionResult.Failed -> HistoryMessage.Problem(null)
                    }
                local.update { it.copy(message = message) }
            }
        }

        private fun group(records: List<JournalRecord>, names: Map<CustomerId, String>): List<DayGroup> {
            val reversed = records.mapNotNull { it.journal.reverses }.toSet()
            return records
                .groupBy { it.journal.day }
                .toSortedMap(compareByDescending { it })
                .map { (day, dayRecords) ->
                    DayGroup(
                        day,
                        dayRecords.map { HistoryItem(it, nameOf(it.journal, names), canCorrect(it.journal, reversed)) },
                    )
                }
        }

        private fun nameOf(journal: Journal, names: Map<CustomerId, String>) = journal.customerId?.let { names[it] }

        private fun canCorrect(journal: Journal, reversed: Set<Ulid>) =
            journal.kind != JournalKind.REVERSAL && journal.id !in reversed

        private companion object {
            const val HISTORY_DAYS = 6L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
