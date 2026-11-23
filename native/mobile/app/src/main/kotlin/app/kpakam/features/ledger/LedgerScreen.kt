package app.kpakam.features.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.shared.time.LagosDates
import app.kpakam.shared.ui.DraftEditorDialog
import app.kpakam.shared.ui.JournalRow
import app.kpakam.shared.ui.MinTouchTarget
import app.kpakam.shared.ui.SectionHeader
import app.kpakam.shared.ui.ledgerErrorLabel

/** History with undo and fix. */
@Composable
fun LedgerScreen(viewModel: LedgerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionHeader(stringResource(R.string.history_title)) }
        state.message?.let { message -> item { HistoryMessageRow(message, viewModel::dismissMessage) } }
        if (state.days.isEmpty()) item { Text(stringResource(R.string.history_empty)) }
        state.days.forEach { group ->
            item(key = group.day) { SectionHeader(LagosDates.dayHeader(group.day)) }
            items(group.items, key = { it.record.journal.id.value }) { item ->
                HistoryRow(
                    item,
                    onUndo = { viewModel.askUndo(item.record.journal.id) },
                    onFix = { viewModel.askFix(item) },
                )
                HorizontalDivider()
            }
        }
    }
    CorrectionDialogs(state.dialog, viewModel)
}

@Composable
private fun HistoryRow(item: HistoryItem, onUndo: () -> Unit, onFix: () -> Unit) {
    JournalRow(item.record.journal, item.record.pendingSync, item.customerName)
    if (item.canCorrect) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.history_undo))
            }
            TextButton(onClick = onFix, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.history_edit))
            }
        }
    }
}

@Composable
private fun HistoryMessageRow(message: HistoryMessage, onDismiss: () -> Unit) {
    val text =
        when (message) {
            HistoryMessage.Undone -> stringResource(R.string.history_undone)
            HistoryMessage.Fixed -> stringResource(R.string.history_fix_saved)
            is HistoryMessage.Problem ->
                stringResource(
                    message.error?.let(::ledgerErrorLabel) ?: R.string.error_generic,
                )
        }
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text(stringResource(R.string.drafts_discard))
        }
    }
}

@Composable
private fun CorrectionDialogs(dialog: CorrectionDialog?, viewModel: LedgerViewModel) {
    when (dialog) {
        is CorrectionDialog.ConfirmUndo ->
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                text = { Text(stringResource(R.string.history_undo_confirm)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmUndo(dialog.journalId) }) {
                        Text(stringResource(R.string.history_undo))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.edit_cancel)) }
                },
            )
        is CorrectionDialog.Fix ->
            DraftEditorDialog(
                draft = dialog.draft,
                onDone = { viewModel.confirmFix(dialog.journalId, it) },
                onDismiss = viewModel::dismissDialog,
            )
        null -> Unit
    }
}
