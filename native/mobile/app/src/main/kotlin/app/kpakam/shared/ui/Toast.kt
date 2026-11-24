package app.kpakam.shared.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Text for the UI that a ViewModel can build without a Context. */
sealed interface UiText {
    data class Res(
        @param:StringRes val id: Int,
    ) : UiText

    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
    ) : UiText
}

/** Resolves [UiText] in a composable. */
@Composable
fun UiText.resolve(): String =
    when (this) {
        is UiText.Res -> stringResource(id)
        is UiText.Plural -> pluralStringResource(id, count, count)
    }

/** A button inside a toast, such as Undo. */
data class ToastAction(
    @param:StringRes val label: Int,
    val run: () -> Unit,
)

/** One toast. [id] makes a repeated message show again. */
data class Toast(val id: Long, val text: UiText, val action: ToastAction?)

/** One toast at a time for the whole app (web shared/ui/toast.tsx). */
@Singleton
class ToastCenter
    @Inject
    constructor() {
        private val ids = AtomicLong()
        private val current = MutableStateFlow<Toast?>(null)
        val toast: StateFlow<Toast?> = current.asStateFlow()

        fun show(text: UiText, action: ToastAction? = null) {
            current.value = Toast(ids.incrementAndGet(), text, action)
        }

        fun clear(id: Long) = current.update { if (it?.id == id) null else it }
    }

private const val TOAST_MS = 3_500L
private const val TOAST_WITH_ACTION_MS = 5_000L

/** A black pill with an optional white action pill, a polite live region, above the bottom bar. */
@Composable
fun ToastHost(center: ToastCenter, modifier: Modifier = Modifier) {
    val toast by center.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast?.id) {
        val shown = toast ?: return@LaunchedEffect
        delay(if (shown.action == null) TOAST_MS else TOAST_WITH_ACTION_MS)
        center.clear(shown.id)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier.fillMaxWidth().padding(horizontal = PageGutter).semantics {
                liveRegion =
                    LiveRegionMode.Polite
            },
    ) {
        AnimatedVisibility(toast != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            toast?.let { ToastPill(it) { center.clear(it.id) } }
        }
    }
}

@Composable
private fun ToastPill(toast: Toast, onDone: () -> Unit) {
    val colors = Kpakam.colors
    Surface(
        shape = CircleShape,
        color = colors.ink,
        contentColor = colors.onInk,
        modifier = Modifier.widthIn(max = 448.dp).shadow(12.dp, CircleShape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .padding(start = 20.dp, end = if (toast.action == null) 20.dp else 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(toast.text.resolve(), style = KpakamType.body, modifier = Modifier.weight(1f, fill = false))
            toast.action?.let { action ->
                Surface(
                    onClick = {
                        action.run()
                        onDone()
                    },
                    shape = CircleShape,
                    color = colors.surface,
                    contentColor = colors.text,
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(action.label), style = KpakamType.button)
                    }
                }
            }
        }
    }
}
