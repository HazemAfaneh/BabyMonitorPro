package com.hazemafaneh.babymonitorpro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Deep charcoal-indigo surfaces with a single warm amber accent. Nothing on the dark
// theme is pure white — a nursery at 3am is the design brief.
private val Amber = Color(0xFFFFC66B)
private val AmberDeep = Color(0xFF2A1F0A)
private val Indigo = Color(0xFF9AA6E8)
private val IndigoDeep = Color(0xFF1B1E33)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberDeep,
    primaryContainer = Color(0xFF4A3A18),
    onPrimaryContainer = Color(0xFFFFE2B0),
    secondary = Indigo,
    onSecondary = Color(0xFF141726),
    secondaryContainer = IndigoDeep,
    onSecondaryContainer = Color(0xFFC7CEF5),
    background = Color(0xFF0F1017),
    onBackground = Color(0xFFE2E4EE),
    surface = Color(0xFF171925),
    onSurface = Color(0xFFE2E4EE),
    surfaceVariant = Color(0xFF232636),
    onSurfaceVariant = Color(0xFFB6BACB),
    outline = Color(0xFF3B3F52),
    outlineVariant = Color(0xFF2A2E3E),
    error = Color(0xFFE79187),
    onError = Color(0xFF3A100C),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE2B0),
    onPrimaryContainer = Color(0xFF2A1F0A),
    secondary = Color(0xFF44508C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF141726),
    background = Color(0xFFFAF9FC),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE7E7EF),
    onSurfaceVariant = Color(0xFF474A57),
    outline = Color(0xFF787B8A),
)

private val BmpShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Set when the user turns on night dimming, or when the platform reports a
 * reduced-motion preference — screens read both instead of animating unconditionally.
 */
val LocalReducedMotion = compositionLocalOf { false }
val LocalNightDim = compositionLocalOf { false }

@Composable
fun BabyMonitorTheme(
    darkTheme: Boolean = true,
    reducedMotion: Boolean = false,
    nightDim: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalNightDim provides nightDim,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = BmpShapes,
            content = content,
        )
    }
}
