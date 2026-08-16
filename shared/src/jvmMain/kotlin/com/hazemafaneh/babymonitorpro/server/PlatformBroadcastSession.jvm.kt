package com.hazemafaneh.babymonitorpro.server

/** A desktop process keeps running on its own. */
actual object PlatformBroadcastSession {
    actual fun begin(deviceName: String) = Unit
    actual fun end() = Unit
}
