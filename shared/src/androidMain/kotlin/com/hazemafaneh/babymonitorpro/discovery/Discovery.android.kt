package com.hazemafaneh.babymonitorpro.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
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

/**
 * Stops the WiFi chip filtering multicast away while we are looking.
 *
 * Android drops multicast by default: the radio discards anything not addressed to this
 * device, because waking the CPU for the neighbours' chatter is what flattens a battery
 * overnight. mDNS is entirely multicast.
 *
 * Belt and braces rather than a known fix. NsdManager talks to a system daemon that does
 * its own receiving, so on a well-behaved device this lock changes nothing — but the
 * manifest has carried CHANGE_WIFI_MULTICAST_STATE since before any of this code, on the
 * theory that some OEM stacks need it, and holding the lock is what that permission is for.
 * Held only while a browser or an advertiser is actually running, so an idle night in the
 * nursery costs nothing.
 *
 * Reference counted, because the camera role runs an advertiser and a browser at once and
 * whichever stopped second would otherwise release the lock out from under the other.
 */
private object MulticastLock {

    private val wifi: WifiManager?
        get() = AndroidPlatformContext.applicationContext
            ?.applicationContext
            ?.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var lock: WifiManager.MulticastLock? = null
    private var holders = 0

    @Synchronized
    fun acquire() {
        holders++
        if (lock != null) return
        lock = runCatching {
            wifi?.createMulticastLock("babymonitorpro-mdns")?.apply {
                // Not reference counted at the Android level: this object does the counting,
                // and a lock that counts too would need release called exactly as often as
                // acquire across callbacks that are not guaranteed to be paired.
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        runCatching { lock?.takeIf { it.isHeld }?.release() }
        lock = null
    }
}

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
        MulticastLock.acquire()
        val registered = runCatching {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        }.isSuccess
        if (!registered) {
            listener = null
            MulticastLock.release()
        }
    }

    override fun stop() {
        val current = listener ?: return
        listener = null
        runCatching { manager.unregisterService(current) }
        MulticastLock.release()
    }
}

private class NsdBrowser(private val manager: NsdManager) : CameraBrowser {

    private val _cameras = MutableStateFlow<List<CameraEndpoint>>(emptyList())
    override val cameras: StateFlow<List<CameraEndpoint>> = _cameras.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null

    /**
     * Services found but not yet resolved, oldest first.
     *
     * Android's resolver takes one job at a time. A second `resolveService` while the first
     * is in flight comes straight back as FAILURE_ALREADY_ACTIVE and that service is simply
     * lost — which is why the old code reliably found one camera in a house that had two,
     * and why which one it found depended on which phone answered the query first.
     */
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    override fun start() {
        stop()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                enqueue(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _cameras.update { list -> list.filter { it.name != info.serviceName } }
            }
        }
        listener = discovery
        MulticastLock.acquire()
        val started = runCatching {
            manager.discoverServices(Bmp.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        }.isSuccess
        if (!started) {
            listener = null
            MulticastLock.release()
        }
    }

    override fun stop() {
        val current = listener
        listener = null
        synchronized(pending) {
            pending.clear()
            resolving = false
        }
        _cameras.value = emptyList()
        if (current == null) return
        runCatching { manager.stopServiceDiscovery(current) }
        MulticastLock.release()
    }

    private fun enqueue(info: NsdServiceInfo) {
        synchronized(pending) {
            pending.addLast(info)
            if (resolving) return
            resolving = true
        }
        drain()
    }

    /** Takes the next service off the queue, or stands down if there is none. */
    private fun drain() {
        val next = synchronized(pending) {
            val head = pending.removeFirstOrNull()
            if (head == null) resolving = false
            head
        } ?: return
        resolve(next)
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        // resolveService is deprecated on API 34+ in favour of registerServiceInfoCallback,
        // but it is the only resolver available all the way back to the minSdk 26 floor.
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                // Nothing to retry on. A name that would not resolve this pass will be
                // offered again by the next announcement, and re-queueing it here risks a
                // tight loop against a camera that has just gone off the air.
                drain()
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                record(resolved)
                drain()
            }
        }
        val dispatched = runCatching { manager.resolveService(info, listener) }.isSuccess
        // The call itself threw, so neither callback will ever arrive to move the queue on.
        if (!dispatched) drain()
    }

    private fun record(resolved: NsdServiceInfo) {
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
}
