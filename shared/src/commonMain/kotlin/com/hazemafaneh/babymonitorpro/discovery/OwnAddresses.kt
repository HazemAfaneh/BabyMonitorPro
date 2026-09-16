package com.hazemafaneh.babymonitorpro.discovery

/**
 * Every LAN address this device answers on.
 *
 * Exists so the viewer can recognise itself in its own discovery results. A device running
 * the camera role advertises over mDNS, and its own browser hears that advertisement like any
 * other — so the list offered a parent their own device as something to go and watch. Tapping
 * it opens a live view of the phone that is doing the watching, which is either a black
 * rectangle or a mirror, and in both cases it is the wrong device.
 *
 * Empty in the browser, which can neither enumerate interfaces nor discover anything to
 * filter.
 */
expect fun ownLanAddresses(): Set<String>

/**
 * Every IPv4 address on this device, including the ones [ownLanAddresses] deliberately drops.
 *
 * [ownLanAddresses] is filtered to RFC1918, because that is what the pairing card may offer.
 * A Tailscale address is **not** RFC1918 — it comes from the carrier-grade NAT range
 * 100.64.0.0/10 — so it was invisible to every part of this app, which is why a tailnet was
 * invisible too. This is the unfiltered list, used only to work out which networks are worth
 * probing.
 */
expect fun allLocalIpv4Addresses(): List<String>

