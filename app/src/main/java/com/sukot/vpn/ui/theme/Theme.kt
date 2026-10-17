package com.sukot.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SukotDarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = TextPrimary,
    primaryContainer = VioletDark,
    onPrimaryContainer = TextPrimary,
    secondary = Blue,
    onSecondary = TextPrimary,
    secondaryContainer = BlueDark,
    onSecondaryContainer = TextPrimary,
    tertiary = Cyan,
    background = DeepBg,
    onBackground = TextPrimary,
    surface = DeepSurface,
    onSurface = TextPrimary,
    surfaceVariant = DeepSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = DeepOutline,
    error = ErrorRed
)

@Composable
fun SukotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Sukot is a luxury dark app: always dark.
    MaterialTheme(
        colorScheme = SukotDarkColors,
        content = content
    )
}
