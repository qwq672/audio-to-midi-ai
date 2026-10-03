package com.audiomidi.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme palette — deep purple / indigo
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF1B0046),
    primaryContainer = Color(0xFF35009E),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFC6B7FF),
    onSecondary = Color(0xFF2F1543),
    secondaryContainer = Color(0xFF462B5B),
    onSecondaryContainer = Color(0xFFE9DBFF),
    background = Color(0xFF141118),
    onBackground = Color(0xFFE8E0EA),
    surface = Color(0xFF1F1824),
    onSurface = Color(0xFFE8E0EA),
    surfaceVariant = Color(0xFF4C4354),
    onSurfaceVariant = Color(0xFFCFC3D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

// Light theme palette
private val LightColors = lightColorScheme(
    primary = Color(0xFF6A40E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF1F0055),
    secondary = Color(0xFF625B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DBFF),
    onSecondaryContainer = Color(0xFF2A1A4F),
    background = Color(0xFFFFF7FA),
    onBackground = Color(0xFF1F1824),
    surface = Color(0xFFFFF7FA),
    onSurface = Color(0xFF1F1824),
    surfaceVariant = Color(0xFFE6DFF0),
    onSurfaceVariant = Color(0xFF4C4354),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun AudioToMidiTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
