package com.hazemafaneh.babymonitorpro.client

import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.protocol.BmpJson
import com.hazemafaneh.babymonitorpro.protocol.DeviceInfoResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks a list of addresses whether a camera is listening on them.
 *
 * This is how a camera on a tailnet is found, since multicast — and therefore mDNS — cannot
 * cross one. It is deliberately narrow: one port, one path, a short deadline, and a cap on
 * how many sockets are open at once. Anything that does not answer `/info` as this app within
 * the deadline is simply not there as far as the viewer is concerned.
 *
 * One client for the whole probe rather than one per address: several hundred Ktor clients
 * would each bring an engine and a thread pool, which is how a scan turns into an outage on
 * the device doing the scanning.
 */
class CameraProbe {

    private val client = cameraHttpClient { expectSuccess = false }

    /** See [KNOWN_HOST_TIMEOUT_MILLIS]. */
    val knownHostTimeoutMillis: Long get() = KNOWN_HOST_TIMEOUT_MILLIS

    /**
     * [ports] rather than one port, because the two ends of a pair are not always the same
     * build. The default moved off 8080, so a camera that has not been updated is listening
     * somewhere the new default never asks — and "nothing found" is indistinguishable from
     * "not on this network" to the parent holding the phone.
     */
    suspend fun probe(
        hosts: List<String>,
        ports: List<Int> = listOf(Bmp.DEFAULT_PORT, Bmp.LEGACY_PORT),
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        /**
         * Called the moment a camera answers, before the rest of the sweep finishes.
         *
         * This is the difference between a feature and a spinner. The list handed in is
         * hundreds of addresses long and almost all of them are silent, so waiting for the
         * last of them costs the better part of two minutes — while the camera the parent is
         * actually looking for is usually the *first* address asked, because it came out of
         * the recents list. Returning only at the end meant a working camera sat undiscovered
         * behind a hundred seconds of timeouts on addresses nobody lives at.
         *
         * Invoked in the caller's context, once per host.
         */
        onFound: ((CameraEndpoint) -> Unit)? = null,
    ): List<CameraEndpoint> = coroutineScope {
        val gate = Semaphore(MAX_PARALLEL)
        val distinctPorts = ports.distinct()
        // One row per host: a camera answering on two ports is still one camera, and the
        // first port asked is the one this device prefers. Tracked as the answers arrive
        // rather than filtered at the end, so the callback cannot report a host twice.
        val seen = mutableSetOf<String>()
        val lock = Mutex()
        hosts
            .flatMap { host -> distinctPorts.map { port -> host to port } }
            .map { (host, port) ->
                async {
                    val found = gate.withPermit { ask(host, port, timeoutMillis) } ?: return@async null
                    val first = lock.withLock { seen.add(found.host) }
                    if (!first) return@async null
                    onFound?.invoke(found)
                    found
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun ask(host: String, port: Int, timeoutMillis: Long): CameraEndpoint? =
        withTimeoutOrNull(timeoutMillis) {
            runCatching {
                val response = client.get("http://$host:$port${Bmp.PATH_INFO}")
                if (!response.status.isSuccess()) return@runCatching null
                val info = BmpJson.decodeFromString(
                    DeviceInfoResponse.serializer(),
                    response.bodyAsText(),
                )
                // Only a camera. A viewer answering here would be a bug, but a stranger's
                // web server on the same port would otherwise be offered as a nursery.
                if (info.role != "camera") return@runCatching null
                CameraEndpoint(
                    name = info.deviceName.ifBlank { host },
                    host = host,
                    port = port,
                    source = CameraEndpoint.Source.MANUAL,
                )
            }.getOrNull()
        }

    fun close() = client.close()

    private companion object {
        /**
         * Short, because a tailnet peer that is up answers in tens of milliseconds and one
         * that is not there never answers at all. Long enough to survive a WireGuard handshake
         * on a sleepy link.
         */
        const val DEFAULT_TIMEOUT_MILLIS = 1200L

        /**
         * For the few hosts that have answered before.
         *
         * A swept address either answers at once or is not there, so 1.2s is generous. A named
         * one is a different problem: a MagicDNS name has to be resolved through the VPN's own
         * resolver and the tunnel to that peer may have to be built before the first byte
         * moves, and that routinely takes longer than a second on a link that has been idle.
         * The short deadline was therefore hanging up on the one host most likely to be the
         * camera. There are only a handful of these, so the extra wait costs nothing.
         */
        const val KNOWN_HOST_TIMEOUT_MILLIS = 5_000L

        /**
         * Enough to get through the swept blocks without opening a socket per address.
         *
         * Raised from 24 once the results started streaming: the number no longer decides how
         * long a parent waits to see their camera — that is the first answer now, not the last
         * — but it does decide how long the card goes on saying it is still looking.
         */
        const val MAX_PARALLEL = 48
    }
}
