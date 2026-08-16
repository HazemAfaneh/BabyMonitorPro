package com.hazemafaneh.babymonitorpro.capture

import com.hazemafaneh.babymonitorpro.core.isPrivateIpv4
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.AF_INET
import platform.posix.SOCK_DGRAM
import platform.posix.close
import platform.posix.connect
import platform.posix.getsockname
import platform.posix.memcpy
import platform.posix.sockaddr_in
import platform.posix.socket

@OptIn(ExperimentalForeignApi::class)
actual fun encodeJpeg(argb: IntArray, width: Int, height: Int, quality: Int): ByteArray? {
    if (width <= 0 || height <= 0 || argb.size < width * height) return null

    // CoreGraphics wants RGBA bytes; the shared pipeline speaks packed ARGB ints.
    val rgba = ByteArray(width * height * 4)
    for (i in 0 until width * height) {
        val pixel = argb[i]
        val offset = i * 4
        rgba[offset] = ((pixel shr 16) and 0xFF).toByte()
        rgba[offset + 1] = ((pixel shr 8) and 0xFF).toByte()
        rgba[offset + 2] = (pixel and 0xFF).toByte()
        rgba[offset + 3] = ((pixel shr 24) and 0xFF).toByte()
    }

    return rgba.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.convert(),
            height = height.convert(),
            bitsPerComponent = 8.convert(),
            bytesPerRow = (width * 4).convert(),
            space = CGColorSpaceCreateDeviceRGB(),
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        ) ?: return@usePinned null

        val cgImage = CGBitmapContextCreateImage(context) ?: return@usePinned null
        val data = UIImageJPEGRepresentation(
            UIImage.imageWithCGImage(cgImage),
            (quality.coerceIn(1, 100) / 100.0),
        ) ?: return@usePinned null
        data.toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}

/**
 * Kotlin/Native's posix package for Apple targets does not expose `getifaddrs`, so the
 * address is discovered the other way round: open an unconnected UDP socket towards a
 * LAN address and ask the kernel which local address it would use. Nothing is ever sent —
 * `connect` on a datagram socket only fixes the route.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun localIpv4Addresses(): List<String> {
    val addresses = mutableListOf<String>()
    for (probe in ROUTE_PROBES) {
        val address = localAddressForRoute(probe) ?: continue
        if (isPrivateIpv4(address) && address !in addresses) addresses += address
    }
    return addresses
}

@OptIn(ExperimentalForeignApi::class)
private fun localAddressForRoute(target: String): String? {
    val targetOctets = target.split('.').mapNotNull { it.toIntOrNull() }
    if (targetOctets.size != 4) return null

    val descriptor = socket(AF_INET, SOCK_DGRAM, 0)
    if (descriptor < 0) return null

    try {
        return memScoped {
            val destination = alloc<sockaddr_in>()
            destination.sin_len = sizeOf<sockaddr_in>().convert()
            destination.sin_family = AF_INET.convert()
            destination.sin_port = hostToNetworkShort(DISCARD_PORT)
            destination.sin_addr.s_addr = packIpv4(targetOctets)

            if (connect(descriptor, destination.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
                return@memScoped null
            }

            val local = alloc<sockaddr_in>()
            val length = alloc<UIntVar>()
            length.value = sizeOf<sockaddr_in>().convert()
            if (getsockname(descriptor, local.ptr.reinterpret(), length.ptr) != 0) {
                return@memScoped null
            }
            unpackIpv4(local.sin_addr.s_addr)
        }
    } finally {
        close(descriptor)
    }
}

/** Little-endian Apple hardware: the first octet occupies the low byte of `s_addr`. */
private fun packIpv4(octets: List<Int>): UInt =
    (octets[0].toUInt() and 0xFFu) or
        ((octets[1].toUInt() and 0xFFu) shl 8) or
        ((octets[2].toUInt() and 0xFFu) shl 16) or
        ((octets[3].toUInt() and 0xFFu) shl 24)

private fun unpackIpv4(value: UInt): String {
    val a = value and 0xFFu
    val b = (value shr 8) and 0xFFu
    val c = (value shr 16) and 0xFFu
    val d = (value shr 24) and 0xFFu
    return "$a.$b.$c.$d"
}

private fun hostToNetworkShort(port: Int): UShort =
    (((port and 0xFF) shl 8) or ((port shr 8) and 0xFF)).toUShort()

private const val DISCARD_PORT = 9
private val ROUTE_PROBES = listOf("192.168.0.1", "10.0.0.1", "172.16.0.1")
