package app.kpakam.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Kpakam Mono colours (web/src/styles.css). A light grey canvas, white cards, and black as the one
 * strong colour. Colour is kept for meaning: green for money in, amber for money owed, red for errors.
 * No gradients anywhere.
 */
@Immutable
data class KpakamColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val ink: Color,
    val onInk: Color,
    val onInkMuted: Color,
    val inkLine: Color,
    val accent: Color,
    val accentSoft: Color,
    val income: Color,
    val incomeSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val credit: Color,
    val danger: Color,
    val dangerSoft: Color,
    val live: Color,
    val scrim: Color,
)

private val LightPalette =
    KpakamColors(
        bg = Color(0xFFEDEDEB),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFF4F4F2),
        surface3 = Color(0xFFE8E8E5),
        border = Color(0xFFE3E3DF),
        text = Color(0xFF0D0D0D),
        muted = Color(0xFF66665F),
        ink = Color(0xFF0D0D0D),
        onInk = Color(0xFFFFFFFF),
        onInkMuted = Color(0xA8FFFFFF),
        inkLine = Color(0x24FFFFFF),
        accent = Color(0xFF8F5300),
        accentSoft = Color(0xFFF7ECD9),
        income = Color(0xFF12643A),
        incomeSoft = Color(0xFFE2F3E7),
        warn = Color(0xFF9A4400),
        warnSoft = Color(0xFFFDE3C6),
        credit = Color(0xFF8F5300),
        danger = Color(0xFFB42318),
        dangerSoft = Color(0xFFFBE8E6),
        live = Color(0xFFE5484D),
        scrim = Color(0x660D0D0D),
    )

private val DarkPalette =
    KpakamColors(
        bg = Color(0xFF0A0A0A),
        surface = Color(0xFF161616),
        surface2 = Color(0xFF202020),
        surface3 = Color(0xFF2A2A2A),
        border = Color(0xFF262626),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A39C),
        ink = Color(0xFFF4F4F2),
        onInk = Color(0xFF0D0D0D),
        onInkMuted = Color(0x9E0D0D0D),
        inkLine = Color(0x1F0D0D0D),
        accent = Color(0xFFF0B65A),
        accentSoft = Color(0xFF33280F),
        income = Color(0xFF7DD3A0),
        incomeSoft = Color(0xFF11291B),
        warn = Color(0xFFF6A55A),
        warnSoft = Color(0xFF3B230D),
        credit = Color(0xFFF0B65A),
        danger = Color(0xFFFF8F84),
        dangerSoft = Color(0xFF3A1714),
        live = Color(0xFFFF6B6F),
        scrim = Color(0x99000000),
    )

private val LocalKpakamColors = staticCompositionLocalOf { LightPalette }

/** Access to the Kpakam colours from any composable. */
object Kpakam {
    val colors: KpakamColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKpakamColors.current
}

/** Tight tracking for big display numbers and titles (-0.035em on the web). */
private val DisplayTracking = (-0.035).em

/** The type scale. System sans; big numbers are bold with tight tracking. */
object KpakamType {
    val heroAmount =
        TextStyle(fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = DisplayTracking)
    val bigAmount =
        TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = DisplayTracking)
    val timer =
        TextStyle(
            fontSize = 52.sp,
            lineHeight = 56.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = DisplayTracking,
        )
    val pageTitle =
        TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = DisplayTracking)
    val name =
        TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = DisplayTracking)
    val statAmount =
        TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val spokenWords =
        TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)
    val rowTitle =
        TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val rowAmount =
        TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    val listAmount =
        TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    val payment =
        TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    val note = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em)
    val chip = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val eyebrow =
        TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
    val dayNumber =
        TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em)
    val cardTitle =
        TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val hint = TextStyle(fontSize = 17.sp, lineHeight = 23.sp)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)
    val section =
        TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val label = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
    val button =
        TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val buttonLarge =
        TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
}

private val KpakamTypography =
    Typography(
        displaySmall = KpakamType.pageTitle,
        titleLarge =
            TextStyle(
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
            ),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
        bodyMedium = KpakamType.body,
        bodySmall = KpakamType.small,
        labelLarge = KpakamType.button,
    )

private val KpakamShapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

private fun materialColors(palette: KpakamColors, dark: Boolean) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = palette.ink,
        onPrimary = palette.onInk,
        background = palette.bg,
        onBackground = palette.text,
        surface = palette.surface,
        onSurface = palette.text,
        surfaceVariant = palette.surface2,
        onSurfaceVariant = palette.muted,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surface,
        surfaceContainerHighest = palette.surface2,
        outline = palette.border,
        outlineVariant = palette.border,
        error = palette.danger,
        secondaryContainer = palette.surface2,
        onSecondaryContainer = palette.text,
    )

/** The Kpakam Mono theme: light and dark, large type for amounts. */
@Composable
fun KpakamTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalKpakamColors provides palette) {
        MaterialTheme(
            colorScheme = materialColors(palette, dark),
            typography = KpakamTypography,
            shapes = KpakamShapes,
            content = content,
        )
    }
}
