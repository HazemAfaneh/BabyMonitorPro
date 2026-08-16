package com.hazemafaneh.babymonitorpro.core

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** "Android", "iOS", "Desktop", "Web" — shown in the privacy note and default device name. */
expect fun platformName(): String

/** Suggested device name before the user edits it, e.g. "Pixel 8" or "MacBook Pro". */
expect fun defaultDeviceName(): String

/** False on web: a browser cannot host the server, so the role picker hides Camera. */
expect val supportsCameraRole: Boolean

/** True where a native notification can be raised for motion/sound alerts. */
expect val supportsNotifications: Boolean

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * True for RFC1918 / link-local addresses. The pairing UI only ever offers these —
 * BabyMonitor Pro is a LAN product and must never hand out a routable address.
 */
fun isPrivateIpv4(address: String): Boolean {
    val parts = address.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return when {
        octets[0] == 10 -> true
        octets[0] == 192 && octets[1] == 168 -> true
        octets[0] == 172 && octets[1] in 16..31 -> true
        octets[0] == 169 && octets[1] == 254 -> true
        else -> false
    }
}
