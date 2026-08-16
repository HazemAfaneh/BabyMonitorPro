package com.hazemafaneh.babymonitorpro.discovery

/**
 * Browsers have no mDNS API, so the web viewer relies on the two fallbacks that work
 * everywhere: manual host:port entry, or reading the address off the camera screen.
 */
actual fun createAdvertiser(): CameraAdvertiser? = null

actual fun createBrowser(): CameraBrowser? = null
