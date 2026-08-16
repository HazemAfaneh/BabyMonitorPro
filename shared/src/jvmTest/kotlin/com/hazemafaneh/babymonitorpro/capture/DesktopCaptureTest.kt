package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Desktop capture must never take the app down, whatever the machine has attached.
 * webcam-capture's bundled driver has no aarch64 build, so on Apple Silicon these calls
 * are expected to report "no camera" rather than throw — the broadcaster then falls back
 * to the synthetic source instead of failing to start.
 */
class DesktopCaptureTest {

    @Test
    fun cameraAvailabilityIsSafeToAsk() {
        val available = isCameraAvailable()
        println("desktop camera available: $available")
        // Either answer is fine; throwing is not.
        assertTrue(available || !available)
    }

    @Test
    fun startingWithoutACameraYieldsNoFramesAndNoCrash() = runBlocking {
        val controller = CameraController(CaptureConfig(fps = 5))
        controller.start()
        try {
            val frame = withTimeoutOrNull(3_000) { controller.frames.firstOrNull() }
            if (frame == null) {
                println("no desktop camera frames (expected on machines without a usable driver)")
            } else {
                assertTrue(frame.size > 100, "a JPEG frame should not be ${frame.size} bytes")
                assertTrue(frame[0] == 0xFF.toByte() && frame[1] == 0xD8.toByte(), "JPEG SOI")
            }
        } finally {
            controller.stop()
        }
    }

    @Test
    fun microphoneCaptureDegradesCleanly() = runBlocking {
        val controller = MicController(AudioConfig())
        controller.start()
        try {
            val chunk = withTimeoutOrNull(3_000) { controller.samples.firstOrNull() }
            if (chunk == null) {
                println("no desktop microphone input available")
            } else {
                assertTrue(chunk.isNotEmpty())
            }
        } finally {
            controller.stop()
        }
    }
}
