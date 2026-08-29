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

/**
 * Day. A warm cream ground, white cards, and colour spent only where it means something.
 *
 * The app used to open on charcoal, on the theory that a baby monitor is a night-time
 * object. It is not: it is set up in daylight, by two tired people passing one phone
 * between them, and a dark app in a bright nursery is the wrong end of every contrast
 * ratio. Night is a room the parent walks into later — [NightColors] — not the default.
 *
 * Structure here is carried by a 1.5dp `outlineVariant` border and nothing else. There are
 * no shadows in this app: depth is `background` (cream) to `surface` (white) to a tint.
 *
 * Two roles are rationed rather than used:
 *  - `primary`, marigold, means **live and healthy** and nothing else. Reach it through
 *    [BmpSemantic.statusLive], never because it happens to look right.
 *  - `secondary`, blueberry at 7.1:1 on cream, is **every interactive affordance**. If a
 *    parent can tap it, it is this colour; if it is this colour, it can be tapped.
 */
private val DayColors = lightColorScheme(
    // Reserved: live and healthy only.
    primary = Color(0xFFF2A63B),
    onPrimary = Color(0xFF3D2A05),
    primaryContainer = Color(0xFFFFE7BC),
    onPrimaryContainer = Color(0xFF4A3208),
    // Every interactive affordance. 7.1:1 on the cream ground.
    secondary = Color(0xFF3B4CC0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE2FA),
    onSecondaryContainer = Color(0xFF1B2263),
    // Privacy state only — the "on your WiFi" line, everywhere it appears.
    tertiary = Color(0xFF1F8A5F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5EEE2),
    onTertiaryContainer = Color(0xFF0C3B27),
    background = Color(0xFFFFF8EF),
    onBackground = Color(0xFF2A2118),
    surface = Color(0xFFFFFFFF),
    // 12.4:1 on the cream ground.
    onSurface = Color(0xFF2A2118),
    surfaceContainerHigh = Color(0xFFF9F4EC),
    // Rows, disabled plates, the QR plate.
    surfaceVariant = Color(0xFFF4F1EA),
    // Labels, metadata, timestamps. Never a value the parent has to read across a room.
    onSurfaceVariant = Color(0xFF8A7A66),
    // Field borders and unselected controls.
    outline = Color(0xFFDFD3C2),
    // The card border, which is where all of this scheme's structure lives.
    outlineVariant = Color(0xFFEADFCF),
    error = Color(0xFFD4384F),
    onError = Color(0xFFFFFFFF),
    // The fill stays quiet and the outline does the shouting — a full red card is a light
    // source, and an error is not worth lighting a nursery for.
    errorContainer = Color(0xFFFBE4E7),
    // 6.8:1 on that fill.
    onErrorContainer = Color(0xFF8E2436),
    scrim = Color(0xFF000000),
)

/**
 * Radii unchanged in spirit, reassigned in practice.
 *
 * `large` carries every content card and the preview frame, which is why it moved up to
 * 22dp: at 20dp a card the width of a phone screen reads as a rounded rectangle, and at
 * 22dp it reads as a card. `extraLarge` is the role cards and the live view's floating bar.
 */
private val BmpShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Set when the platform reports a reduced-motion preference — screens read this instead of
 * animating unconditionally.
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * The app has exactly two schemes: [DayColors] and [NightColors]. There is no third
 * "system dark" — a baby monitor's ground is a decision the design makes, and the one
 * thing a parent gets to say about it is whether the room is dark right now.
 *
 * The swap between them is a cut, not a fade. This used to cross-fade over 400ms on the
 * theory that a hard change to near-black is a flash the eye catches. It is the other way
 * round: the parent has just pressed the moon button *because* the room is dark, and the
 * fade means the screen they are looking at spends four hundred milliseconds passing
 * through every mid-grey between the two schemes — which is more emitted light, for longer,
 * than either end. A colour change nobody is waiting on can afford to be slow; this one is
 * the answer to a button press.
 */
@Composable
fun BabyMonitorTheme(
    night: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = if (night) NightColors else DayColors

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalBmpTints provides if (night) NightTints else DayTints,
        LocalBmpMotion provides when {
            reducedMotion -> ReducedMotion
            night -> NightMotion
            else -> DayMotion
        },
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

