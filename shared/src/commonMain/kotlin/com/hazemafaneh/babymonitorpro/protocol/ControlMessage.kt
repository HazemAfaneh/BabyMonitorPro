package com.hazemafaneh.babymonitorpro.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON messages exchanged over `WS /control`, in both directions.
 * Encoded with [BmpJson], which writes the subclass name into a `type` field.
 */
@Serializable
sealed class ControlMessage {

    /** Viewer -> camera, first message after the socket opens. */
    @Serializable
    @SerialName("Hello")
    data class Hello(
        val viewerName: String,
        val protocolVersion: Int = com.hazemafaneh.babymonitorpro.core.Bmp.PROTOCOL_VERSION,
    ) : ControlMessage()

    /** Camera -> viewer, on connect and whenever something changes. */
    @Serializable
    @SerialName("Status")
    data class Status(
        val deviceName: String,
        val protocolVersion: Int = com.hazemafaneh.babymonitorpro.core.Bmp.PROTOCOL_VERSION,
        val viewerCount: Int = 0,
        val streaming: Boolean = true,
        val audioAvailable: Boolean = false,
        val motionSensitivity: Int = 50,
        val soundSensitivity: Int = 50,
        val uptimeMillis: Long = 0,
        /**
         * The camera device's battery, 0..100, or -1 where the platform will not say.
         *
         * On the status rather than in `/info`, because it is the one fact about the nursery
         * phone that *changes while you watch* — and the question it answers is the one a
         * parent asks at midnight: is the thing filming my baby going to last the night.
         * -1 rather than null keeps every field present on the wire, as the rest of this
         * message already does.
         */
        val batteryPercent: Int = -1,
        /** True when that battery is on a charger, which is what makes a low number fine. */
        val charging: Boolean = false,
        /**
         * The camera device's temperature in Celsius, or -1 where unknown.
         *
         * A phone encoding video under a blanket gets hot, and hot is how a night ends early
         * — thermal throttling first, then a shutdown. It rides with the battery because it
         * comes from the same reading and answers the same question.
         */
        val temperatureC: Float = -1f,
        /** Which lens is live, so a viewer can offer to switch it. */
        val usingFrontCamera: Boolean = true,
        /** False when the camera device has only one lens. */
        val canSwitchCamera: Boolean = false,
        /** Torch state, so the control a viewer draws matches the room. */
        val torchOn: Boolean = false,
        /** Capture geometry and rate, so a viewer's settings screen opens on the truth. */
        val videoWidth: Int = 1280,
        val videoHeight: Int = 720,
        val frameRate: Int = 12,
        /**
         * What the camera is hearing and seeing right now — normalised RMS and changed-pixel
         * ratio, the same two numbers its own detectors compare against their thresholds.
         * -1 where they are not being measured.
         *
         * Sent so that a *viewer* setting the camera's thresholds can see where the room
         * actually sits, which is the only way that slider is anything but guesswork. The
         * viewer used to derive the sound figure from the audio it was playing, which meant
         * the marks appeared only when the sound happened to be on, and there was no movement
         * figure at all. Measuring at the camera fixes both: it is the device doing the
         * detecting, so its reading is the one the threshold is actually compared against.
         */
        val soundLevel: Float = -1f,
        val motionLevel: Float = -1f,
    ) : ControlMessage()

    /** Camera -> viewer. [intensity] is the changed-pixel ratio, 0..1. */
    @Serializable
    @SerialName("MotionEvent")
    data class MotionEvent(
        val atMillis: Long,
        val intensity: Float,
    ) : ControlMessage()

    /** Camera -> viewer. [level] is normalised RMS, 0..1. */
    @Serializable
    @SerialName("SoundEvent")
    data class SoundEvent(
        val atMillis: Long,
        val level: Float,
    ) : ControlMessage()

    /** Viewer -> camera. Both values are 0..100 slider positions. */
    @Serializable
    @SerialName("SetSensitivity")
    data class SetSensitivity(
        val motion: Int,
        val sound: Int,
    ) : ControlMessage()

    /**
     * Viewer -> camera: change the nursery device's settings from where the parent is
     * standing.
     *
     * Every field is nullable and every null means "leave this alone", so a viewer that only
     * wants the torch on does not have to know, or restate, the other six. That also makes
     * the message safe across versions: an older camera ignores the fields it cannot parse
     * and applies the rest.
     *
     * The camera answers with a fresh [Status] to **every** viewer, not just the one that
     * asked — two parents watching the same cot should not disagree about which way the lens
     * is pointing.
     *
     * [videoWidth], [videoHeight] and [frameRate] cannot be changed without rebinding the
     * capture pipeline, which drops the picture for a second or two. The camera does it
     * anyway rather than refusing: a parent who just asked for a smaller picture over mobile
     * data has accepted that trade by asking.
     */
    @Serializable
    @SerialName("SetCameraSettings")
    data class SetCameraSettings(
        val motionSensitivity: Int? = null,
        val soundSensitivity: Int? = null,
        val useFrontCamera: Boolean? = null,
        val torchOn: Boolean? = null,
        val deviceName: String? = null,
        val videoWidth: Int? = null,
        val videoHeight: Int? = null,
        val frameRate: Int? = null,
    ) : ControlMessage() {
        /** True when applying this needs the camera rebound — i.e. the picture will blink. */
        val restartsCapture: Boolean
            get() = videoWidth != null || videoHeight != null || frameRate != null
    }

    /** Either direction. [nonce] is echoed back in [Pong] to measure round-trip latency. */
    @Serializable
    @SerialName("Ping")
    data class Ping(val nonce: Long) : ControlMessage()

    @Serializable
    @SerialName("Pong")
    data class Pong(val nonce: Long) : ControlMessage()
}

/** Response body of `GET /info`. */
@Serializable
data class DeviceInfoResponse(
    val deviceName: String,
    val role: String = "camera",
    val protocolVersion: Int = com.hazemafaneh.babymonitorpro.core.Bmp.PROTOCOL_VERSION,
    val streaming: Boolean = true,
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val audioSampleRate: Int = 16_000,
    /** Battery of the device doing the filming, 0..100, or -1 where it cannot be read. */
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    /** That device's temperature in Celsius, or -1 where unknown. */
    val temperatureC: Float = -1f,
)

/**
 * Lenient on purpose: a viewer on an older build must not fall over when a newer
 * camera adds a field, and vice versa.
 */
val BmpJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

fun ControlMessage.encode(): String = BmpJson.encodeToString(ControlMessage.serializer(), this)

/** Returns null for anything that is not a control message this build understands. */
fun decodeControlMessage(text: String): ControlMessage? = try {
    BmpJson.decodeFromString(ControlMessage.serializer(), text)
} catch (_: Exception) {
    null
}
