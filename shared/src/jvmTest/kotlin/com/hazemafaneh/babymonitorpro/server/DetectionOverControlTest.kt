package com.hazemafaneh.babymonitorpro.server

import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The detection path end to end: the synthetic camera's drifting disc is real movement as
 * far as the detector is concerned, so a viewer sitting on `/control` should be told about
 * it — and the sensitivity it pushes back should be honoured.
 *
 * One socket for the whole test, as a real viewer has: reconnecting per assertion would
 * hide exactly the bugs this is meant to catch.
 */
class DetectionOverControlTest {

    private val broadcaster = KtorBroadcaster()

    @AfterTest
    fun tearDown() = runBlocking { broadcaster.stop() }

    @Test
    fun aViewerIsToldAboutMotionAndCanChangeSensitivity() = runBlocking {
        val port = 18_095
        broadcaster.start(
            BroadcastConfig(
                deviceName = "Detector test",
                port = port,
                useSyntheticVideo = true,
                motionSensitivity = 95,
                soundSensitivity = 95,
            ),
        )

        val client = ViewerClient(CameraEndpoint(name = "t", host = "127.0.0.1", port = port))
        val outgoing = MutableSharedFlow<ControlMessage>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val received = MutableSharedFlow<ControlMessage>(
            replay = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        val pump = launch {
            client.control(outgoing).collect { received.emit(it) }
        }

        try {
            val status = withTimeout(TIMEOUT) {
                received.first { it is ControlMessage.Status }
            } as ControlMessage.Status
            assertEquals("Detector test", status.deviceName)
            assertEquals(95, status.motionSensitivity)

            val motion = withTimeout(TIMEOUT) {
                received.first { it is ControlMessage.MotionEvent }
            } as ControlMessage.MotionEvent
            assertTrue(motion.intensity > 0f, "intensity should carry the changed ratio")
            assertTrue(motion.atMillis > 0, "events are timestamped")

            outgoing.emit(ControlMessage.SetSensitivity(motion = 10, sound = 20))
            val updated = withTimeout(TIMEOUT) {
                received.first { it is ControlMessage.Status && it.motionSensitivity == 10 }
            } as ControlMessage.Status
            assertEquals(20, updated.soundSensitivity)
        } finally {
            pump.cancel()
            client.close()
        }
    }

    private companion object {
        const val TIMEOUT = 25_000L
    }
}
