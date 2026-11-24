package app.kpakam.shared.ui

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val BARS = 44
private const val MIN_SCALE = 0.08f
private const val TICK_MS = 50L
private const val LOUD_BARS = 6

/** A calm, still shape for when there is no live sound (also used when animations are off). */
private val IdleShape = List(BARS) { i -> 0.18f + 0.5f * abs(sin(i * 0.55f)) * abs(cos(i * 0.21f)) }

/** `true` when the phone's animations are turned off (animator duration scale 0). */
@Composable
fun animationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * A recording sound wave (web soundwave.tsx): 44 thin rounded bars, about 3dp apart, that rise with
 * the speaker's real loudness and scroll from right to left. It freezes when [active] is false.
 * Decorative: the timer and status text carry the meaning.
 */
@Composable
fun Soundwave(level: () -> Float, active: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = animationsOff()
    val history = remember { mutableStateListOf<Float>().apply { addAll(IdleShape) } }
    val currentLevel by rememberUpdatedState(level)
    LaunchedEffect(active, reduceMotion) {
        if (!active || reduceMotion) return@LaunchedEffect
        history.replaceAll { MIN_SCALE }
        while (true) {
            delay(TICK_MS)
            history.removeAt(0)
            history.add(currentLevel().coerceIn(0f, 1f))
        }
    }
    val color = Kpakam.colors.text
    val soft = color.copy(alpha = 0.8f)
    Canvas(modifier.fillMaxWidth().height(96.dp).clearAndSetSemantics {}) {
        val bar = 3.dp.toPx()
        val gap = 3.dp.toPx()
        val total = BARS * bar + (BARS - 1) * gap
        val start = (size.width - total) / 2 + bar / 2
        history.forEachIndexed { i, value ->
            val height = size.height * value.coerceAtLeast(MIN_SCALE)
            val x = start + i * (bar + gap)
            val top = (size.height - height) / 2 + bar / 2
            val bottom = (size.height + height) / 2 - bar / 2
            drawLine(
                color = if (i >= BARS - LOUD_BARS) color else soft,
                start = Offset(x, top),
                end = Offset(x, bottom.coerceAtLeast(top)),
                strokeWidth = bar,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A gentle pulse for the red "Listening" dot; still when animations are off. */
@Composable
fun pulseAlpha(running: Boolean): Float {
    val reduceMotion = animationsOff()
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "pulse",
    )
    return if (running && !reduceMotion) alpha else 1f
}
