package com.hazemafaneh.babymonitorpro.server

/** A browser cannot bind a listening socket; the web build is viewer-only. */
actual fun createBroadcaster(): Broadcaster? = null
