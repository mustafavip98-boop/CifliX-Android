package com.ciflix.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CifliXColors = darkColorScheme(
    primary = Color(0xFF9B7BFF),
    secondary = Color(0xFF64D9E8),
    background = Color(0xFF090A10),
    surface = Color(0xFF151622),
    surfaceVariant = Color(0xFF222436)
)

@Composable
fun CifliXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CifliXColors, content = content)
}
