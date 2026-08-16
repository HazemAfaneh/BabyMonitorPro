package com.hazemafaneh.babymonitorpro.discovery

import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import kotlinx.coroutines.flow.StateFlow

/** Publishes this camera as `_babymonitorpro._tcp` on the local network. */
interface CameraAdvertiser {
    fun start(deviceName: String, port: Int, pinRequired: Boolean)
    fun stop()
}

/**
 * Watches for cameras on the local network.
 *
 * Discovery is a convenience, never a requirement: every platform also reaches a camera
 * through manual `host:port` entry or a scanned pairing code, which is the only route a
 * browser has.
 */
interface CameraBrowser {
    val cameras: StateFlow<List<CameraEndpoint>>
    fun start()
    fun stop()
}

/** Null where the platform cannot advertise — the browser, and anywhere mDNS is missing. */
expect fun createAdvertiser(): CameraAdvertiser?

/** Null where the platform cannot browse; the UI then shows manual entry only. */
expect fun createBrowser(): CameraBrowser?
