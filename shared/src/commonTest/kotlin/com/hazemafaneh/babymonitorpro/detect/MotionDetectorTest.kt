package com.hazemafaneh.babymonitorpro.detect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionDetectorTest {

    private val width = 64
    private val height = 48
    private val pixels = width * height

    private fun frame(fill: Int = 40) = ByteArray(pixels) { fill.toByte() }

    /** Brightens [count] pixels well past the per-pixel delta. */
    private fun frameWithChange(count: Int, base: Int = 40) = frame(base).also { data ->
        for (i in 0 until count.coerceAtMost(pixels)) data[i] = (base + 90).toByte()
    }

    @Test
    fun theFirstFrameNeverFires() {
        val detector = MotionDetector(sensitivity = 100)
        assertFalse(detector.submit(frame(), nowMillis = 0))
    }

    @Test
    fun aStillSceneNeverFires() {
        val detector = MotionDetector(sensitivity = 100)
        detector.submit(frame(), 0)

        repeat(10) { step ->
            assertFalse(detector.submit(frame(), (step + 1) * 1000L), "still frame $step")
        }
        assertEquals(0f, detector.lastRatio)
    }

    @Test
    fun aMovingSceneFires() {
        val detector = MotionDetector(sensitivity = 50)
        detector.submit(frame(), 0)

        // Half the frame changes: far above any threshold.
        assertTrue(detector.submit(frameWithChange(pixels / 2), 1_000))
        assertTrue(detector.lastRatio > 0.4f, "ratio was ${detector.lastRatio}")
    }

    @Test
    fun changesBelowThePerPixelDeltaAreIgnored() {
        val detector = MotionDetector(sensitivity = 100)
        detector.submit(frame(40), 0)

        // Every pixel shifts, but only slightly — sensor noise, not a baby.
        val noisy = ByteArray(pixels) { (40 + MotionDetector.DEFAULT_PIXEL_DELTA - 1).toByte() }
        assertFalse(detector.submit(noisy, 1_000))
    }

    @Test
    fun theDebounceSuppressesRepeatsWithinThreeSeconds() {
        val detector = MotionDetector(sensitivity = 50)
        detector.submit(frame(), 0)

        assertTrue(detector.submit(frameWithChange(pixels / 2), 10_000), "first alert")
        assertFalse(detector.submit(frame(), 11_000), "still inside the debounce")
        assertFalse(detector.submit(frameWithChange(pixels / 2), 12_900), "still inside")
        assertTrue(detector.submit(frame(), 13_100), "debounce has expired")
    }

    @Test
    fun sensitivityMovesTheThreshold() {
        // 3% of the frame changes: above the sensitive threshold, below the insensitive one.
        val changed = (pixels * 0.03).toInt()

        val sensitive = MotionDetector(sensitivity = 90)
        sensitive.submit(frame(), 0)
        assertTrue(sensitive.submit(frameWithChange(changed), 1_000))

        val insensitive = MotionDetector(sensitivity = 5)
        insensitive.submit(frame(), 0)
        assertFalse(insensitive.submit(frameWithChange(changed), 1_000))
    }

    @Test
    fun thresholdsSpanTheDocumentedRange() {
        // Tolerance because the interpolation is float arithmetic, not exact endpoints.
        assertEquals(MotionDetector.MAX_THRESHOLD, MotionDetector.thresholdFor(0), 1e-5f)
        assertEquals(MotionDetector.MIN_THRESHOLD, MotionDetector.thresholdFor(100), 1e-5f)
        assertTrue(MotionDetector.thresholdFor(50) < MotionDetector.thresholdFor(10))
        // Out-of-range values clamp rather than invert the scale.
        assertEquals(MotionDetector.thresholdFor(0), MotionDetector.thresholdFor(-20))
        assertEquals(MotionDetector.thresholdFor(100), MotionDetector.thresholdFor(500))
    }

    @Test
    fun resetForgetsTheReferenceFrame() {
        val detector = MotionDetector(sensitivity = 50)
        detector.submit(frame(), 0)
        detector.reset()

        // With no reference frame the next submission is a baseline, not an alert.
        assertFalse(detector.submit(frameWithChange(pixels / 2), 1_000))
        assertTrue(detector.submit(frame(), 2_000))
    }

    @Test
    fun aResizedFrameIsTreatedAsANewBaseline() {
        val detector = MotionDetector(sensitivity = 100)
        detector.submit(frame(), 0)
        assertFalse(detector.submit(ByteArray(16) { 120 }, 1_000), "geometry changed")
    }
}
