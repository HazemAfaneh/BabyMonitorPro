package com.hazemafaneh.babymonitorpro.discovery

/** A page cannot see its own interfaces, and has no discovery results to filter anyway. */
actual fun ownLanAddresses(): Set<String> = emptySet()
