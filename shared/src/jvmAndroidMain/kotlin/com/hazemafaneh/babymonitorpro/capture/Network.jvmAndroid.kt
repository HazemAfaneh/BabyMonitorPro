package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.isLinkLocalIpv4
import com.hazemafaneh.babymonitorpro.core.isPrivateIpv4
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Only LAN addresses, best candidate first: a WiFi 192.168.x.x beats a virtual-machine
 * bridge, and a routable address is never offered at all.
 *
 * A dead 169.254 address sorts last whatever interface carries it. Machines with a
 * Thunderbolt bridge or an attached phone routinely have one on an `enN` interface that
 * ranks as highly as real WiFi, and it used to win the pairing slot on a name tie — which
 * hands the viewer an address that can only ever refuse the connection.
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
        .sortedWith(
            compareBy<Pair<String, String>> { (_, address) -> addressRank(address) }
                .thenBy { (name, _) -> interfaceRank(name) },
        )
        .map { (_, address) -> address }
        .distinct()
        .toList()
}.getOrDefault(emptyList())

private fun addressRank(address: String): Int = if (isLinkLocalIpv4(address)) 1 else 0

private fun interfaceRank(name: String): Int = when {
    name.startsWith("wlan") || name.startsWith("en") || name.startsWith("wl") -> 0
    name.startsWith("eth") -> 1
    else -> 2
}
