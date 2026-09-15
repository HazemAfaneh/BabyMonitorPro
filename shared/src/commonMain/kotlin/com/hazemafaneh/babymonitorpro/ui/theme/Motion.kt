package com.hazemafaneh.babymonitorpro.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Every duration in the app. Four places move; nothing else does.
 *
 * The list is short on purpose. This is an app that spends most of its life face-up on a
 * bedside table in a dark room, and motion in the dark is noise — a parent who catches
 * movement out of the corner of their eye looks at the phone, and the whole point of the
 * app is that they only have to look when something actually happened.
 *
 * So: a press confirms it landed, a pairing succeeds once, the sound meter reports the
 * room, and an alert arrives. Nothing animates because it looked nice in isolation.
 */
@Immutable
data class BmpMotion(
    /** Press-in on any pressable surface. */
    val pressInMillis: Int,
    /** Release. Slower than the press — the finger is already gone, the eye is not. */
    val pressOutMillis: Int,
    /** The pairing celebration, start to finish. Fires once, never on a retry. */
    val celebrateMillis: Int,
    /**
     * How long one sound-meter bar takes to reach its new height.
     *
     * Never zero. This is the one animation that *is* the information: at 0ms fourteen bars
     * driven by an RMS buffer flicker like static, and a parent cannot tell a quiet room
     * from a noisy one by looking at it. Interpolated, a quiet room breathes.
     */
    val meterMillis: Int,
    /** The alert banner's single slide-in. One, then still — no pulse and no repeat. */
    val bannerMillis: Int,
) {
    /** Cards. */
    val pressScale: Float get() = 0.97f

    /** Wide rows, where the same scale would visibly shift the text at the far edge. */
    val pressScaleWide: Float get() = 0.98f
}

/** Day. */
internal val DayMotion = BmpMotion(
    pressInMillis = 90,
    pressOutMillis = 140,
    celebrateMillis = 560,
    meterMillis = 90,
    bannerMillis = 280,
)

/**
 * Night keeps only the motion that carries information.
 *
 * Press feedback and the pairing celebration go to zero: both are delight, and delight in a
 * dark bedroom at 3am is light and movement the parent did not ask for. The meter keeps
 * breathing because it is the reason they are looking, and the banner keeps its one slide
 * because an alert that appears without moving is an alert that gets missed.
 */
internal val NightMotion = DayMotion.copy(
    pressInMillis = 0,
    pressOutMillis = 0,
    celebrateMillis = 0,
)

/**
 * A reduced-motion preference stops everything except the meter — which is not decoration
 * but a reading, and a bar chart that jumps between frames is harder to read, not calmer.
 */
internal val ReducedMotion = BmpMotion(
    pressInMillis = 0,
    pressOutMillis = 0,
    celebrateMillis = 0,
    meterMillis = DayMotion.meterMillis,
    bannerMillis = 0,
)

val LocalBmpMotion = staticCompositionLocalOf { DayMotion }
