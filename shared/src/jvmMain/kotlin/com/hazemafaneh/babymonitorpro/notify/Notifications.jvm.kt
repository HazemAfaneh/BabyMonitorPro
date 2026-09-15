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
actual fun notifyAlert(endpoint: CameraEndpoint, alert: CameraAlert) {
    runCatching {
        // A balloon has a caption and a body and nothing else, so the camera's name rides
        // with the headline rather than taking a line of its own.
        trayIcon?.displayMessage(
            "${alert.headline} · ${endpoint.name}",
            alert.detail,
            TrayIcon.MessageType.INFO,
        )
    }
}
