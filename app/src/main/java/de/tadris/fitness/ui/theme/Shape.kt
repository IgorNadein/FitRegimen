package de.tadris.fitness.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A softer Material 3 shape scale shared by all Compose screens.
 * Text fields use [extraSmall], cards use the medium/large roles, and dialogs use
 * the larger roles, so forms no longer look like square widgets pasted onto the UI.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
