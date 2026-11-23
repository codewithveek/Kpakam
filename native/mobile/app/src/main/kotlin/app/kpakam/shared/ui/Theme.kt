package app.kpakam.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF1B6B3A)
private val GreenLight = Color(0xFF8FD6A4)

private val LightColors =
    lightColorScheme(
        primary = Green,
        onPrimary = Color.White,
        secondary = Color(0xFF7A5900),
        error = Color(0xFFB3261E),
    )

private val DarkColors =
    darkColorScheme(
        primary = GreenLight,
        onPrimary = Color(0xFF00391A),
        secondary = Color(0xFFF2C14E),
        error = Color(0xFFF2B8B5),
    )

private val KpakamTypography =
    Typography(
        displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 18.sp),
        bodyMedium = TextStyle(fontSize = 16.sp),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    )

/** The Kpakam theme: light and dark, large type for amounts. */
@Composable
fun KpakamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = KpakamTypography,
        content = content,
    )
}
