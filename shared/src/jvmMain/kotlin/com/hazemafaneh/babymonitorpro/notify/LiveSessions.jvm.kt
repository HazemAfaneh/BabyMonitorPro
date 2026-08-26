package com.hazemafaneh.babymonitorpro.notify

/**
 * A desktop has no lock screen to glance at and no Dynamic Island; the viewer window is
 * already the persistent surface. Alerts still reach the tray through [notifyAlert].
 */
actual val liveSessions: LiveSessions = NoLiveSessions
