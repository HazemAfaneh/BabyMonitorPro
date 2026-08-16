package com.hazemafaneh.babymonitorpro.server

/**
 * TODO(platform): iOS suspends a backgrounded app, so the camera role currently requires
 * the app to stay in the foreground. Continuing in the background would need a background
 * mode entitlement and an audio session — deliberately out of scope for v1.
 */
actual object PlatformBroadcastSession {
    actual fun begin(deviceName: String) = Unit
    actual fun end() = Unit
}
