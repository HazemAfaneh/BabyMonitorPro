package com.hazemafaneh.babymonitorpro.client

/** iOS keeps the app alive for a while on its own; the Live Activity is the surface. */
actual object PlatformViewingSession {
    actual fun begin(cameraName: String) = Unit
    actual fun end() = Unit
}
