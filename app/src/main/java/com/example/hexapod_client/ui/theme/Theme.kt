package com.example.hexapod_client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HexapodColorScheme = darkColorScheme(
    primary          = AccentCyan,
    onPrimary        = BgColor,
    secondary        = AccentGreen,
    onSecondary      = BgColor,
    tertiary         = Amber,
    onTertiary       = BgColor,
    background       = BgColor,
    onBackground     = ValueColor,
    surface          = PanelColor,
    onSurface        = ValueColor,
    surfaceVariant   = BorderDim,
    onSurfaceVariant = LabelColor,
    error            = RedHalt,
    onError          = ValueColor,
    outline          = BorderDim,
    outlineVariant   = BorderGlow,
)

@Composable
fun HexapodClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HexapodColorScheme,
        typography  = Typography,
        content     = content
    )
}
