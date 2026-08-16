package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import com.hazemafaneh.babymonitorpro.core.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sin

/**
 * Test-pattern camera: a slow indigo gradient with a drifting amber disc and a moving
 * bar, so both the stream and the motion detector have something real to chew on.
 *
 * Used when the device has no camera (or has not granted it yet) and by the desktop
 * build during development.
 */
class SyntheticVideoSource(private val config: CaptureConfig) {

    val frames: Flow<ByteArray> = flow {
        val width = SMALL_WIDTH
        val height = SMALL_HEIGHT
        val pixels = IntArray(width * height)
        val start = nowMillis()

        while (true) {
            val t = (nowMillis() - start) / 1000.0
            render(pixels, width, height, t)
            encodeJpeg(pixels, width, height, config.jpegQuality)?.let { emit(it) }
            delay(config.frameIntervalMs)
        }
    }

    private fun render(pixels: IntArray, width: Int, height: Int, t: Double) {
        val discX = (width / 2) + (sin(t * 0.7) * width * 0.28)
        val discY = (height / 2) + (sin(t * 0.43) * height * 0.22)
        val discR = height * 0.16
        val barX = ((t * 90) % width)

        for (y in 0 until height) {
            val rowBase = y * width
            val vertical = y.toDouble() / height
            for (x in 0 until width) {
                val horizontal = x.toDouble() / width

                // Background: deep charcoal-indigo gradient.
                var r = (16 + 26 * vertical).toInt()
                var g = (18 + 30 * vertical).toInt()
                var b = (34 + 70 * horizontal).toInt()

                val dx = x - discX
                val dy = y - discY
                if (dx * dx + dy * dy < discR * discR) {
                    r = 255; g = 198; b = 107
                }

                if (abs(x - barX) < 2.0) {
                    r = (r + 90).coerceAtMost(255)
                    g = (g + 90).coerceAtMost(255)
                    b = (b + 90).coerceAtMost(255)
                }

                pixels[rowBase + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private companion object {
        // Deliberately small: this is a stand-in, and every platform encodes it on the CPU.
        const val SMALL_WIDTH = 640
        const val SMALL_HEIGHT = 360
    }
}
