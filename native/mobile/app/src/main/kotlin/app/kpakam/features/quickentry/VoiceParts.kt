package app.kpakam.features.quickentry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.routing.QueueReason
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.PillButton
import app.kpakam.shared.ui.PillStyle
import app.kpakam.shared.ui.Spinner

/** The words part of the voice screen, for each state. */
@Composable
internal fun ColumnScope.WordsBody(state: VoiceUi, voice: VoiceViewModel) {
    val colors = Kpakam.colors
    when (state) {
        is VoiceUi.Review -> ReviewWords(state.words, voice::editWords)
        is VoiceUi.Listening ->
            when {
                !state.live -> Hint(stringResource(R.string.voice_words_later))
                state.partial.isEmpty() -> Hint(stringResource(R.string.voice_live_hint))
                else ->
                    Text(
                        state.partial,
                        style = KpakamType.spokenWords,
                        color = colors.text,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
            }
        is VoiceUi.Finished ->
            Text(
                stringResource(noteText(state.note)),
                style = KpakamType.cardTitle,
                color = if (state.isError) colors.danger else colors.text,
                modifier =
                    Modifier.semantics {
                        liveRegion = if (state.isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
                    },
            )
        is VoiceUi.Processing ->
            Working(
                stringResource(if (state.mixed) R.string.voice_mixed else R.string.entry_working),
            )
        VoiceUi.Preparing, VoiceUi.Closed -> Working(stringResource(R.string.voice_preparing))
    }
}

@Composable
private fun ColumnScope.ReviewWords(words: String, onChange: (String) -> Unit) {
    val colors = Kpakam.colors
    val label = stringResource(R.string.voice_live_words)
    BasicTextField(
        value = words,
        onValueChange = onChange,
        textStyle = KpakamType.spokenWords.copy(color = colors.text),
        cursorBrush = SolidColor(colors.text),
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface2)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = label },
    )
    Text(
        stringResource(R.string.voice_review_hint),
        style = KpakamType.small,
        color = colors.muted,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = KpakamType.hint,
        color = Kpakam.colors.muted,
    )
}

@Composable
private fun Working(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Spinner()
        Hint(text)
    }
}

private class VoiceButtonSpec(
    val text: Int,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

/** The two buttons at the bottom: Cancel + Done, Say again + Send, or Say again + Close. */
@Composable
internal fun VoiceActions(state: VoiceUi, voice: VoiceViewModel, onAgain: () -> Unit) {
    val again = VoiceButtonSpec(R.string.voice_say_again, onAgain, Icons.Outlined.Mic)
    val (left, right) =
        when (state) {
            is VoiceUi.Review ->
                again to
                    VoiceButtonSpec(
                        R.string.entry_submit,
                        voice::send,
                        Icons.AutoMirrored.Outlined.Send,
                        state.words.isNotBlank(),
                    )
            is VoiceUi.Finished -> again to VoiceButtonSpec(R.string.voice_close, voice::close)
            else ->
                VoiceButtonSpec(R.string.voice_cancel, voice::close) to
                    VoiceButtonSpec(R.string.voice_done, voice::finish, Icons.Outlined.Stop, state is VoiceUi.Listening)
        }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        PillButton(
            stringResource(left.text),
            left.onClick,
            Modifier.weight(1f),
            PillStyle.Secondary,
            left.icon,
            large = true,
        )
        PillButton(
            stringResource(right.text),
            right.onClick,
            Modifier.weight(1f),
            icon = right.icon,
            enabled = right.enabled,
            large = true,
        )
    }
}

private fun noteText(note: VoiceNote): Int =
    when (note) {
        is VoiceNote.Queued ->
            if (note.queueReason ==
                app.kpakam.core.routing.QueueReason.OFFLINE
            ) {
                R.string.voice_queued
            } else {
                queuedWhen(note.queueReason)
            }
        VoiceNote.NothingHeard -> R.string.voice_nothing_heard
        VoiceNote.ReadFailed -> R.string.entry_failed
        VoiceNote.MicDenied -> R.string.voice_mic_denied
    }
