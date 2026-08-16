package com.hazemafaneh.babymonitorpro.detect

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

        /** Sensitivity 0 needs a shout; 100 fires on a whimper. */
        const val MIN_THRESHOLD = 0.01f
        const val MAX_THRESHOLD = 0.35f

        fun thresholdFor(sensitivity: Int): Float {
            val clamped = sensitivity.coerceIn(0, 100) / 100f
            return MAX_THRESHOLD - (MAX_THRESHOLD - MIN_THRESHOLD) * clamped
        }
    }
}
