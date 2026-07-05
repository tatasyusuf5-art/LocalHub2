package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LocalHubColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    primaryContainer = PrimaryOrangeDark,
    secondary = TextSecondary,
    background = Black,
    surface = DarkSurface,
    surfaceVariant = CardBackground,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = RedFailed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LocalHubColorScheme,
        typography = Typography,
        content = content
    )
}
