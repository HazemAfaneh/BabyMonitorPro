package com.hazemafaneh.babymonitorpro.core

/** A background tab is throttled by the browser and the page cannot opt out. */
actual object BackgroundAccess {
    actual val isRelevant: Boolean = false
    actual fun isUnrestricted(): Boolean = true
    actual fun canRequest(): Boolean = false
    actual fun request() = Unit
    actual fun openSystemSettings() = Unit
}
