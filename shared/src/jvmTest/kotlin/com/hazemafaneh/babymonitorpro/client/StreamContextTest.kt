package com.hazemafaneh.babymonitorpro.client

import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.server.BroadcastConfig
import com.hazemafaneh.babymonitorpro.server.KtorBroadcaster
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Collects the stream on a dedicated dispatcher rather than the caller's, covering the
 * path the viewers actually use.
 *
 * It does **not** reproduce the bug that prompted it. Ktor's `execute` runs its block in
 * the engine's own context; on Darwin that is a different dispatcher from the collector's,
 * so a bare `flow {}` fails there with "Flow invariant is violated" — but CIO, the only
 * engine available on the JVM, stays on the calling context and emits happily either way.
 * Verified by reverting [ViewerClient.streamFrames] to `flow {}`: this test still passed.
 *
 * So the Darwin case is guarded by `channelFlow` being the right construct, not by this
 * test. Catching a regression would need an iOS test target running against a live server.
 */
class StreamContextTest {

    private val broadcaster = KtorBroadcaster()
    private val collectOn = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "collector").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    @AfterTest
    fun tearDown() = runBlocking {
        broadcaster.stop()
        collectOn.close()
    }

    @Test
    fun framesSurviveBeingCollectedOnAForeignDispatcher() = runBlocking {
        val port = 18_120
        broadcaster.start(
            BroadcastConfig(deviceName = "Context", port = port, useSyntheticVideo = true),
        )

        val client = ViewerClient(CameraEndpoint(name = "Context", host = "127.0.0.1", port = port))
        try {
            val frames = withContext(collectOn) {
                withTimeout(TIMEOUT) { client.streamFrames().take(3).toList() }
            }

            assertEquals(3, frames.size)
            for (frame in frames) {
                assertTrue(frame.size > 500, "unexpectedly small JPEG: ${frame.size} bytes")
                assertEquals(0xFF.toByte(), frame[0])
                assertEquals(0xD8.toByte(), frame[1])
            }
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
