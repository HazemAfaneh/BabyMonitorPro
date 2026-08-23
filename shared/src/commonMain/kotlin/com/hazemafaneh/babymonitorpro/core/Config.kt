package com.hazemafaneh.babymonitorpro.core

/**
 * Video capture settings. Defaults follow the v1 transport: MJPEG at 12 fps, 720p,
 * quality 70 — cheap enough to encode on a phone, small enough for WiFi.
 */
data class CaptureConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 12,
    val jpegQuality: Int = 70,
    val useFrontCamera: Boolean = true,
) {
    val frameIntervalMs: Long get() = 1000L / fps
}

/** Audio capture settings: PCM 16-bit signed little-endian, mono, 16 kHz, ~100 ms chunks. */
data class AudioConfig(
    val sampleRate: Int = 16_000,
    val channels: Int = 1,
    val bitsPerSample: Int = 16,
    val chunkMillis: Int = 100,
) {
    val bytesPerSample: Int get() = bitsPerSample / 8
    /** 16 kHz * 0.1 s * 2 bytes = 3200 bytes per chunk. */
    val chunkBytes: Int get() = sampleRate * chunkMillis / 1000 * bytesPerSample * channels
    val samplesPerChunk: Int get() = sampleRate * chunkMillis / 1000
}

enum class Role { CAMERA, VIEWER }

/** A camera the viewer can connect to, however it was found. */
data class CameraEndpoint(
    val name: String,
    val host: String,
    val port: Int = Bmp.DEFAULT_PORT,
    val source: Source = Source.MANUAL,
) {
    enum class Source { MDNS, MANUAL, QR }

    val baseUrl: String get() = "http://$host:$port"
    val id: String get() = "$host:$port"
}
