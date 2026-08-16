package com.hazemafaneh.babymonitorpro.server

import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the real socket: a broadcaster with the synthetic camera on one side, the
 * viewer's own client on the other. No mocks — if the multipart framing or the PIN check
 * were wrong, this would fail.
 */
class BroadcastServerTest {

    private val broadcaster = KtorBroadcaster()

    @AfterTest
    fun tearDown() = runBlocking { broadcaster.stop() }

    @Test
    fun servesInfoStreamAndFrames() = runBlocking {
        val port = 18_080
        broadcaster.start(config(port = port))

        val endpoint = CameraEndpoint(name = "Test", host = "127.0.0.1", port = port)
        val client = ViewerClient(endpoint)
        try {
            val info = withTimeout(TIMEOUT) { client.fetchInfo() }
            assertEquals("Nursery test", info.deviceName)
            assertEquals(Bmp.PROTOCOL_VERSION, info.protocolVersion)
            assertTrue(!info.pinRequired)

            val frames = withTimeout(TIMEOUT) { client.streamFrames().take(3).toList() }
            assertEquals(3, frames.size)
            for (frame in frames) {
                assertTrue(frame.size > 500, "a 640x360 JPEG should not be ${frame.size} bytes")
                // JPEG SOI marker.
                assertEquals(0xFF.toByte(), frame[0])
                assertEquals(0xD8.toByte(), frame[1])
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun theStreamAnnouncesTheMultipartBoundary() = runBlocking {
        val port = 18_081
        broadcaster.start(config(port = port))

        val raw = HttpClient()
        try {
            val response = withTimeout(TIMEOUT) { raw.get("http://127.0.0.1:$port${Bmp.PATH_INFO}") }
            assertEquals(HttpStatusCode.OK, response.status)

            // Every field must be on the wire even when it sits at its default: a viewer
            // reading /info has no other way to learn the protocol version or the geometry.
            val body = response.bodyAsText()
            for (field in listOf(
                "\"deviceName\":\"Nursery test\"",
                "\"protocolVersion\":1",
                "\"pinRequired\":false",
                "\"videoWidth\":1280",
                "\"audioSampleRate\":16000",
            )) {
                assertContains(body, field)
            }
        } finally {
            raw.close()
        }
    }

    @Test
    fun aPinLocksEveryEndpointAndBothWaysOfPassingItWork() = runBlocking {
        val port = 18_082
        broadcaster.start(config(port = port, pin = "246813"))

        val raw = HttpClient { expectSuccess = false }
        try {
            val withoutPin = withTimeout(TIMEOUT) { raw.get("http://127.0.0.1:$port${Bmp.PATH_INFO}") }
            assertEquals(HttpStatusCode.Unauthorized, withoutPin.status)

            val wrongPin = withTimeout(TIMEOUT) {
                raw.get("http://127.0.0.1:$port${Bmp.PATH_INFO}") {
                    header(Bmp.PIN_HEADER, "000000")
                }
            }
            assertEquals(HttpStatusCode.Unauthorized, wrongPin.status)

            val viaHeader = withTimeout(TIMEOUT) {
                raw.get("http://127.0.0.1:$port${Bmp.PATH_INFO}") {
                    header(Bmp.PIN_HEADER, "246813")
                }
            }
            assertEquals(HttpStatusCode.OK, viaHeader.status)

            // The browser's <img> tag cannot set headers, hence the query parameter.
            val viaQuery = withTimeout(TIMEOUT) {
                raw.get("http://127.0.0.1:$port${Bmp.PATH_INFO}?${Bmp.PIN_QUERY_PARAM}=246813")
            }
            assertEquals(HttpStatusCode.OK, viaQuery.status)
            assertContains(viaQuery.bodyAsText(), "\"pinRequired\":true")
        } finally {
            raw.close()
        }
    }

    private fun config(port: Int, pin: String? = null) = BroadcastConfig(
        deviceName = "Nursery test",
        port = port,
        pin = pin,
        useSyntheticVideo = true,
    )

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
