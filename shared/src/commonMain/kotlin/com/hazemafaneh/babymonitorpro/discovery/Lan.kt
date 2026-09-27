package com.hazemafaneh.babymonitorpro.discovery

/**
 * Finding a camera on the local network without waiting for it to announce itself.
 *
 * mDNS is the right way to do this and it is the first thing tried, but it is not reliable
 * enough to be the only thing: plenty of routers drop multicast between wireless clients,
 * some Android builds throttle it in the background, guest networks block it outright, and a
 * camera that announced itself before this device joined the network may not announce again
 * for a while. When any of that happens the card sits on "Still looking around" over a
 * nursery phone that is up, listening, and perfectly reachable.
 *
 * So the LAN is asked as well as listened to, exactly as the tailnet is — see [Tailnet], which
 * this deliberately mirrors. The difference is which addresses are worth asking: a household
 * LAN is a /24 the device is already inside, so the sweep is bounded by construction.
 */
object Lan {

    /**
     * A private IPv4 address — one of this household's own, rather than something on the
     * internet.
     *
     * Used in two directions: these are the addresses worth sweeping here, and they are the
     * addresses the tailnet probe leaves alone because this search already covers them.
     */
    fun isLanAddress(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when (octets[0]) {
            10 -> true
            127 -> true
            169 -> octets[1] == 254
            172 -> octets[1] in 16..31
            192 -> octets[1] == 168
            else -> false
        }
    }

    /**
     * Addresses worth asking on this network, best first.
     *
     * Known hosts lead, for the same reason they do on a tailnet: a camera this household has
     * actually connected to is evidence, and the probe reports answers as they arrive, so the
     * order decides what the parent sees first rather than merely what gets asked.
     *
     * After that, the /24 around each of this device's own LAN addresses. Link-local 169.254
     * is skipped — nothing on the WiFi can reach it, and offering it produces a connection
     * that refuses with the camera taking the blame — and so is loopback.
     */
    fun candidates(
        ownAddresses: Collection<String>,
        knownHosts: Collection<String> = emptyList(),
    ): List<String> {
        val own = ownAddresses.filter { isLanAddress(it) && isSweepable(it) }
        if (own.isEmpty()) return emptyList()

        val known = knownHosts.filter { isLanAddress(it) && isSweepable(it) }

        val blocks = LinkedHashSet<String>()
        known.forEach { blocks += it.substringBeforeLast('.') }
        own.forEach { blocks += it.substringBeforeLast('.') }

        val swept = blocks.flatMap { prefix -> (1..254).map { host -> "$prefix.$host" } }

        return (known + swept)
            .filterNot { it in ownAddresses }
            .distinct()
            .take(MAX_CANDIDATES)
    }

    /** Neither loopback nor link-local: no camera a parent can watch lives at either. */
    private fun isSweepable(host: String): Boolean =
        !host.startsWith("127.") && !host.startsWith("169.254.")

    /** Two /24s' worth. A household with more subnets than that has mDNS working. */
    private const val MAX_CANDIDATES = 520
}
