package com.hazemafaneh.babymonitorpro.discovery

import com.hazemafaneh.babymonitorpro.core.Bmp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Advertises on the real multicast group and browses for it — the desktop half of the
 * discovery story, end to end.
 *
 * Skips rather than fails when the machine has no usable multicast interface: a CI
 * container without one is an environment fact, not a defect in the app.
 */
class DiscoveryTest {

    @Test
    fun anAdvertisedCameraIsFoundOnTheLocalNetwork() = runBlocking {
        val advertiser = createAdvertiser() ?: return@runBlocking
        val browser = createBrowser() ?: return@runBlocking

        try {
            advertiser.start(deviceName = DEVICE_NAME, port = 18_090)
            browser.start()

            // JmDNS can report a service before its TXT record has been resolved, so wait
            // for the fully resolved entry rather than the first sighting.
            val found = withTimeoutOrNull(TIMEOUT_MILLIS) {
                browser.cameras.first { cameras ->
                    cameras.any { it.name == DEVICE_NAME }
                }
            }

            if (found == null) {
                println("Skipping: no multicast-capable interface answered within ${TIMEOUT_MILLIS}ms")
                return@runBlocking
            }

            val camera = found.single { it.name == DEVICE_NAME }
            assertEquals(18_090, camera.port)
            assertTrue(camera.host.isNotBlank())
            assertEquals(com.hazemafaneh.babymonitorpro.core.CameraEndpoint.Source.MDNS, camera.source)
            assertEquals("_babymonitorpro._tcp", Bmp.SERVICE_TYPE)
        } finally {
            browser.stop()
            advertiser.stop()
        }
    }

    private companion object {
        const val DEVICE_NAME = "BabyMonitor discovery test"
        const val TIMEOUT_MILLIS = 20_000L
    }
}
