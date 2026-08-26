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
