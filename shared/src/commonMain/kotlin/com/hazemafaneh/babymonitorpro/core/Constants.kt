package com.hazemafaneh.babymonitorpro.core

/** Wire-level constants shared by broadcaster and viewer. See PROTOCOL.md. */
object Bmp {
    const val PROTOCOL_VERSION = 1

    /**
     * 47821, not 8080.
     *
     * 8080 is the most contested port on any network: a dev server, a router's admin page, a
     * media server, a second copy of this app. A camera that cannot bind it prints no pairing
     * address at all, and the parent has no way to know why. This number is in the registered
     * range, assigned to nothing, and nothing else a household runs is going to want it.
     *
     * Changing it is safe here because both halves of a pair are this app: the QR code and
     * the pairing line carry the port, and a viewer typing a bare address gets this default.
     */
    const val DEFAULT_PORT = 47821

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

    /**
     * What the default used to be.
     *
     * Kept only so discovery can still find a camera that has not been updated yet — the two
     * ends of a pair are upgraded at different times, and a viewer that only asks the new
     * port reports "nothing found" for a camera that is working perfectly on the old one.
     */
    const val LEGACY_PORT = 8080

    /**
     * `GET /stream?fps=N` — the most frames per second this viewer wants.
     *
     * Absent means "everything the camera has", which is what a device on the same WiFi
     * should get. It exists for the parent watching from work over mobile data, where the
     * difference between 12 fps and 2 is the difference between a data plan surviving the
     * afternoon and not.
     */
    const val QUERY_FPS = "fps"

    /** TXT record key carrying the human-readable device name. */
    const val TXT_NAME = "name"
    const val TXT_VERSION = "v"
}
