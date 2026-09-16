package com.hazemafaneh.babymonitorpro.client

/** A background tab is throttled by the browser and there is nothing the page can do. */
actual object PlatformViewingSession {
    actual fun begin(cameraName: String) = Unit
    actual fun end() = Unit
}
