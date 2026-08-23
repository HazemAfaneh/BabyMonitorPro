package com.hazemafaneh.babymonitorpro.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A camera to open, arriving from outside the composition — a tapped alert notification, a
 * scanned code handed over by the OS, or a `bmpro://` link.
 *
 * [autoAudio] is the whole reason this carries more than an endpoint. When an alert fires,
 * the parent taps the notification, lands on a muted view, hunts for the Sound control and
 * taps again: three actions in the one situation where the requirement is the fewest
 * possible. A notification raised *because the app heard something* should arrive with the
 * sound already on.
 */
data class LiveRequest(
    val endpoint: CameraEndpoint,
    val autoAudio: Boolean = false,
)

/**
 * The hand-off point between platform entry points and the navigation graph.
 *
 * Platform code (an Android `Intent`, an iOS notification response) has no access to the
 * composition, and the composition may not exist yet when the link arrives — a notification
 * tap can be what launches the process. So the request is parked here and `App()` collects
 * it whenever it does come up.
 */
object DeepLinks {

    private val _pending = MutableStateFlow<LiveRequest?>(null)

    /** Null once handled. Collected by `App()`. */
    val pending: StateFlow<LiveRequest?> = _pending.asStateFlow()

    fun offer(request: LiveRequest) {
        _pending.value = request
    }

    /**
     * Parses a `bmpro://host:port?audio=1` link. Returns false when it is not one,
     * so a caller can tell "not for us" from "handled".
     */
    fun offerUri(uri: String): Boolean {
        val parsed = PairingUri.parse(uri) ?: return false
        offer(
            LiveRequest(
                endpoint = CameraEndpoint(
                    name = parsed.host,
                    host = parsed.host,
                    port = parsed.port,
                    source = CameraEndpoint.Source.QR,
                ),
                autoAudio = uri.contains("${Bmp.AUDIO_QUERY_PARAM}=1"),
            ),
        )
        return true
    }

    /** Called once the navigation graph has acted on it. */
    fun consume() {
        _pending.update { null }
    }
}
