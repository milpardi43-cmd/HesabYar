package ir.hesabyar.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0E2),
    onPrimaryContainer = Color(0xFF0B3D29),
    secondary = Color(0xFF8F6218),
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF182420),
    surface = Color.White,
    onSurface = Color(0xFF182420),
    surfaceVariant = Color(0xFFE7EEE9),
    onSurfaceVariant = Color(0xFF4E5D54),
    error = Color(0xFFAD4949),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6B4),
    onPrimary = Color(0xFF0B3D29),
    primaryContainer = Color(0xFF14523A),
    onPrimaryContainer = Color(0xFFD8F0E2),
    secondary = Color(0xFFE2B86B),
    background = Color(0xFF101614),
    onBackground = Color(0xFFE6EDE8),
    surface = Color(0xFF18211D),
    onSurface = Color(0xFFE6EDE8),
    surfaceVariant = Color(0xFF243029),
    onSurfaceVariant = Color(0xFFB7C4BB),
    error = Color(0xFFE59898),
)

@Composable
fun HesabYarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScalePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val scale = fontScalePercent.coerceIn(85, 130) / 100f
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = appTypography(scale),
        content = content,
    )
}

private fun appTypography(scale: Float): Typography {
    fun style(size: Int, weight: FontWeight = FontWeight.Normal, height: Int = size + 8) = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (height * scale).sp,
    )
    return Typography(
        headlineMedium = style(26, FontWeight.Bold, 36),
        headlineSmall = style(22, FontWeight.Bold, 32),
        titleLarge = style(20, FontWeight.Bold, 30),
        titleMedium = style(16, FontWeight.Medium, 26),
        titleSmall = style(14, FontWeight.Medium, 22),
        bodyLarge = style(16, FontWeight.Normal, 28),
        bodyMedium = style(14, FontWeight.Normal, 24),
        bodySmall = style(12, FontWeight.Normal, 20),
        labelLarge = style(14, FontWeight.Medium, 20),
        labelMedium = style(12, FontWeight.Medium, 18),
        labelSmall = style(11, FontWeight.Medium, 16),
    )
}
