package com.hazemafaneh.babymonitorpro.detect

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * RMS level detection over PCM chunks (16-bit signed little-endian, mono).
 *
 * No cry classification, by design — that is an explicit v1 non-goal. This fires on loud,
 * which covers crying, coughing and a door slamming alike.
 */
class SoundDetector(
    sensitivity: Int = 50,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    var sensitivity: Int = sensitivity.coerceIn(0, 100)
        set(value) {
            field = value.coerceIn(0, 100)
        }

    private var lastFiredAt: Long? = null

    /** Normalised RMS of the most recent chunk, 0..1. */
    var lastLevel: Float = 0f
        private set

    fun submit(pcm: ByteArray, nowMillis: Long): Boolean {
        if (pcm.size < 2) return false

        var sumOfSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < pcm.size) {
            // Little-endian: low byte first, high byte carries the sign.
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            sumOfSquares += sample.toDouble() * sample.toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return false

        val level = (sqrt(sumOfSquares / samples) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
        lastLevel = level
        if (level < thresholdFor(sensitivity)) return false

        val previousFire = lastFiredAt
        if (previousFire != null && nowMillis - previousFire < debounceMillis) return false

        lastFiredAt = nowMillis
        return true
    }

    fun reset() {
        lastFiredAt = null
        lastLevel = 0f
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 3000L

        /**
         * Sensitivity 0 needs a shout; 100 fires on a whimper.
         *
         * Both ends moved down after measuring what a real room actually produces. A phone
         * on a table in a quiet room reads 0.0005–0.015 RMS and a voice nearby peaks around
         * 0.04; the old floor of 0.01 was inside the noise and the old ceiling of 0.35 is
         * louder than anything a microphone at cot distance ever sees. At the default
         * sensitivity that put the threshold at 0.18 — roughly a shout into the handset — so
         * a sound alert never fired at all, which is the whole feature.
         */
        const val MIN_THRESHOLD = 0.004f
        const val MAX_THRESHOLD = 0.25f

        /**
         * Geometric, not linear.
         *
         * Loudness is multiplicative: the step from 0.004 to 0.02 is the same perceptual
         * distance as 0.05 to 0.25, and a linear sweep spends ninety of its hundred stops
         * above 0.03 — i.e. above everything a nursery produces. Interpolating in the
         * exponent puts the useful range in the middle of the slider, where a parent setting
         * it against the live meter can actually find it.
         */
        /**
         * The inverse of [thresholdFor]: the sensitivity at which a given level would fire.
         *
         * This is what turns the slider from a guess into a reading. A parent setting "alert
         * me at 50%" has no idea whether the room they are standing in is a 20 or an 80 —
         * the number is in units of nothing they can hear. Run the measurement back through
         * the curve and it becomes a position on the same scale: *this* noise, the one
         * happening right now, would raise an alert at 62% and above.
         *
         * Returns 0 for anything loud enough to fire even at the least sensitive setting, and
         * 100 for anything below the most sensitive threshold there is.
         */
        fun sensitivityFor(level: Float): Float = when {
            level >= MAX_THRESHOLD -> 0f
            level <= MIN_THRESHOLD -> 100f
            else -> (100f * ln(level / MAX_THRESHOLD) / ln(MIN_THRESHOLD / MAX_THRESHOLD))
                .coerceIn(0f, 100f)
        }

        /**
         * The room's loudness as 0..100, where louder is bigger.
         *
         * The scale everything user-facing speaks: the slider, the live mark, and the words
         * on an alert. [sensitivityFor] runs the other way — it answers "how sensitive would
         * you have to be" — and showing a parent a number that falls as the room gets louder
         * is how a threshold ends up set backwards.
         */
        fun levelPercent(level: Float): Float = 100f - sensitivityFor(level)

        fun thresholdFor(sensitivity: Int): Float {
            val clamped = sensitivity.coerceIn(0, 100) / 100f
            return MAX_THRESHOLD * (MIN_THRESHOLD / MAX_THRESHOLD).pow(clamped)
        }
    }
}
