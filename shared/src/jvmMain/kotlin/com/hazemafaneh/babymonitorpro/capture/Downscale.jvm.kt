package com.hazemafaneh.babymonitorpro.capture

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual fun downscaleToGray(jpeg: ByteArray, width: Int, height: Int): ByteArray? = runCatching {
    val source = ImageIO.read(ByteArrayInputStream(jpeg)) ?: return null

    val scaled = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    val graphics = scaled.createGraphics()
    graphics.drawImage(source.getScaledInstance(width, height, Image.SCALE_FAST), 0, 0, null)
    graphics.dispose()

    ByteArray(width * height).also { output ->
        scaled.raster.getDataElements(0, 0, width, height, output)
    }
}.getOrNull()
