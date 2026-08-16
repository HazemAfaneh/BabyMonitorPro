package com.hazemafaneh.babymonitorpro.server

/**
 * Whatever the platform needs in order to keep capturing while the app is not in front —
 * a foreground service on Android, nothing at all on desktop.
 */
expect object PlatformBroadcastSession {
    fun begin(deviceName: String)
    fun end()
}
