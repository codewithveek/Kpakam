package app.kpakam.features.quickentry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.shared.ui.KCard
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.RoundIconButton
import app.kpakam.shared.ui.Soundwave
import app.kpakam.shared.ui.pulseAlpha
import kotlinx.coroutines.delay

private const val TIMER_TICK_MS = 250L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60

/** "mm:ss" for the big timer. */
internal fun clockText(seconds: Int): String =
    "%02d:%02d".format(
        seconds / SECONDS_PER_MINUTE,
        seconds % SECONDS_PER_MINUTE,
    )

/** F8: the full-screen voice screen (live words, sound wave, timer, then check and send). */
@Composable
fun VoiceScreen(state: VoiceUi, level: () -> Float, voice: VoiceViewModel, onAgain: () -> Unit) {
    BackHandler(onBack = voice::close)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .background(Kpakam.colors.bg)
                .safeDrawingPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Outlined.Close, stringResource(R.string.voice_close), voice::close)
            Text(
                stringResource(R.string.voice_start),
                style = KpakamType.listAmount,
                color = Kpakam.colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Spacer(Modifier.width(48.dp))
        }
        RecorderCard(state, level)
        WordsCard(state, voice)
        VoiceActions(state, voice, onAgain)
    }
}

@Composable
private fun RecorderCard(state: VoiceUi, level: () -> Float) {
    val listening = state as? VoiceUi.Listening
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(listening?.startedAtMs) {
        while (listening != null) {
            now = System.currentTimeMillis()
            delay(TIMER_TICK_MS)
        }
    }
    val elapsed = listening?.let { ((now - it.startedAtMs) / MS_PER_SECOND).toInt().coerceAtLeast(0) } ?: 0
    val seconds = if (state is VoiceUi.Review) state.seconds else elapsed
    KCard(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusPill(state)
            Soundwave(level = level, active = listening != null)
            Text(clockText(seconds), style = KpakamType.timer, color = Kpakam.colors.text)
            Text(
                if (listening !=
                    null
                ) {
                    stringResource(
                        R.string.voice_seconds_left,
                        (VoiceViewModel.MAX_SECONDS - elapsed).coerceAtLeast(0),
                    )
                } else {
                    " "
                },
                style = KpakamType.small,
                fontWeight = FontWeight.Medium,
                color = Kpakam.colors.muted,
            )
        }
    }
}

@Composable
private fun StatusPill(state: VoiceUi) {
    val colors = Kpakam.colors
    val listening = state is VoiceUi.Listening
    val text =
        when (state) {
            is VoiceUi.Listening -> R.string.voice_status_listening
            VoiceUi.Preparing -> R.string.voice_preparing
            is VoiceUi.Review, is VoiceUi.Processing -> R.string.voice_status_heard
            else -> R.string.voice_start
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (listening) colors.dangerSoft else colors.surface2)
                .heightIn(min = 32.dp)
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(pulseAlpha(listening))
                .clip(CircleShape)
                .background(if (listening) colors.live else colors.muted),
        )
        Text(
            stringResource(text),
            style = KpakamType.small,
            fontWeight = FontWeight.SemiBold,
            color = if (listening) colors.danger else colors.muted,
        )
    }
}

/** Bottom card: the words, live while speaking and editable afterwards (fix by hand or say again). */
@Composable
private fun ColumnScope.WordsCard(state: VoiceUi, voice: VoiceViewModel) {
    KCard(modifier = Modifier.weight(1f).heightIn(min = 192.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Kpakam.colors.muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(R.string.voice_live_words).uppercase(),
                style = KpakamType.eyebrow,
                color = Kpakam.colors.muted,
            )
        }
        Spacer(Modifier.size(12.dp))
        WordsBody(state, voice)
    }
}
