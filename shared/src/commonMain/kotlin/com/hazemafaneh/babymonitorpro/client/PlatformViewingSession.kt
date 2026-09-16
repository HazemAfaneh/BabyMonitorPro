package com.hazemafaneh.babymonitorpro.client

/**
 * Whatever the platform needs in order to keep *watching* while the app is not in front.
 *
 * The mirror of `PlatformBroadcastSession`, and it should have existed from the start. The
 * camera end has had a foreground service all along; the watching end had nothing, so the
 * moment a parent opened another app Android was free to freeze its threads and cut its
 * sockets. What that looked like: the Live Update in the shade said "Reconnecting" while the
 * Dynamic Island still claimed the picture was live, and no alert arrived at all — which is
 * precisely the situation the alerts exist for. A monitor that only works while you are
 * looking at it is not a monitor.
 *
 * Nothing on desktop or web, where a backgrounded window keeps its sockets.
 */
expect object PlatformViewingSession {
    fun begin(cameraName: String)
    fun end()
}
