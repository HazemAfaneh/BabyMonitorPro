package com.hazemafaneh.babymonitorpro.detect

import kotlin.math.abs

/**
 * Frame-difference motion detection over a 64x48 grayscale thumbnail.
 *
 * Each frame is compared with the previous one pixel by pixel; pixels whose brightness
 * moved more than [pixelDelta] count as changed, and motion fires when the changed
 * fraction crosses the sensitivity-derived threshold.
 *
 * Deliberately dumb: it reacts to a blanket kicked off as readily as to a baby sitting up,
 * which is the right trade for a monitor that must never miss the second case. Time is
 * passed in rather than read from a clock so the debounce is testable.
 */
class MotionDetector(
    sensitivity: Int = 50,
    private val pixelDelta: Int = DEFAULT_PIXEL_DELTA,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    var sensitivity: Int = sensitivity.coerceIn(0, 100)
        set(value) {
            field = value.coerceIn(0, 100)
        }

    private var previous: ByteArray? = null
    private var lastFiredAt: Long? = null

    /** The changed-pixel ratio of the most recent comparison, 0..1. */
    var lastRatio: Float = 0f
        private set

    /**
     * Feeds one downscaled grayscale frame and returns true when an alert should fire.
     * [frame] holds one byte per pixel, read as unsigned.
     */
    fun submit(frame: ByteArray, nowMillis: Long): Boolean {
        val last = previous
        previous = frame.copyOf()

        if (last == null || last.size != frame.size || frame.isEmpty()) {
            lastRatio = 0f
            return false
        }

        var changed = 0
        for (i in frame.indices) {
            val difference = abs((frame[i].toInt() and 0xFF) - (last[i].toInt() and 0xFF))
            if (difference > pixelDelta) changed++
        }

        val ratio = changed.toFloat() / frame.size
        lastRatio = ratio
        if (ratio < thresholdFor(sensitivity)) return false

        val previousFire = lastFiredAt
        if (previousFire != null && nowMillis - previousFire < debounceMillis) return false

        lastFiredAt = nowMillis
        return true
    }

    /** Forgets the reference frame — call when the stream restarts. */
    fun reset() {
        previous = null
        lastFiredAt = null
        lastRatio = 0f
    }

    companion object {
        const val DEFAULT_PIXEL_DELTA = 24
        const val DEFAULT_DEBOUNCE_MILLIS = 3000L

        /** Sensitivity 0 needs a fifth of the frame to change; 100 needs half a percent. */
        const val MIN_THRESHOLD = 0.005f
        const val MAX_THRESHOLD = 0.20f

        fun thresholdFor(sensitivity: Int): Float {
            val clamped = sensitivity.coerceIn(0, 100) / 100f
            return MAX_THRESHOLD - (MAX_THRESHOLD - MIN_THRESHOLD) * clamped
        }
    }
}
