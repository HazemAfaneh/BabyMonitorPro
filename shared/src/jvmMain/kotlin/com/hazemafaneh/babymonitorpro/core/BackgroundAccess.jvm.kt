package com.hazemafaneh.babymonitorpro.core

/** A desktop window keeps running when it loses focus. Nothing to ask for. */
actual object BackgroundAccess {
    actual val isRelevant: Boolean = false
    actual fun isUnrestricted(): Boolean = true
    actual fun canRequest(): Boolean = false
    actual fun request() = Unit
    actual fun openSystemSettings() = Unit
}
