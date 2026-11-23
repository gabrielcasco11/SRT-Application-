package com.gabriel.srtmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** All app colours in one place — change these to re-skin the app. */
object Palette {
    val Background = Color(0xFF070D1A)
    val Surface = Color(0xFF111A2B)
    val SurfaceHigh = Color(0xFF1A2539)
    val Field = Color(0xFF0D1524)
    val Outline = Color(0xFF2A3852)
    val Accent = Color(0xFF2D7FF9)
    val AccentCyan = Color(0xFF22D3EE)
    val Text = Color(0xFFF2F5FA)
    val Muted = Color(0xFF8D9AB0)
    val Live = Color(0xFFFF3B4E)
    val Good = Color(0xFF2BD47D)
    val Warn = Color(0xFFFFB547)
    val Bad = Color(0xFFFF5C6C)

    // chart lines
    val ChartBuffer = Color(0xFFF5D547)
    val ChartRtt = Color(0xFFFF7A59)
    val ChartLatency = Color(0xFFB388FF)
    val ChartBandwidth = Color(0xFF4FC3F7)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.AccentCyan,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Outline,
    error = Palette.Bad,
)

@Composable
fun SrtTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
