package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import kotlinx.coroutines.flow.Flow

private const val WEB_IS_VIEWER_ONLY =
    "BabyMonitor Pro cannot broadcast from a browser — a web page cannot host the LAN " +
        "server. Run the camera role on Android, iOS or desktop."

actual class CameraController actual constructor(config: CaptureConfig) {
    actual val frames: Flow<ByteArray>
        get() = throw UnsupportedOperationException(WEB_IS_VIEWER_ONLY)

    actual suspend fun start(): Unit = throw UnsupportedOperationException(WEB_IS_VIEWER_ONLY)
    actual suspend fun stop() = Unit
    actual fun switchCamera() = Unit
    actual fun setTorch(enabled: Boolean) = Unit
}

actual class MicController actual constructor(config: AudioConfig) {
    actual val samples: Flow<ByteArray>
        get() = throw UnsupportedOperationException(WEB_IS_VIEWER_ONLY)

    actual suspend fun start(): Unit = throw UnsupportedOperationException(WEB_IS_VIEWER_ONLY)
    actual suspend fun stop() = Unit
}

actual fun isCameraAvailable(): Boolean = false
actual fun isMicrophoneAvailable(): Boolean = false
actual fun hasMultipleCameras(): Boolean = false
