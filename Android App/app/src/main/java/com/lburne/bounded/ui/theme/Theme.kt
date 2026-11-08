package com.lburne.bounded.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Material's roles mapped onto the Gilded Rift palette, so stock components (dialogs,
 * menus, text fields, sheets) match the custom ones. Always dark: the cards are.
 */
private val GildedRiftScheme = darkColorScheme(
    primary = Rift.Gold,
    onPrimary = Rift.OnGold,
    primaryContainer = Rift.GoldWash,
    onPrimaryContainer = Rift.Ivory,
    secondary = Rift.Gold,
    onSecondary = Rift.OnGold,
    secondaryContainer = Rift.GoldWash,
    onSecondaryContainer = Rift.Ivory,
    tertiary = Rift.Parchment,
    onTertiary = Rift.Ink,
    background = Rift.Ink,
    onBackground = Rift.Ivory,
    surface = Rift.Ink,
    onSurface = Rift.Ivory,
    surfaceVariant = Rift.Raised,
    onSurfaceVariant = Rift.Muted,
    surfaceTint = Color.Transparent,
    surfaceDim = Rift.Ink,
    surfaceBright = Rift.Raised,
    surfaceContainerLowest = Rift.Ink,
    surfaceContainerLow = Color(0xFF120E0B),
    surfaceContainer = Rift.Surface,
    surfaceContainerHigh = Rift.Raised,
    surfaceContainerHighest = Color(0xFF2A221A),
    inverseSurface = Rift.Ivory,
    inverseOnSurface = Rift.Ink,
    inversePrimary = Rift.GoldDeep,
    outline = Rift.Line,
    outlineVariant = Rift.Track,
    error = Color(0xFFFF8A7A),
    onError = Rift.Ink,
    errorContainer = Color(0xFF5C1A12),
    onErrorContainer = Rift.Ivory,
    scrim = Color.Black
)

private val GildedRiftShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun RiftboundTheme(content: @Composable () -> Unit) {
    // Edge to edge: the status bar is transparent; its icons are light on the dark ground.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = GildedRiftScheme,
        typography = Typography,
        shapes = GildedRiftShapes,
        content = content
    )
}
