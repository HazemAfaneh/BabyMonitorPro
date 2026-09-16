package com.hazemafaneh.babymonitorpro.notify

import com.hazemafaneh.babymonitorpro.detect.MotionDetector
import com.hazemafaneh.babymonitorpro.detect.SoundDetector

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
                percent >= VERY_HIGH -> "A lot of movement"
                percent >= HIGH -> "Plenty of movement"
                percent >= MIDDLING -> "Some movement"
                else -> "Slight movement"
            }
            AlertKind.SOUND -> when {
                percent >= VERY_HIGH -> "Loud — crying or a shout"
                percent >= HIGH -> "Clearly audible"
                percent >= MIDDLING -> "Quiet but there"
                else -> "Faint — a murmur or a rustle"
            }
        }

    /**
     * The reading on the 0..100 scale the sliders use, where bigger is louder or busier.
     *
     * The bands used to sit on the raw magnitude, and the raw magnitude is not where the
     * events are: a sound alert fires at around 0.03–0.1 RMS, well under the 0.25 the "loud"
     * band wanted, so **every** sound alert read "Faint" — and a motion alert cannot fire
     * below its threshold, which at the default is already past the "a lot" band, so **every**
     * movement alert read "A lot of movement". Two labels doing no work at all.
     *
     * Banding the normalised figure instead means the words move with the room, and they are
     * the same numbers the parent set the threshold against.
     */
    val percent: Float
        get() = when (kind) {
            AlertKind.MOTION -> MotionDetector.levelPercent(magnitude)
            AlertKind.SOUND -> SoundDetector.levelPercent(magnitude)
        }

    /** Headline and detail as one line, for surfaces that only have one. */
    val oneLine: String get() = "$headline · $detail"

    private companion object {
        // Quarters of the scale both detectors are now expressed on.
        const val VERY_HIGH = 75f
        const val HIGH = 55f
        const val MIDDLING = 35f
    }
}
