package com.hazemafaneh.babymonitorpro.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext
import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import java.net.Inet4Address
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private fun nsdManager(): NsdManager? =
    AndroidPlatformContext.applicationContext
        ?.getSystemService(Context.NSD_SERVICE) as? NsdManager

actual fun createAdvertiser(): CameraAdvertiser? = nsdManager()?.let(::NsdAdvertiser)

actual fun createBrowser(): CameraBrowser? = nsdManager()?.let(::NsdBrowser)

private class NsdAdvertiser(private val manager: NsdManager) : CameraAdvertiser {

    private var listener: NsdManager.RegistrationListener? = null

    override fun start(deviceName: String, port: Int) {
        stop()
        val info = NsdServiceInfo().apply {
            serviceName = deviceName.ifBlank { "BabyMonitor Pro" }
            serviceType = Bmp.SERVICE_TYPE
            setPort(port)
            setAttribute(Bmp.TXT_NAME, deviceName)
            setAttribute(Bmp.TXT_VERSION, Bmp.PROTOCOL_VERSION.toString())
        }

        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = registration
        runCatching {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        }
    }

    override fun stop() {
        listener?.let { runCatching { manager.unregisterService(it) } }
        listener = null
    }
}

private class NsdBrowser(private val manager: NsdManager) : CameraBrowser {

    private val _cameras = MutableStateFlow<List<CameraEndpoint>>(emptyList())
    override val cameras: StateFlow<List<CameraEndpoint>> = _cameras.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null

    override fun start() {
        stop()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                resolve(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _cameras.update { list -> list.filter { it.name != info.serviceName } }
            }
        }
        listener = discovery
        runCatching {
            manager.discoverServices(Bmp.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        }
    }

    override fun stop() {
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null
        _cameras.value = emptyList()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        // resolveService is deprecated on API 34+ in favour of registerServiceInfoCallback,
        // but it is the only resolver available all the way back to the minSdk 26 floor.
        manager.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = (resolved.host as? Inet4Address)?.hostAddress ?: return
                    val attributes = resolved.attributes
                    val name = attributes[Bmp.TXT_NAME]?.decodeToString()
                        ?: resolved.serviceName
                        ?: host
                    val endpoint = CameraEndpoint(
                        name = name,
                        host = host,
                        port = resolved.port,
                        source = CameraEndpoint.Source.MDNS,
                    )
                    _cameras.update { list ->
                        list.filterNot { it.id == endpoint.id } + endpoint
                    }
                }
            },
        )
    }
}
