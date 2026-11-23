package app.kpakam.features.quickentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.routing.QueueReason
import app.kpakam.shared.ui.DraftEditorDialog
import app.kpakam.shared.ui.MinTouchTarget
import app.kpakam.shared.ui.OfflineBanner
import app.kpakam.shared.ui.SectionHeader

/** Route entry point: connects the ViewModel to the stateless content. */
@Composable
fun QuickEntryScreen(viewModel: QuickEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuickEntryContent(state, viewModel)
    state.editingIndex?.let { index ->
        state.drafts.getOrNull(index)?.let { item ->
            DraftEditorDialog(item.draft, onDone = { viewModel.saveEdit(index, it) }, onDismiss = viewModel::cancelEdit)
        }
    }
}

@Composable
private fun QuickEntryContent(state: QuickEntryUiState, viewModel: QuickEntryViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(stringResource(R.string.entry_title)) }
        if (!state.online) item { OfflineBanner() }
        item { NoteInput(state, viewModel::onNoteChange, viewModel::submit, viewModel::clearNote) }
        item { VoiceButton(state.phase, onTap = viewModel::onMicTap, onDenied = viewModel::onMicDenied) }
        if (state.phase == EntryPhase.Parsing || state.phase == EntryPhase.Saving) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.message?.let { message -> item { MessageText(message, viewModel::dismissMessage) } }
        state.transcript?.let { item { Text(stringResource(R.string.entry_transcript, it)) } }
        if (state.drafts.isNotEmpty()) item { DraftList(state, viewModel) }
        if (state.jobs.isNotEmpty()) item { PendingJobs(state.jobs, viewModel) }
    }
}

@Composable
private fun NoteInput(state: QuickEntryUiState, onChange: (String) -> Unit, onSubmit: () -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = state.note,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.entry_label)) },
        placeholder = { Text(stringResource(R.string.entry_hint)) },
        supportingText = {
            Text(stringResource(R.string.entry_counter, state.note.length, QuickEntryUiState.MAX_NOTE_CHARS))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        enabled = !state.busy,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text(stringResource(submitLabel(state.phase)))
        }
        if (state.note.isNotEmpty()) {
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.entry_clear))
            }
        }
    }
}

@Composable
private fun MessageText(message: EntryMessage, onDismiss: () -> Unit) {
    val text =
        when (message) {
            is EntryMessage.Clarify -> stringResource(R.string.entry_clarify, message.question)
            EntryMessage.ReadFailed -> stringResource(R.string.entry_failed)
            is EntryMessage.Queued ->
                stringResource(
                    R.string.entry_queued,
                    stringResource(queuedWhen(message.queueReason)),
                )
            is EntryMessage.Saved -> stringResource(R.string.drafts_saved, message.count)
            EntryMessage.AlreadySaved -> stringResource(R.string.drafts_duplicate)
            EntryMessage.NoSpeech -> stringResource(R.string.entry_no_speech)
            EntryMessage.MicDenied -> stringResource(R.string.entry_mic_denied)
            EntryMessage.SaveFailed -> stringResource(R.string.draft_error_other)
        }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text(stringResource(R.string.drafts_discard))
        }
    }
}

private fun submitLabel(phase: EntryPhase): Int =
    if (phase == EntryPhase.Parsing) R.string.entry_working else R.string.entry_submit

private fun queuedWhen(reason: QueueReason): Int =
    when (reason) {
        QueueReason.OFFLINE -> R.string.entry_queued_offline
        QueueReason.DATA_SAVER -> R.string.entry_queued_data_saver
        QueueReason.CLOUD_DISABLED -> R.string.entry_queued_cloud_disabled
        QueueReason.RATE_LIMITED -> R.string.entry_queued_rate_limited
    }
