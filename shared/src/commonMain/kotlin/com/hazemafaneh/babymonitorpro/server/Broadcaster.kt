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
    /** Whether the torch is lit, so a viewer's remote switch can show the truth. */
    val torchOn: Boolean = false,
    val motionSensitivity: Int = 50,
    val soundSensitivity: Int = 50,
    val lastError: String? = null,
    /**
     * The port could not be opened — almost always another copy of this app, or something
     * else already sitting on 8080.
     *
     * Carried as its own flag rather than left inside [lastError] because it is the one
     * fault the parent can fix from this screen: the banner it raises has an action, and a
     * banner with an action cannot be driven off a prose string.
     */
    val portUnavailable: Boolean = false,
) {
    /**
     * The port to offer instead when [portUnavailable]. One above whatever was tried, so a
     * second retry moves on again rather than re-offering a port already known to be taken.
     */
    val alternatePort: Int get() = port + 1

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

    /**
     * Normalised RMS of the most recent audio chunk, 0..1 — what the room sounds like right
     * now, whether or not it was loud enough to raise an alert.
     *
     * Separate from [state] rather than a field on it: this changes with every audio chunk,
     * and folding it into the broadcast state would recompose the whole camera screen at
     * audio rate to move fourteen bars. It is the same value [events] already reports on a
     * sound alert, published continuously instead of only when it crosses the threshold —
     * no new capture path, and nothing extra computed.
     */
    val soundLevel: StateFlow<Float>

    /**
     * How much of the picture changed between the last two frames, 0..1 — what the room
     * *looks* like right now, whether or not it was enough to raise an alert.
     *
     * The mirror of [soundLevel], and for the same reason: a movement threshold set against a
     * number with no units is set by guesswork. Published on every frame, which is a dozen
     * times a second, so it is a StateFlow rather than part of the broadcast state.
     */
    val motionLevel: StateFlow<Float>

    suspend fun start(config: BroadcastConfig)
    suspend fun stop()

    /**
     * Restart on a different port after [BroadcastState.portUnavailable].
     *
     * Here rather than in the camera screen because the broadcaster already holds the
     * config that failed: the screen only knows which port to try next, not the eight other
     * fields that have to come back unchanged with it.
     */
    fun retryOnPort(port: Int)

    /**
     * Fire-and-forget shutdown for teardown paths — leaving the camera screen cancels the
     * composition's scope, so a `launch { stop() }` there would never run.
     */
    fun requestStop()

    /**
     * Called with whatever a *viewer* just changed, so the camera device can remember it.
     *
     * Without this, remote changes lived only in the running broadcaster: the nursery
     * device's own settings screen went on showing the old values, and the next start read
     * them back from storage and undid everything the parent had set from the other room.
     * The camera screen is the one place that can write this device's settings, so the hook
     * belongs to it rather than to the server.
     */
    var onRemoteSettings: ((ControlMessage.SetCameraSettings) -> Unit)?

    fun setSensitivity(motion: Int, sound: Int)
    fun switchCamera()
    fun setTorch(enabled: Boolean)
}

/** Null where the platform cannot host a server — i.e. the browser. */
expect fun createBroadcaster(): Broadcaster?
