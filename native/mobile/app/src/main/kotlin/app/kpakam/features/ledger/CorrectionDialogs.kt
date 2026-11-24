package app.kpakam.features.ledger

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.ledger.Journal
import app.kpakam.shared.ui.DraftEditorDialog
import app.kpakam.shared.ui.EntryActions
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType

/** Undo and Fix buttons for one entry, wired to [corrections]. */
@Composable
fun rememberEntryActions(corrections: CorrectionsViewModel, journal: Journal, customerName: String?): EntryActions =
    remember(journal.id, customerName) {
        EntryActions(
            onUndo = { corrections.askUndo(journal.id) },
            onFix = { corrections.askFix(journal, customerName) },
        )
    }

/** The confirm-undo dialog and the fix editor. */
@Composable
fun CorrectionDialogs(corrections: CorrectionsViewModel) {
    val dialog by corrections.dialog.collectAsStateWithLifecycle()
    when (val current = dialog) {
        is CorrectionDialog.ConfirmUndo ->
            AlertDialog(
                onDismissRequest = corrections::dismiss,
                containerColor = Kpakam.colors.surface,
                text = { Text(stringResource(R.string.history_undo_confirm), style = KpakamType.body) },
                confirmButton = {
                    TextButton(onClick = { corrections.confirmUndo(current.journalId) }) {
                        Text(stringResource(R.string.history_undo), color = Kpakam.colors.text)
                    }
                },
                dismissButton = {
                    TextButton(onClick = corrections::dismiss) {
                        Text(stringResource(R.string.edit_cancel), color = Kpakam.colors.muted)
                    }
                },
            )
        is CorrectionDialog.Fix ->
            DraftEditorDialog(
                draft = current.draft,
                onDone = { corrections.confirmFix(current.journalId, it) },
                onDismiss = corrections::dismiss,
            )
        null -> Unit
    }
}
