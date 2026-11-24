package app.kpakam.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kpakam.R

/** Minimum touch target from the accessibility rules. */
val MinTouchTarget = 48.dp

/** Page side padding, like the web's px-4. */
val PageGutter = 16.dp

/** Rounded card corner sizes. */
object Radii {
    val row = RoundedCornerShape(24.dp)
    val card = RoundedCornerShape(28.dp)
    val hero = RoundedCornerShape(32.dp)
    val tile = RoundedCornerShape(14.dp)
}

/** A large page title with tight tracking; an optional second line in grey (two-tone title). */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.padding(top = 8.dp).semantics(mergeDescendants = true) { heading() }) {
        Text(title, style = KpakamType.pageTitle, color = Kpakam.colors.text)
        subtitle?.let { Text(it, style = KpakamType.pageTitle, color = Kpakam.colors.muted) }
    }
}

/** A section heading: small, quiet, with an optional trailing action. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp),
    ) {
        Text(
            text,
            style = KpakamType.section,
            color = Kpakam.colors.muted,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        action()
    }
}

/** A white card on the grey canvas. */
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radii.card,
    color: Color = Kpakam.colors.surface,
    contentColor: Color = Kpakam.colors.text,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = shape, color = color, contentColor = contentColor, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** The one black card on a page (dark mode: light). */
@Composable
fun InkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    KCard(
        modifier = modifier,
        shape = Radii.hero,
        color = Kpakam.colors.ink,
        contentColor = Kpakam.colors.onInk,
        padding = PaddingValues(24.dp),
        content = content,
    )
}

/** A grouped settings-style panel: one card, rows separated by hairlines. */
@Composable
fun ListPanel(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    Surface(shape = Radii.row, color = Kpakam.colors.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = Kpakam.colors.border, thickness = 1.dp)
                row()
            }
        }
    }
}

/** Button looks: black pill, white pill, grey pill, and red. */
enum class PillStyle { Primary, Secondary, Soft, Danger }

/** Pill button with a 48dp minimum touch target. [large] is 56dp tall. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    large: Boolean = false,
    busy: Boolean = false,
) {
    val colors = Kpakam.colors
    val (bg, fg) =
        when {
            !enabled && style == PillStyle.Primary -> colors.surface3 to colors.muted
            style == PillStyle.Primary -> colors.ink to colors.onInk
            style == PillStyle.Secondary -> colors.surface to colors.text
            style == PillStyle.Soft -> colors.surface2 to colors.text
            else -> colors.dangerSoft to colors.danger
        }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = bg,
        contentColor = fg.copy(alpha = if (enabled || style == PillStyle.Primary) 1f else 0.4f),
        border = if (style == PillStyle.Secondary) BorderStroke(1.dp, colors.border) else null,
        modifier = modifier.heightIn(min = if (large) 56.dp else MinTouchTarget),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = if (large) 24.dp else 20.dp, vertical = 8.dp),
        ) {
            when {
                busy -> Spinner(size = 16.dp)
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text, style = if (large) KpakamType.buttonLarge else KpakamType.button, maxLines = 1)
        }
    }
}

/** Round icon-only tones. */
enum class RoundTone { Surface, Soft, Ink }

/** A round 48dp icon-only button. [label] is read by screen readers. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: RoundTone = RoundTone.Surface,
    enabled: Boolean = true,
    size: Dp = MinTouchTarget,
) {
    val colors = Kpakam.colors
    val (bg, fg) =
        when (tone) {
            RoundTone.Surface -> colors.surface to colors.text
            RoundTone.Soft -> colors.surface2 to colors.text
            RoundTone.Ink -> colors.ink to colors.onInk
        }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = bg,
        contentColor = fg.copy(alpha = if (enabled) 1f else 0.4f),
        border = if (tone == RoundTone.Surface) BorderStroke(1.dp, colors.border) else null,
        modifier = modifier.size(size).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/** A monochrome tile: an icon for entries and settings rows. Decorative; the row text carries meaning. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = Kpakam.colors.surface2,
    tint: Color = Kpakam.colors.text,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(44.dp).clip(Radii.tile).background(background),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Initials in a grey tile, for people. [large] is the 80dp tile on the customer page. */
@Composable
fun InitialsTile(name: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val size = if (large) 80.dp else 44.dp
    val shape = if (large) RoundedCornerShape(26.dp) else Radii.tile
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(Kpakam.colors.surface2)
                .clearAndSetSemantics {},
    ) {
        Text(
            Initials.of(name),
            style =
                if (large) {
                    KpakamType.statAmount
                } else {
                    KpakamType.rowTitle.copy(
                        fontSize = KpakamType.small.fontSize,
                    )
                },
            color = Kpakam.colors.text,
        )
    }
}

/** The standard row layout: tile, title over subtitle, right-aligned value. */
@Composable
fun RowContent(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    subtitle: @Composable (RowScope.() -> Unit)? = null,
    trailing: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = KpakamType.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                CompositionLocalProvider(LocalContentColor provides Kpakam.colors.muted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        content = it,
                    )
                }
            }
        }
        trailing?.let {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), content = it)
        }
    }
}

/** A row that is one tap target (whole card), with a button role. */
fun Modifier.rowClick(onClick: () -> Unit): Modifier = clickable(role = Role.Button, onClick = onClick)

/** Small inline text for row subtitles. */
@Composable
fun SubtitleText(
    text: String,
    color: Color = LocalContentColor.current,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        style = KpakamType.small,
        color = color,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A small round progress spinner in the current content colour. */
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    CircularProgressIndicator(
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
        modifier = modifier.size(size),
    )
}

/** Network status as a plain word on a soft colour (green online, orange "warn" offline), never colour alone. */
@Composable
fun NetworkPill(online: Boolean, modifier: Modifier = Modifier) {
    val colors = Kpakam.colors
    val full = stringResource(R.string.status_offline)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(CircleShape)
                .background(if (online) colors.incomeSoft else colors.warnSoft)
                .heightIn(min = 36.dp)
                .padding(horizontal = 16.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (!online) contentDescription = full
                },
    ) {
        Text(
            stringResource(if (online) R.string.status_online else R.string.status_offline_short),
            style = KpakamType.small,
            fontWeight = FontWeight.SemiBold,
            color = if (online) colors.income else colors.warn,
        )
    }
}
