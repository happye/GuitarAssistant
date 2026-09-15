package com.guitarcoach.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFE8A34D)
private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241A0C),
    secondary = Color(0xFFB98A5A),
    background = Color(0xFF121110),
    surface = Color(0xFF1C1A17),
    surfaceVariant = Color(0xFF26221D),
    onBackground = Color(0xFFEDE7DE),
    onSurface = Color(0xFFEDE7DE),
    onSurfaceVariant = Color(0xFFB5AA99),
)

/** 固定暗色主题（练习多在晚间，深色也更省电）；后续可在设置里加浅色选项。 */
@Composable
fun GuitarCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
