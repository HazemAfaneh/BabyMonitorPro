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

/**
 * True on a television — Android TV, Google TV, a set-top box.
 *
 * A TV is not merely a large tablet, which is why this is its own flag rather than a width
 * threshold. It is driven by a remote with no pointer, so focus has to be visible at all
 * times; it is watched from across a room; its panel is overscanned, so content at the edge
 * of the window can be physically off the screen; and nobody dismisses a notification on it,
 * so anything posted there has to take itself away.
 */
expect val isTelevision: Boolean

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

/**
 * A self-assigned 169.254 address, handed out when an interface never got a DHCP lease.
 * It is private, so [isPrivateIpv4] accepts it, but nothing on the WiFi can reach it —
 * offering one as a pairing address gets the viewer a refused connection.
 */
fun isLinkLocalIpv4(address: String): Boolean {
    val parts = address.split('.')
    return parts.size == 4 && parts[0] == "169" && parts[1] == "254"
}
