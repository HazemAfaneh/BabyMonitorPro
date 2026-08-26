package com.hazemafaneh.babymonitorpro.discovery

import com.hazemafaneh.babymonitorpro.capture.localIpv4Addresses

/** The same enumeration the broadcaster pairs from, so the two agree on what "this device" is. */
actual fun ownLanAddresses(): Set<String> = localIpv4Addresses().toSet()
