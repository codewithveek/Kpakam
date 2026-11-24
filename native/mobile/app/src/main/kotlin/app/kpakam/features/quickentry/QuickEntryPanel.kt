package app.kpakam.features.quickentry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kpakam.R
import app.kpakam.core.routing.QueueReason
import app.kpakam.shared.ui.KCard
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.MinTouchTarget
import app.kpakam.shared.ui.PillButton
import app.kpakam.shared.ui.RoundIconButton
import app.kpakam.shared.ui.RoundTone

private const val SHOW_COUNT_BELOW = 40
private const val WARN_BELOW = 10

/** Example chips: the label shown, and the words added to the note. */
private val EXAMPLES =
    listOf(
        R.string.example_sold to "sold ",
        R.string.example_bought to "bought ",
        R.string.example_paid_me to " paid me ",
        R.string.example_owes_me to " owes me ",
    )

/** Returns an action that opens the voice screen, asking for the microphone the first time. */
@Composable
fun rememberSpeakAction(voice: VoiceViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) voice.open() else voice.onMicDenied()
        }
    return {
        if (hasMicPermission(context)) voice.open() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** F1 + F8: type one sentence, or tap the mic to speak it; Kpakam turns it into entries. */
@Composable
fun QuickEntryPanel(
    state: QuickEntryUiState,
    viewModel: QuickEntryViewModel,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NoteField(state, viewModel)
            if (state.note.isEmpty()) {
                ExampleChips(viewModel::addExample)
            } else {
                CharsLeft(
                    QuickEntryUiState.MAX_NOTE_CHARS - state.note.length,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(
                    Icons.Outlined.Mic,
                    stringResource(R.string.voice_start),
                    onSpeak,
                    tone = RoundTone.Soft,
                    enabled = !state.busy,
                )
                Box(Modifier.weight(1f))
                PillButton(
                    text = stringResource(R.string.entry_submit),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    busy = state.phase == EntryPhase.Parsing,
                    icon = Icons.AutoMirrored.Outlined.Send,
                )
            }
        }
    }
}

@Composable
private fun NoteField(state: QuickEntryUiState, viewModel: QuickEntryViewModel) {
    val colors = Kpakam.colors
    val label = stringResource(R.string.entry_label)
    Box {
        if (state.note.isEmpty()) {
            Text(stringResource(R.string.entry_hint), style = KpakamType.note, color = colors.muted)
        }
        BasicTextField(
            value = state.note,
            onValueChange = viewModel::onNoteChange,
            enabled = !state.busy,
            textStyle = KpakamType.note.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            minLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.submit() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

@Composable
private fun ExampleChips(onPick: (String) -> Unit) {
    val colors = Kpakam.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        EXAMPLES.forEach { (label, text) ->
            Surface(
                onClick = { onPick(text) },
                shape = CircleShape,
                color = colors.surface2,
                contentColor = colors.text,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.heightIn(min = MinTouchTarget).padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(label), style = KpakamType.chip, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CharsLeft(left: Int) {
    if (left > SHOW_COUNT_BELOW) return
    Text(
        pluralStringResource(R.plurals.entry_chars_left, left, left),
        style = KpakamType.small,
        fontWeight = if (left < WARN_BELOW) FontWeight.SemiBold else null,
        color = if (left < WARN_BELOW) Kpakam.colors.accent else Kpakam.colors.muted,
    )
}

/** A message about the last note (needs more detail, could not read, saved for later). */
@Composable
fun EntryMessageCard(message: EntryMessage, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Kpakam.colors
    val text =
        when (message) {
            is EntryMessage.Clarify -> stringResource(R.string.entry_clarify, message.question)
            EntryMessage.ReadFailed -> stringResource(R.string.entry_failed)
            is EntryMessage.Queued -> stringResource(queuedWhen(message.queueReason))
            EntryMessage.SaveFailed -> stringResource(R.string.error_save_failed)
        }
    val error = message == EntryMessage.ReadFailed || message == EntryMessage.SaveFailed
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (error) colors.dangerSoft else colors.surface)
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
    ) {
        Text(
            text,
            style = KpakamType.body,
            color = if (error) colors.danger else colors.text,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.drafts_discard),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The sentence for a note saved until it can be read. */
fun queuedWhen(reason: QueueReason): Int =
    when (reason) {
        QueueReason.OFFLINE -> R.string.entry_queued_offline
        QueueReason.DATA_SAVER -> R.string.entry_queued_data_saver
        QueueReason.CLOUD_DISABLED -> R.string.entry_queued_cloud_disabled
        QueueReason.RATE_LIMITED -> R.string.entry_queued_rate_limited
    }
