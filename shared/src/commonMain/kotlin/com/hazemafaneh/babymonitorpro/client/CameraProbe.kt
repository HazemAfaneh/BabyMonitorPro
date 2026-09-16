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
import kotlinx.coroutines.sync.Semaphore
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
    ): List<CameraEndpoint> = coroutineScope {
        val gate = Semaphore(MAX_PARALLEL)
        val distinctPorts = ports.distinct()
        hosts
            .flatMap { host -> distinctPorts.map { port -> host to port } }
            .map { (host, port) ->
                async {
                    gate.withPermit { ask(host, port, timeoutMillis) }
                }
            }
            .awaitAll()
            .filterNotNull()
            // One row per host: a camera answering on two ports is still one camera, and the
            // first port asked is the one this device prefers.
            .distinctBy { it.host }
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

        /** Enough to finish 500 addresses in a few seconds without opening 500 sockets. */
        const val MAX_PARALLEL = 24
    }
}
