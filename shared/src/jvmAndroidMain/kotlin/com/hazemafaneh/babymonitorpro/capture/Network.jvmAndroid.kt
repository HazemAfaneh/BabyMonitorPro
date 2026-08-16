package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.isPrivateIpv4
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Only LAN addresses, best candidate first: a WiFi 192.168.x.x beats a virtual-machine
 * bridge, and a routable address is never offered at all.
 */
actual fun localIpv4Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { networkInterface ->
            networkInterface.inetAddresses.asSequence()
                .filterIsInstance<Inet4Address>()
                .map { networkInterface.name to it.hostAddress.orEmpty() }
        }
        .filter { (_, address) -> isPrivateIpv4(address) }
        .sortedBy { (name, _) -> interfaceRank(name) }
        .map { (_, address) -> address }
        .distinct()
        .toList()
}.getOrDefault(emptyList())

private fun interfaceRank(name: String): Int = when {
    name.startsWith("wlan") || name.startsWith("en") || name.startsWith("wl") -> 0
    name.startsWith("eth") -> 1
    else -> 2
}
