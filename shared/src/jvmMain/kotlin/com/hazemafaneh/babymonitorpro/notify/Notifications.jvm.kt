package com.hazemafaneh.babymonitorpro.notify

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

actual fun notifyAlert(cameraName: String, message: String) {
    runCatching {
        trayIcon?.displayMessage(cameraName, message, TrayIcon.MessageType.INFO)
    }
}
