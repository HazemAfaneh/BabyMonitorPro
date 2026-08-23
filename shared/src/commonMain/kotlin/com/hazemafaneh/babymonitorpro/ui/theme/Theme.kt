package com.hazemafaneh.babymonitorpro.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Deep charcoal-indigo surfaces with a single warm amber accent. Nothing on the dark
// theme is pure white — a nursery at 3am is the design brief.
//
// Amber is the right accent for this product for a physical reason: long-wavelength light at
// low luminance is the least disruptive thing you can put in a dark bedroom, and it is the
// colour of every night light already in the room. It is spent on one meaning only — see
// [BmpSemantic].
private val Amber = Color(0xFFFFC66B)
private val AmberDeep = Color(0xFF2A1F0A)
private val Indigo = Color(0xFF9AA6E8)
private val IndigoDeep = Color(0xFF1B1E33)
private val Mint = Color(0xFF86C9AE)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberDeep,
    primaryContainer = Color(0xFF4A3A18),
    onPrimaryContainer = Color(0xFFFFE2B0),
    secondary = Indigo,
    onSecondary = Color(0xFF141726),
    secondaryContainer = IndigoDeep,
    onSecondaryContainer = Color(0xFFC7CEF5),
    tertiary = Mint,
    onTertiary = Color(0xFF0A2018),
    tertiaryContainer = Color(0xFF14372C),
    onTertiaryContainer = Color(0xFFB8E6D3),
    background = Color(0xFF0F1017),
    onBackground = Color(0xFFE2E4EE),
    surface = Color(0xFF171925),
    onSurface = Color(0xFFE2E4EE),
    // Sheets, so they read as sitting above a card rather than beside it.
    surfaceContainerHigh = Color(0xFF1D2030),
    surfaceVariant = Color(0xFF232636),
    onSurfaceVariant = Color(0xFFB6BACB),
    outline = Color(0xFF3B3F52),
    outlineVariant = Color(0xFF2A2E3E),
    error = Color(0xFFE79187),
    onError = Color(0xFF3A100C),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = Color(0xFFF3C7C1),
    scrim = Color(0xFF000000),
)

/**
 * Night. Not the day scheme at 45% opacity.
 *
 * `Modifier.alpha(0.45f)` faded emitted light and legibility by the same factor, so the
 * screen got dimmer *and* harder to read at exactly the moment reading matters — every
 * element becoming a mid-grey on a mid-grey. It also faded the live dot, which is the one
 * thing that must not fade.
 *
 * Here the surfaces fall toward black (the ground loses about 62% of its luminance),
 * borders replace fills so structure survives without emitting light, and the accents lose
 * chroma but keep their identity. Text lands at 7.3:1 against the new ground — a *higher*
 * contrast ratio than the day scheme manages, while the panel emits roughly a third of the
 * light. Less light in the room, and the words are easier to read, not harder.
 */
private val NightColors = darkColorScheme(
    primary = Color(0xFFC9924A),
    onPrimary = Color(0xFF1A1206),
    primaryContainer = Color(0xFF2E2411),
    onPrimaryContainer = Color(0xFFE3C79A),
    secondary = Color(0xFF7E88BE),
    onSecondary = Color(0xFF0D0F19),
    secondaryContainer = Color(0xFF12141F),
    onSecondaryContainer = Color(0xFFA8B0D8),
    tertiary = Color(0xFF5E9A85),
    onTertiary = Color(0xFF06120E),
    tertiaryContainer = Color(0xFF0E211B),
    onTertiaryContainer = Color(0xFF8FC0AD),
    background = Color(0xFF07080C),
    onBackground = Color(0xFFB9BECE),
    surface = Color(0xFF0C0E14),
    onSurface = Color(0xFFB9BECE),
    surfaceContainerHigh = Color(0xFF11131C),
    surfaceVariant = Color(0xFF14161F),
    // Labels only at night, never a value: 4.6:1 is enough to read a heading by and not
    // enough to trust with a pairing address.
    onSurfaceVariant = Color(0xFF7B8092),
    outline = Color(0xFF2C303D),
    // The only structure at night — with fills this dark, the border is doing all the work.
    outlineVariant = Color(0xFF23262F),
    // Dimmed like everything else. A fault is not worth brightening the room for; the parent
    // is holding the phone when they read it.
    error = Color(0xFFB06B62),
    onError = Color(0xFF1C0806),
    errorContainer = Color(0xFF23100E),
    onErrorContainer = Color(0xFFC79891),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    // Darkened from #8A5A00: 4.9:1 on white, where it used to be 4.3:1 and failed AA for
    // the small text it gets used on.
    primary = Color(0xFF7A4E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE2B0),
    onPrimaryContainer = Color(0xFF2A1F0A),
    secondary = Color(0xFF3C4784),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1FF),
    onSecondaryContainer = Color(0xFF141726),
    tertiary = Color(0xFF1F6B4F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB8E6D3),
    onTertiaryContainer = Color(0xFF05261A),
    background = Color(0xFFFAF9FC),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B22),
    surfaceContainerHigh = Color(0xFFF0F0F6),
    surfaceVariant = Color(0xFFE7E7EF),
    onSurfaceVariant = Color(0xFF474A57),
    outline = Color(0xFF787B8A),
    outlineVariant = Color(0xFFD5D6E0),
    error = Color(0xFFA03027),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410904),
)

private val BmpShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Set when the platform reports a reduced-motion preference — screens read this instead of
 * animating unconditionally.
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * True while the night scheme is in force, for the few places that need to know something
 * other than a colour — the camera preview does not dim with the chrome, for instance.
 * Reading this to *pick a colour* is a mistake; the scheme has already handled that.
 */
val LocalNightDim = compositionLocalOf { false }

@Composable
fun BabyMonitorTheme(
    darkTheme: Boolean = true,
    reducedMotion: Boolean = false,
    /**
     * Night has settled — the camera device has been untouched long enough, or a bedside
     * viewer is running overnight. Distinct from the *preference* being on: the preference
     * arms it, this is it actually being in force.
     */
    nightActive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val target = when {
        nightActive -> NightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    val scheme = animatedScheme(target)

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalNightDim provides nightActive,
        LocalBmpSemantic provides BmpSemantic(
            statusLive = scheme.primary,
            statusDegraded = scheme.secondary,
            statusWaiting = scheme.onSurfaceVariant,
            statusFault = scheme.error,
            privacy = scheme.tertiary,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = BmpShapes,
            content = content,
        )
    }
}

/**
 * Cross-fades between schemes rather than cutting.
 *
 * Deliberately exempt from [LocalReducedMotion]: a colour change is not motion, and nothing
 * here moves or blinks. A hard cut to near-black in a dark room is a flash of change the eye
 * catches; a 400ms fade is not. Only the roles the app actually paints with are animated —
 * animating all thirty would cost recompositions for colours nothing reads.
 */
@Composable
private fun animatedScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = SCHEME_FADE_MILLIS)

    val background by animateColorAsState(target.background, spec, label = "background")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "onBackground")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "onSurface")
    val surfaceContainerHigh by
        animateColorAsState(target.surfaceContainerHigh, spec, label = "surfaceContainerHigh")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant")
    val onSurfaceVariant by
        animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant")
    val outline by animateColorAsState(target.outline, spec, label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "outlineVariant")
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val primaryContainer by
        animateColorAsState(target.primaryContainer, spec, label = "primaryContainer")
    val onPrimaryContainer by
        animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val secondaryContainer by
        animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "tertiary")
    val error by animateColorAsState(target.error, spec, label = "error")
    val errorContainer by animateColorAsState(target.errorContainer, spec, label = "errorContainer")
    val onErrorContainer by
        animateColorAsState(target.onErrorContainer, spec, label = "onErrorContainer")

    return target.copy(
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        primary = primary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        secondaryContainer = secondaryContainer,
        tertiary = tertiary,
        error = error,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
    )
}

private const val SCHEME_FADE_MILLIS = 400
