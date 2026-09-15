package com.hazemafaneh.babymonitorpro.notify

/** Which detector fired. The two are never phrased alike — see [CameraAlert.headline]. */
enum class AlertKind { MOTION, SOUND }

/**
 * One motion or sound event, with the reading that raised it.
 *
 * The magnitude travels with the event rather than being formatted away at the call site,
 * because every surface phrases it differently: the banner has a line, the notification has
 * a title and a body, and the Lock Screen session has about four words. All three have to
 * agree about what happened, and the only way that holds is if all three derive their words
 * from the same numbers.
 *
 * The previous shape carried a label and a prose `meta` built at the call site, and that
 * meta claimed what the *other* detector saw — "sound was quiet" on every motion event,
 * "the picture was still" on every sound one. Neither was ever measured: the camera sends
 * motion and sound events independently and says nothing about the other channel, so those
 * sentences were invented. A parent deciding whether to get up was reading a guess, which is
 * worse than reading nothing.
 */
data class CameraAlert(
    val kind: AlertKind,
    val atMillis: Long,
    /**
     * Motion: the changed-pixel ratio, 0..1. Sound: normalised RMS, 0..1.
     *
     * The same number the camera's detector compared against its threshold, passed through
     * untouched — see `PROTOCOL.md`.
     */
    val magnitude: Float,
) {
    /** What happened, in the fewest words that still say which sensor saw it. */
    val headline: String
        get() = when (kind) {
            AlertKind.MOTION -> "Movement in the nursery"
            AlertKind.SOUND -> "Sound in the nursery"
        }

    /**
     * The reading behind the headline, in words rather than a number.
     *
     * A ratio of 0.08 tells a parent nothing at 3am. "A lot of movement" does, and it is the
     * same fact. The bands are wide on purpose — the detectors are frame-difference and RMS,
     * which cannot justify finer distinctions than these.
     */
    val detail: String
        get() = when (kind) {
            AlertKind.MOTION -> when {
                magnitude >= MOTION_STRONG -> "A lot of movement"
                magnitude >= MOTION_MODERATE -> "Steady movement"
                else -> "Slight movement"
            }
            AlertKind.SOUND -> when {
                magnitude >= SOUND_LOUD -> "Loud — crying or a shout"
                magnitude >= SOUND_MODERATE -> "Clearly audible"
                else -> "Faint — a murmur or a rustle"
            }
        }

    /** Headline and detail as one line, for surfaces that only have one. */
    val oneLine: String get() = "$headline · $detail"

    private companion object {
        // Motion fires between 0.005 and 0.20 of the frame depending on sensitivity, so the
        // bands sit inside that range rather than spanning 0..1.
        const val MOTION_STRONG = 0.12f
        const val MOTION_MODERATE = 0.04f

        // Sound fires between 0.01 and 0.35 RMS for the same reason.
        const val SOUND_LOUD = 0.25f
        const val SOUND_MODERATE = 0.08f
    }
}
