package com.hazemafaneh.babymonitorpro.client

/** Desktop windows keep their sockets when they lose focus. */
actual object PlatformViewingSession {
    actual fun begin(cameraName: String) = Unit
    actual fun end() = Unit
}
