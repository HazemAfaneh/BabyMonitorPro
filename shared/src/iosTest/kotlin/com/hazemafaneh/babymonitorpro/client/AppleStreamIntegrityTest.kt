package com.hazemafaneh.babymonitorpro.client

import com.hazemafaneh.babymonitorpro.capture.encodeJpeg
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.protocol.DeviceInfoResponse
import com.hazemafaneh.babymonitorpro.server.BroadcastServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every frame the viewer receives on an Apple target must be byte-identical to one the
 * camera sent. Frames are noise, so they land in the same size class as a real 720p camera
 * frame — which is the point: this only ever failed on large ones.
 *
 * The bug it guards against: with the Darwin engine, `NSURLSession` parses
 * `multipart/x-mixed-replace` itself and announces every part through
 * `didReceiveResponse:completionHandler:`, which Ktor's delegate does not implement. Parts
 * were then delivered incomplete — the viewer decoded the head of one frame followed by the
 * tail of a later one, and drew a picture that was correct at the top, scrambled in the
 * middle and flat grey at the bottom. A structural check would not have caught it: such a
 * frame still starts on a JPEG SOI and ends on an EOI, which is why this compares bytes.
 */
class AppleStreamIntegrityTest {

    @Test
    fun everyFrameArrivesExactlyAsItWasSent() = runBlocking {
        val sent = (0 until 3).map { seed ->
            val rng = Random(seed)
            val argb = IntArray(WIDTH * HEIGHT) { rng.nextInt() or (0xFF shl 24) }
            encodeJpeg(argb, WIDTH, HEIGHT, QUALITY) ?: error("could not encode a test frame")
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val frames = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 1)
        val server = BroadcastServer(
            scope = scope,
            frames = frames.asSharedFlow(),
            audio = MutableSharedFlow<ByteArray>().asSharedFlow(),
            outbound = MutableSharedFlow<ControlMessage>().asSharedFlow(),
            statusProvider = { ControlMessage.Status(deviceName = "test") },
            infoProvider = { DeviceInfoResponse(deviceName = "test") },
            onViewerCountChanged = { },
            onControlMessage = { },
        )
        server.start(PORT)
        val camera = scope.launch {
            var i = 0
            while (true) {
                frames.emit(sent[i % sent.size])
                i++
                delay(FRAME_INTERVAL_MILLIS)
            }
        }
        // Let the camera get ahead, so the reader is behind from the first frame on.
        delay(FRAME_INTERVAL_MILLIS * 4)

        val client = ViewerClient(CameraEndpoint(name = "test", host = "127.0.0.1", port = PORT))
        var received = 0
        var altered = 0
        try {
            client.streamFrames().collect { frame ->
                received++
                if (sent.none { it.contentEquals(frame) }) altered++
                // Stands in for the decode, which is what puts the viewer behind the camera.
                delay(DECODE_MILLIS)
                if (received >= FRAMES) throw EnoughFrames()
            }
        } catch (_: EnoughFrames) {
        } finally {
            client.close()
            camera.cancel()
            server.stop()
        }

        assertEquals(FRAMES, received, "the stream stopped early")
        assertEquals(0, altered, "$altered of $received frames differed from what the camera sent")
    }

    private class EnoughFrames : Exception()

    private companion object {
        const val PORT = 18_841
        const val WIDTH = 640
        const val HEIGHT = 360
        const val QUALITY = 60
        const val FRAMES = 20
        const val FRAME_INTERVAL_MILLIS = 83L
        const val DECODE_MILLIS = 80L
    }
}
