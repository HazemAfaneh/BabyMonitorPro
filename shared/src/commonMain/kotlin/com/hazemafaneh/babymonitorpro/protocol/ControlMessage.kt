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
