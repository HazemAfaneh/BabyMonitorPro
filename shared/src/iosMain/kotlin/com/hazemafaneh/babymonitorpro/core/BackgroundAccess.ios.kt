package com.hazemafaneh.babymonitorpro.core

/** iOS suspends a backgrounded app whatever it asks for; the Live Activity is the surface that survives. */
actual object BackgroundAccess {
    actual val isRelevant: Boolean = false
    actual fun isUnrestricted(): Boolean = true
    actual fun canRequest(): Boolean = false
    actual fun request() = Unit
    actual fun openSystemSettings() = Unit
}
