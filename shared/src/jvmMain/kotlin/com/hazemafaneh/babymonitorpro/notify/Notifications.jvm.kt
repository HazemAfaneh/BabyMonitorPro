package com.hazemafaneh.babymonitorpro.notify

import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

private val trayIcon: TrayIcon? by lazy {
    runCatching {
        if (!SystemTray.isSupported()) return@runCatching null
        val image = Toolkit.getDefaultToolkit().createImage(ByteArray(0))
        TrayIcon(image, "BabyMonitor Pro").apply {
            isImageAutoSize = true
            SystemTray.getSystemTray().add(this)
        }
    }.getOrNull()
}

// A tray balloon cannot carry a deep link back into the window, so the endpoint is used
// only for its name here; the desktop viewer is already on screen when this fires.
actual fun notifyAlert(endpoint: CameraEndpoint, message: String) {
    runCatching {
        trayIcon?.displayMessage(endpoint.name, message, TrayIcon.MessageType.INFO)
    }
}
