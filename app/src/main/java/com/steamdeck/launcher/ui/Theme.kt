package com.steamdeck.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** One dark scheme, the icon's purple as the accent. The app is never light. */
private val Scheme = darkColorScheme(
    primary = Color(0xFFC77DFF),
    onPrimary = Color(0xFF1B0730),
    secondary = Color(0xFF8899A6),
    background = Color(0xFF101418),
    onBackground = Color(0xFFDDDDDD),
    surface = Color(0xFF151A22),
    onSurface = Color(0xFFDDDDDD),
    surfaceVariant = Color(0xFF1E2530),
    onSurfaceVariant = Color(0xFF8899A6),
    error = Color(0xFFFF8A80),
)

@Composable
fun SteamDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
