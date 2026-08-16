package com.hazemafaneh.babymonitorpro.detect

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundDetectorTest {

    /** 100 ms of 16 kHz mono PCM at the given amplitude fraction of full scale. */
    private fun tone(amplitude: Double, samples: Int = 1600): ByteArray {
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (sin(2 * PI * i / 40.0) * amplitude * Short.MAX_VALUE).toInt()
            pcm[i * 2] = (value and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    @Test
    fun silenceNeverFires() {
        val detector = SoundDetector(sensitivity = 100)
        assertFalse(detector.submit(ByteArray(3200), 0))
        assertEquals(0f, detector.lastLevel)
    }

    @Test
    fun aLoudChunkFires() {
        val detector = SoundDetector(sensitivity = 50)
        assertTrue(detector.submit(tone(0.8), 0))
        assertTrue(detector.lastLevel > 0.4f, "level was ${detector.lastLevel}")
    }

    @Test
    fun quietRoomToneDoesNotFire() {
        val detector = SoundDetector(sensitivity = 50)
        assertFalse(detector.submit(tone(0.02), 0))
    }

    @Test
    fun theDebounceSuppressesRepeatsWithinThreeSeconds() {
        val detector = SoundDetector(sensitivity = 50)

        assertTrue(detector.submit(tone(0.8), 5_000))
        assertFalse(detector.submit(tone(0.8), 6_000))
        assertFalse(detector.submit(tone(0.8), 7_900))
        assertTrue(detector.submit(tone(0.8), 8_100))
    }

    @Test
    fun sensitivityMovesTheThreshold() {
        val quiet = tone(0.08)

        assertTrue(SoundDetector(sensitivity = 95).submit(quiet, 0))
        assertFalse(SoundDetector(sensitivity = 5).submit(quiet, 0))
    }

    @Test
    fun negativeSamplesCountAsLoudly() {
        // A chunk that is entirely negative-going must not read as silence.
        val samples = 1600
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = -20_000
            pcm[i * 2] = (value and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }

        assertTrue(SoundDetector(sensitivity = 50).submit(pcm, 0))
    }

    @Test
    fun truncatedChunksAreIgnoredRatherThanCrashing() {
        val detector = SoundDetector(sensitivity = 100)
        assertFalse(detector.submit(ByteArray(0), 0))
        assertFalse(detector.submit(ByteArray(1), 0))
    }
}
