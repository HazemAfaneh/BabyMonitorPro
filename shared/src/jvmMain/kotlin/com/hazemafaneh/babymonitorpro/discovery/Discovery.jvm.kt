package com.hazemafaneh.babymonitorpro.discovery

import com.hazemafaneh.babymonitorpro.capture.localIpv4Addresses
import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

actual fun createAdvertiser(): CameraAdvertiser? = JmDnsAdvertiser()

actual fun createBrowser(): CameraBrowser? = JmDnsBrowser()

/** JmDNS binds to one interface, so it gets the same LAN address the pairing UI shows. */
private fun bindAddress(): InetAddress =
    localIpv4Addresses().firstOrNull()
        ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
        ?: InetAddress.getLocalHost()

private class JmDnsAdvertiser : CameraAdvertiser {

    private var jmdns: JmDNS? = null

    override fun start(deviceName: String, port: Int, pinRequired: Boolean) {
        stop()
        runCatching {
            val instance = JmDNS.create(bindAddress())
            val info = ServiceInfo.create(
                Bmp.SERVICE_TYPE_LOCAL,
                deviceName.ifBlank { "BabyMonitor Pro" },
                port,
                0,
                0,
                mapOf(
                    Bmp.TXT_NAME to deviceName,
                    Bmp.TXT_PIN to if (pinRequired) "1" else "0",
                    Bmp.TXT_VERSION to Bmp.PROTOCOL_VERSION.toString(),
                ),
            )
            instance.registerService(info)
            jmdns = instance
        }
    }

    override fun stop() {
        runCatching {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        }
        jmdns = null
    }
}

private class JmDnsBrowser : CameraBrowser {

    private val _cameras = MutableStateFlow<List<CameraEndpoint>>(emptyList())
    override val cameras: StateFlow<List<CameraEndpoint>> = _cameras.asStateFlow()

    private var jmdns: JmDNS? = null
    private var listener: ServiceListener? = null

    override fun start() {
        stop()
        runCatching {
            val instance = JmDNS.create(bindAddress())
            val serviceListener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    // Added only carries the name; requesting the info triggers resolution.
                    instance.requestServiceInfo(event.type, event.name, RESOLVE_TIMEOUT_MILLIS)
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    _cameras.update { list -> list.filterNot { it.name == event.name } }
                }

                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info ?: return
                    val host = info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                    val endpoint = CameraEndpoint(
                        name = info.getPropertyString(Bmp.TXT_NAME)?.takeIf { it.isNotBlank() }
                            ?: event.name,
                        host = host,
                        port = info.port,
                        pinRequired = info.getPropertyString(Bmp.TXT_PIN) == "1",
                        source = CameraEndpoint.Source.MDNS,
                    )
                    _cameras.update { list -> list.filterNot { it.id == endpoint.id } + endpoint }
                }
            }
            instance.addServiceListener(Bmp.SERVICE_TYPE_LOCAL, serviceListener)
            jmdns = instance
            listener = serviceListener
        }
    }

    override fun stop() {
        runCatching {
            listener?.let { jmdns?.removeServiceListener(Bmp.SERVICE_TYPE_LOCAL, it) }
            jmdns?.close()
        }
        listener = null
        jmdns = null
        _cameras.value = emptyList()
    }

    private companion object {
        const val RESOLVE_TIMEOUT_MILLIS = 3000L
    }
}
