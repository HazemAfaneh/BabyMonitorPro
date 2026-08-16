package com.hazemafaneh.babymonitorpro.capture

/**
 * Encodes packed ARGB pixels to JPEG using the platform encoder — Bitmap on Android,
 * ImageIO on the JVM, CoreGraphics on iOS. Broadcaster-side only.
 */
expect fun encodeJpeg(argb: IntArray, width: Int, height: Int, quality: Int): ByteArray?

/** LAN addresses this device can be reached on. Never returns routable addresses. */
expect fun localIpv4Addresses(): List<String>
