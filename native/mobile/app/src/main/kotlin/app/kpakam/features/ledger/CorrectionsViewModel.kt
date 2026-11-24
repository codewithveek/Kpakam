package app.kpakam.features.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kpakam.R
import app.kpakam.core.draft.Draft
import app.kpakam.core.ids.Ulid
import app.kpakam.core.ledger.Journal
import app.kpakam.core.ledger.JournalDrafts
import app.kpakam.shared.ui.ToastCenter
import app.kpakam.shared.ui.UiText
import app.kpakam.shared.ui.ledgerErrorLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A correction the trader is making. */
sealed interface CorrectionDialog {
    data class ConfirmUndo(val journalId: Ulid) : CorrectionDialog

    data class Fix(val journalId: Ulid, val draft: Draft) : CorrectionDialog
}

/** Undo and Fix for any entry list (History, Home, a customer's page) (F4). Feedback is a toast. */
@HiltViewModel
class CorrectionsViewModel
    @Inject
    constructor(
        private val corrections: CorrectionUseCase,
        private val toasts: ToastCenter,
    ) : ViewModel() {
        private val current = MutableStateFlow<CorrectionDialog?>(null)
        val dialog: StateFlow<CorrectionDialog?> = current.asStateFlow()

        fun askUndo(id: Ulid) {
            current.value = CorrectionDialog.ConfirmUndo(id)
        }

        fun askFix(journal: Journal, customerName: String?) {
            val draft = JournalDrafts.toDraft(journal, customerName) ?: return
            current.value = CorrectionDialog.Fix(journal.id, draft)
        }

        fun dismiss() {
            current.value = null
        }

        fun confirmUndo(id: Ulid) = run(R.string.history_undone) { corrections.undo(id) }

        fun confirmFix(id: Ulid, draft: Draft) = run(R.string.history_fix_saved) { corrections.fix(id, draft) }

        private fun run(success: Int, action: suspend () -> CorrectionResult) {
            current.value = null
            viewModelScope.launch {
                val text =
                    when (val result = action()) {
                        CorrectionResult.Done -> success
                        is CorrectionResult.Invalid -> ledgerErrorLabel(result.error)
                        CorrectionResult.NotFound, is CorrectionResult.Failed -> R.string.error_generic
                    }
                toasts.show(UiText.Res(text))
            }
        }
    }
