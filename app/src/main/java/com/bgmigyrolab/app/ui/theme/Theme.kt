package com.bgmigyrolab.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0B1020)
private val Panel = Color(0xFF151B2E)
private val Accent = Color(0xFF3DDC97)
private val Warn = Color(0xFFFFB020)
private val Text = Color(0xFFE8EDF7)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF042016),
    secondary = Warn,
    background = Ink,
    surface = Panel,
    onBackground = Text,
    onSurface = Text,
    error = Color(0xFFFF6B6B),
)

@Composable
fun GyroLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
