package com.hazemafaneh.babymonitorpro.discovery

/**
 * Discovery for networks that carry no multicast — which is every VPN, and Tailscale in
 * particular.
 *
 * mDNS cannot work over a tailnet, and no amount of fixing the mDNS code will change that:
 * a tailnet is a mesh of point-to-point WireGuard tunnels, and multicast has nowhere to go.
 * A camera reachable over Tailscale is therefore invisible to `_babymonitorpro._tcp` by
 * construction, however healthy the connection is. That is what "discovery doesn't detect
 * Tailscale devices" actually is: not a bug in the browser, but a network that browsing
 * cannot reach.
 *
 * So the tailnet is *probed* instead of browsed. Tailscale hands every node an address in
 * the carrier-grade NAT range, 100.64.0.0/10, and a node can see its own. The probe asks the
 * camera port of a bounded set of neighbouring addresses whether anything there answers
 * `/info` as a camera. It is a scan, and scans are rude — so it is bounded hard, it runs only
 * where a tailnet address actually exists on this device, and it only ever touches this
 * app's own port.
 */
object Tailnet {

    /** The CGNAT range Tailscale allocates from: 100.64.0.0/10. */
    fun isTailnetAddress(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    /**
     * Addresses worth asking, given this device's own tailnet addresses.
     *
     * Two blocks, and no more:
     *
     *  - **The /24 this device sits in.** In a household tailnet every node is usually
     *    allocated close to every other, so this is where the nursery phone actually is.
     *  - **100.64.0.0/24**, the block Tailscale starts allocating from, which covers the
     *    common case of a tailnet whose first few nodes were created years apart.
     *
     * Roughly 500 addresses, which is a few seconds of parallel connects to one port. A full
     * sweep of 100.64.0.0/10 is four million and is never attempted — that is a port scan,
     * not a feature.
     */
    fun candidates(
        ownAddresses: Collection<String>,
        /**
         * Tailnet addresses already known to have held a camera, from the recents list.
         *
         * These matter more than any guess. Tailscale allocates across the whole /10 more or
         * less arbitrarily, so a peer is often **not** in the same /24 as this device — which
         * is the honest limit of scanning. An address that worked before tells us which /24
         * the household's nodes actually live in, and that is worth far more than the two
         * blocks below.
         */
        knownHosts: Collection<String> = emptyList(),
    ): List<String> {
        val own = ownAddresses.filter(::isTailnetAddress)
        if (own.isEmpty()) return emptyList()

        val blocks = LinkedHashSet<String>()
        // Blocks where a camera has actually been seen come first: they are evidence rather
        // than a guess, and the probe is capped, so order decides what gets asked.
        knownHosts.filter(::isTailnetAddress).forEach { blocks += it.substringBeforeLast('.') }
        own.forEach { blocks += it.substringBeforeLast('.') }
        blocks += FIRST_BLOCK

        return blocks
            .flatMap { prefix -> (1..254).map { host -> "$prefix.$host" } }
            .filterNot { it in ownAddresses }
            .distinct()
            .take(MAX_CANDIDATES)
    }

    private const val FIRST_BLOCK = "100.64.0"

    /** A hard ceiling, so a strange interface configuration can never turn this into a sweep. */
    private const val MAX_CANDIDATES = 900
}
