package com.audiomidi.ai.ui.theme

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

// ============================================================
// Color palette — warm indigo primary + teal accent
// Tonal progression based on Material 3 spec
// ============================================================

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF1B0046),
    primaryContainer = Color(0xFF35009E),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFF65DBE4),
    onSecondary = Color(0xFF00363C),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFFB0F0F8),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF5F1400),
    tertiaryContainer = Color(0xFF852B12),
    onTertiaryContainer = Color(0xFFFFDBCE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF160F1F),
    onBackground = Color(0xFFECE2F4),
    surface = Color(0xFF1F1628),
    onSurface = Color(0xFFECE2F4),
    surfaceVariant = Color(0xFF4A4351),
    onSurfaceVariant = Color(0xFFCDC3D5),
    surfaceTint = Color(0xFFB69CFF),
    outline = Color(0xFF988DA3),
    outlineVariant = Color(0xFF4A4351)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A40E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF1F0055),
    secondary = Color(0xFF006A75),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9CF0FB),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFFA04116),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCE),
    onTertiaryContainer = Color(0xFF380D00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCF7FA),
    onBackground = Color(0xFF1F1628),
    surface = Color(0xFFFCF7FA),
    onSurface = Color(0xFF1F1628),
    surfaceVariant = Color(0xFFE7E0EA),
    onSurfaceVariant = Color(0xFF4A4351),
    surfaceTint = Color(0xFF6A40E6),
    outline = Color(0xFF7B7386),
    outlineVariant = Color(0xFFCBC4D4)
)

// Custom typography — Material 3 spec but with slightly larger weights
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp),
    displayMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
)

@Composable
fun AudioToMidiTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
