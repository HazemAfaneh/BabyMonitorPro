package com.hazemafaneh.babymonitorpro.capture

import com.github.sarxos.webcam.Webcam
import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Desktop capture through webcam-capture.
 *
 * TODO(platform): webcam-capture 0.3.12 ships a bridj-based default driver that has no
 * aarch64 build, so on Apple Silicon [Webcam.getDefault] throws at class-init time. Every
 * entry point below therefore degrades to "no camera", and the broadcaster falls back to
 * the synthetic source rather than failing to start. Replacing the driver (JavaCV, or a
 * native AVFoundation bridge) is the fix; that is a v1.1 decision, not a v1 one.
 */
actual class CameraController actual constructor(private val config: CaptureConfig) {

    private var webcam: Webcam? = null

    actual val frames: Flow<ByteArray> = flow {
        val device = webcam ?: return@flow
        val pixels = IntArray(config.width * config.height)
        while (true) {
            val image: BufferedImage? = runCatching { device.image }.getOrNull()
            if (image != null) {
                val jpeg = encodeFrom(image, pixels)
                if (jpeg != null) emit(jpeg)
            }
            delay(config.frameIntervalMs)
        }
    }.flowOn(Dispatchers.IO)

    actual suspend fun start() {
        withContext(Dispatchers.IO) {
            webcam = runCatching {
                val device = Webcam.getDefault() ?: return@runCatching null
                device.viewSize = bestViewSize(device)
                device.open(true)
                device
            }.getOrNull()
        }
    }

    actual suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching { webcam?.close() }
            webcam = null
        }
    }

    /** Desktops have one webcam in practice; nothing to switch to. */
    actual fun switchCamera() = Unit

    /** No torch on a laptop. */
    actual fun setTorch(enabled: Boolean) = Unit

    private fun encodeFrom(image: BufferedImage, scratch: IntArray): ByteArray? {
        val width = image.width
        val height = image.height
        val pixels = if (scratch.size == width * height) scratch else IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        return encodeJpeg(pixels, width, height, config.jpegQuality)
    }

    private fun bestViewSize(device: Webcam): Dimension {
        val requested = Dimension(config.width, config.height)
        val supported = runCatching { device.viewSizes }.getOrNull() ?: return requested
        return supported.minByOrNull { size ->
            val dw = size.width - config.width
            val dh = size.height - config.height
            dw * dw + dh * dh
        } ?: requested
    }
}

actual class MicController actual constructor(private val config: AudioConfig) {

    private var line: TargetDataLine? = null

    actual val samples: Flow<ByteArray> = flow {
        val source = line ?: return@flow
        val buffer = ByteArray(config.chunkBytes)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read <= 0) break
            emit(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
        }
    }.flowOn(Dispatchers.IO)

    actual suspend fun start() {
        withContext(Dispatchers.IO) {
            line = runCatching {
                val format = audioFormat(config)
                val info = DataLine.Info(TargetDataLine::class.java, format)
                (AudioSystem.getLine(info) as TargetDataLine).apply {
                    open(format, config.chunkBytes * 4)
                    start()
                }
            }.getOrNull()
        }
    }

    actual suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching {
                line?.stop()
                line?.close()
            }
            line = null
        }
    }
}

private fun audioFormat(config: AudioConfig) = AudioFormat(
    config.sampleRate.toFloat(),
    config.bitsPerSample,
    config.channels,
    /* signed = */ true,
    /* bigEndian = */ false,
)

actual fun isCameraAvailable(): Boolean =
    runCatching { Webcam.getDefault() != null }.getOrDefault(false)

/** Desktops rarely have two, and the driver has to enumerate them for this to say yes. */
actual fun hasMultipleCameras(): Boolean =
    runCatching { Webcam.getWebcams().size > 1 }.getOrDefault(false)

actual fun isMicrophoneAvailable(): Boolean = runCatching {
    AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, audioFormat(AudioConfig())))
}.getOrDefault(false)
