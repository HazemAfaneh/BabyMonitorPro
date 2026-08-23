package com.hazemafaneh.babymonitorpro.core

/** Wire-level constants shared by broadcaster and viewer. See PROTOCOL.md. */
object Bmp {
    const val PROTOCOL_VERSION = 1

    const val DEFAULT_PORT = 8080

    /** RFC 6763 service type, 14 chars including the leading underscore. */
    const val SERVICE_TYPE = "_babymonitorpro._tcp"
    const val SERVICE_TYPE_LOCAL = "_babymonitorpro._tcp.local."

    const val DEEP_LINK_SCHEME = "bmpro"

    /**
     * `audio=1` on a deep link opens the live view with sound already on. Only set on the
     * link inside an alert notification: the app heard something, so the parent tapping it
     * wants to hear it too.
     */
    const val AUDIO_QUERY_PARAM = "audio"

    const val MJPEG_BOUNDARY = "frame"
    const val MJPEG_CONTENT_TYPE = "multipart/x-mixed-replace; boundary=$MJPEG_BOUNDARY"

    const val PATH_STREAM = "/stream"
    const val PATH_AUDIO = "/audio"
    const val PATH_CONTROL = "/control"
    const val PATH_INFO = "/info"

    /** TXT record key carrying the human-readable device name. */
    const val TXT_NAME = "name"
    const val TXT_VERSION = "v"
}
