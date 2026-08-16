package com.hazemafaneh.babymonitorpro.capture

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

actual fun encodeJpeg(argb: IntArray, width: Int, height: Int, quality: Int): ByteArray? =
    runCatching {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, width, height, argb, 0, width)

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream(width * height / 4)
        ImageIO.createImageOutputStream(output).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality.coerceIn(1, 100) / 100f
            }
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        output.toByteArray()
    }.getOrNull()
