package com.hazemafaneh.babymonitorpro.notify

import com.hazemafaneh.babymonitorpro.core.CameraEndpoint

/**
 * The two things this app does that a phone's system surfaces can show while the app is not
 * on screen: it is broadcasting, or it is watching.
 *
 * Both are *ongoing* states rather than events, which is the distinction that matters here.
 * [notifyAlert] posts a one-shot notification for a thing that just happened and is then
 * over; these two run for as long as the job does, and are updated in place — the viewer
 * count changing, the picture dropping, an alert arriving. On iOS that is a Live Activity in
 * the Dynamic Island and on the Lock Screen; on Android 16 it is a Live Update with a
 * status-bar chip. The parent gets the answer to "is it still working" without unlocking
 * anything.
 *
 * Fire-and-forget by design: nothing the app does should wait on, or fail because of, a
 * notification surface. Every call is safe to make on a platform that has no such surface,
 * where [liveSessions] is [NoLiveSessions] and all of this costs nothing.
 */
interface LiveSessions {

    /** The parent is watching [endpoint]. Ends at [stopViewing]. */
    fun startViewing(endpoint: CameraEndpoint)

    /**
     * New state for the running viewer session.
     *
     * [alert] is the most recent motion or sound event, or null when there is nothing to
     * report. A second alert replaces the first for the same reason the in-app banner does —
     * a stack of them on a Lock Screen is a stack nobody reads.
     */
    fun updateViewing(statusLabel: String, alert: String? = null)

    fun stopViewing()

    /** This device is the camera. Ends at [stopBroadcasting]. */
    fun startBroadcasting(deviceName: String)

    /** New state for the running broadcast session: who is watching, and on what address. */
    fun updateBroadcasting(statusLabel: String, viewerCount: Int, address: String?)

    fun stopBroadcasting()
}

/**
 * Desktop and the browser. Neither has a system surface that stays on screen and updates in
 * place, so rather than each of them re-implementing six empty functions, they share this.
 */
internal object NoLiveSessions : LiveSessions {
    override fun startViewing(endpoint: CameraEndpoint) = Unit
    override fun updateViewing(statusLabel: String, alert: String?) = Unit
    override fun stopViewing() = Unit
    override fun startBroadcasting(deviceName: String) = Unit
    override fun updateBroadcasting(statusLabel: String, viewerCount: Int, address: String?) = Unit
    override fun stopBroadcasting() = Unit
}

/** The live-session surface for this platform. */
expect val liveSessions: LiveSessions
