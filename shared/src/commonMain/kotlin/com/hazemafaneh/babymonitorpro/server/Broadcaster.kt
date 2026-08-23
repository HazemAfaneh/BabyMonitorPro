package com.hazemafaneh.babymonitorpro.server

import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import com.hazemafaneh.babymonitorpro.core.isLinkLocalIpv4
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class BroadcastConfig(
    val deviceName: String,
    val port: Int = Bmp.DEFAULT_PORT,
    val capture: CaptureConfig = CaptureConfig(),
    val audio: AudioConfig = AudioConfig(),
    /** Set when no camera is available (or for development) — draws a test pattern. */
    val useSyntheticVideo: Boolean = false,
    val motionSensitivity: Int = 50,
    val soundSensitivity: Int = 50,
)

data class BroadcastState(
    val running: Boolean = false,
    val viewerCount: Int = 0,
    /** LAN addresses this camera can be reached on, best candidate first. */
    val addresses: List<String> = emptyList(),
    val port: Int = Bmp.DEFAULT_PORT,
    val videoActive: Boolean = false,
    val audioActive: Boolean = false,
    val syntheticVideo: Boolean = false,
    /** Which lens is live, so the camera screen can label the switch control. */
    val usingFrontCamera: Boolean = true,
    /** False when the device has only one lens; the switch control is then hidden. */
    val canSwitchCamera: Boolean = false,
    val motionSensitivity: Int = 50,
    val soundSensitivity: Int = 50,
    val lastError: String? = null,
) {
    /**
     * The address the pairing card offers, or null when there is nothing worth offering yet.
     *
     * Link-local 169.254 addresses are filtered out rather than merely ranked last.
     * [isPrivateIpv4] accepts them — they are private — but nothing on the WiFi can reach
     * one, so printing it hands the viewer an address that can only ever refuse the
     * connection, and the camera takes the blame for a self-assigned address the router
     * never issued.
     */
    val primaryAddress: String? get() = addresses.firstOrNull { !isLinkLocalIpv4(it) }
}

/**
 * The camera-role half of the app: owns the embedded HTTP server plus the capture
 * pipeline feeding it.
 */
interface Broadcaster {
    val state: StateFlow<BroadcastState>

    /** JPEG frames, also used for the on-device self-preview. */
    val frames: Flow<ByteArray>

    /** Motion/sound events raised locally, mirrored to viewers over `/control`. */
    val events: Flow<ControlMessage>

    suspend fun start(config: BroadcastConfig)
    suspend fun stop()

    /**
     * Fire-and-forget shutdown for teardown paths — leaving the camera screen cancels the
     * composition's scope, so a `launch { stop() }` there would never run.
     */
    fun requestStop()

    fun setSensitivity(motion: Int, sound: Int)
    fun switchCamera()
    fun setTorch(enabled: Boolean)
}

/** Null where the platform cannot host a server — i.e. the browser. */
expect fun createBroadcaster(): Broadcaster?
