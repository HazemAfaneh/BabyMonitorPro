package com.hazemafaneh.babymonitorpro.core

/**
 * `bmpro://<host>:<port>?pin=<pin>` — what the camera screen encodes into its QR code
 * and what the viewer's scanner (or a deep link) parses back out.
 */
object PairingUri {

    fun build(host: String, port: Int = Bmp.DEFAULT_PORT, pin: String? = null): String =
        buildString {
            append(Bmp.DEEP_LINK_SCHEME).append("://").append(host).append(':').append(port)
            if (!pin.isNullOrBlank()) append('?').append(Bmp.PIN_QUERY_PARAM).append('=').append(pin)
        }

    /** Returns null when [uri] is not a well-formed pairing link. */
    fun parse(uri: String): Parsed? {
        val trimmed = uri.trim()
        val prefix = "${Bmp.DEEP_LINK_SCHEME}://"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null

        val body = trimmed.removeRange(0, prefix.length)
        val queryIndex = body.indexOf('?')
        val authority = if (queryIndex >= 0) body.substring(0, queryIndex) else body
        val query = if (queryIndex >= 0) body.substring(queryIndex + 1) else ""

        val hostPort = parseHostPort(authority) ?: return null
        val pin = query.split('&')
            .firstOrNull { it.startsWith("${Bmp.PIN_QUERY_PARAM}=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }

        return Parsed(hostPort.first, hostPort.second, pin)
    }

    /** Accepts `host`, `host:port`, or a bare IPv4 — used by the manual-entry row. */
    fun parseHostPort(input: String): Pair<String, Int>? {
        val text = input.trim().removeSuffix("/")
        if (text.isEmpty()) return null

        val colon = text.lastIndexOf(':')
        if (colon <= 0) return text to Bmp.DEFAULT_PORT

        val port = text.substring(colon + 1).toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        val host = text.substring(0, colon)
        return if (host.isEmpty()) null else host to port
    }

    data class Parsed(val host: String, val port: Int, val pin: String?)
}
