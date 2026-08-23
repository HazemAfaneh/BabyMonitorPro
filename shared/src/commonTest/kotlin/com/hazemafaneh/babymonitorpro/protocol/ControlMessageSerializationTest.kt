package com.hazemafaneh.babymonitorpro.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlMessageSerializationTest {

    private val samples = listOf(
        ControlMessage.Hello(viewerName = "Kitchen iPad"),
        ControlMessage.Status(
            deviceName = "Nursery",
            viewerCount = 2,
            streaming = true,
            audioAvailable = true,
            motionSensitivity = 62,
            soundSensitivity = 41,
            uptimeMillis = 90_000,
        ),
        ControlMessage.MotionEvent(atMillis = 1_700_000_000_000, intensity = 0.42f),
        ControlMessage.SoundEvent(atMillis = 1_700_000_000_500, level = 0.77f),
        ControlMessage.SetSensitivity(motion = 80, sound = 20),
        ControlMessage.Ping(nonce = 9),
        ControlMessage.Pong(nonce = 9),
    )

    @Test
    fun everyMessageSurvivesARoundTrip() {
        for (message in samples) {
            assertEquals(message, decodeControlMessage(message.encode()), "round trip of $message")
        }
    }

    @Test
    fun theDiscriminatorIsTheClassName() {
        val json = ControlMessage.SetSensitivity(motion = 10, sound = 90).encode()
        assertTrue(json.contains("\"type\":\"SetSensitivity\""), json)
        assertTrue(json.contains("\"motion\":10"), json)
    }

    @Test
    fun unknownFieldsFromANewerBuildAreIgnored() {
        val fromTheFuture = """
            {"type":"Status","deviceName":"Nursery","cryDetected":true,"batteryLevel":0.5}
        """.trimIndent()

        val decoded = decodeControlMessage(fromTheFuture)
        assertEquals(ControlMessage.Status(deviceName = "Nursery"), decoded)
    }

    @Test
    fun garbageDecodesToNullRatherThanThrowing() {
        assertNull(decodeControlMessage("not json"))
        assertNull(decodeControlMessage("""{"type":"NoSuchMessage"}"""))
        assertNull(decodeControlMessage(""))
    }

    @Test
    fun deviceInfoCarriesTheProtocolVersion() {
        val info = DeviceInfoResponse(deviceName = "Nursery")
        val json = BmpJson.encodeToString(DeviceInfoResponse.serializer(), info)
        val decoded = BmpJson.decodeFromString(DeviceInfoResponse.serializer(), json)

        assertEquals(info, decoded)
        assertEquals(1, decoded.protocolVersion)
        assertTrue(decoded.streaming)
    }
}
