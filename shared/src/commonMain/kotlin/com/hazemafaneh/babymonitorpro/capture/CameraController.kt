package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import kotlinx.coroutines.flow.Flow

/**
 * Platform camera, normalised to a flow of JPEG frames.
 *
 * JPEG rather than a native surface because that is what `/stream` sends and what every
 * viewer target can decode. UPGRADE PATH: a WebRTC track would replace this flow with an
 * encoder-backed pipeline; nothing above this interface would change shape.
 */
expect class CameraController(config: CaptureConfig) {
    val frames: Flow<ByteArray>
    suspend fun start()
    suspend fun stop()
    fun switchCamera()
    fun setTorch(enabled: Boolean)
}

/** PCM 16-bit signed little-endian, mono, 16 kHz, ~100 ms per emission. */
expect class MicController(config: AudioConfig) {
    val samples: Flow<ByteArray>
    suspend fun start()
    suspend fun stop()
}

/** False when there is no usable camera — the camera screen then offers the test pattern. */
expect fun isCameraAvailable(): Boolean

expect fun isMicrophoneAvailable(): Boolean

/** True only when there is something to switch *to* — front and rear both present. */
expect fun hasMultipleCameras(): Boolean
