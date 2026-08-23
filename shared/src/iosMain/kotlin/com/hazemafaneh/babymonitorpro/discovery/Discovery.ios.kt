package com.hazemafaneh.babymonitorpro.discovery

import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject

/**
 * Bonjour through NSNetService rather than Network.framework's NWBrowser.
 *
 * NWBrowser is the modern API but its Kotlin/Native surface is raw C with dispatch blocks;
 * NSNetService exposes the same mDNS responder through delegates that map cleanly onto
 * Kotlin, and it is still the shortest correct path for a service this simple.
 *
 * Requires NSLocalNetworkUsageDescription and NSBonjourServices in Info.plist — without
 * them iOS silently returns nothing.
 */
actual fun createAdvertiser(): CameraAdvertiser? = IosAdvertiser()

actual fun createBrowser(): CameraBrowser? = IosBrowser()

private const val LOCAL_DOMAIN = "local."

private class IosAdvertiser : CameraAdvertiser {

    private var service: NSNetService? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun start(deviceName: String, port: Int) {
        stop()
        val published = NSNetService(
            domain = LOCAL_DOMAIN,
            type = "${Bmp.SERVICE_TYPE}.",
            name = deviceName.ifBlank { "BabyMonitor Pro" },
            port = port,
        )
        val txt = mapOf<Any?, Any?>(
            Bmp.TXT_NAME to deviceName.toNSData(),
            Bmp.TXT_VERSION to Bmp.PROTOCOL_VERSION.toString().toNSData(),
        )
        published.setTXTRecordData(NSNetService.dataFromTXTRecordDictionary(txt))
        published.publish()
        service = published
    }

    override fun stop() {
        service?.stop()
        service = null
    }
}

private class IosBrowser : CameraBrowser {

    private val _cameras = MutableStateFlow<List<CameraEndpoint>>(emptyList())
    override val cameras: StateFlow<List<CameraEndpoint>> = _cameras.asStateFlow()

    // Strong references: NSNetService drops resolution the moment nothing retains it.
    private val pending = mutableListOf<NSNetService>()
    private val browser = NSNetServiceBrowser()

    private val delegate = object : NSObject(),
        NSNetServiceBrowserDelegateProtocol,
        NSNetServiceDelegateProtocol {

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            didFindService.delegate = this
            pending += didFindService
            didFindService.resolveWithTimeout(RESOLVE_TIMEOUT_SECONDS)
        }

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didRemoveService: NSNetService,
            moreComing: Boolean,
        ) {
            val name = didRemoveService.name
            _cameras.update { list -> list.filterNot { it.name == name } }
            pending.remove(didRemoveService)
        }

        override fun netServiceDidResolveAddress(sender: NSNetService) {
            // A Bonjour ".local" name, which iOS resolves for us on the HTTP request.
            val host = sender.hostName?.trimEnd('.') ?: return
            val txt = sender.TXTRecordData()?.let { NSNetService.dictionaryFromTXTRecordData(it) }

            val endpoint = CameraEndpoint(
                name = txt.stringFor(Bmp.TXT_NAME) ?: sender.name,
                host = host,
                port = sender.port.toInt(),
                source = CameraEndpoint.Source.MDNS,
            )
            _cameras.update { list -> list.filterNot { it.id == endpoint.id } + endpoint }
        }

        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            pending.remove(sender)
        }
    }

    override fun start() {
        stop()
        browser.delegate = delegate
        browser.searchForServicesOfType("${Bmp.SERVICE_TYPE}.", LOCAL_DOMAIN)
    }

    override fun stop() {
        browser.stop()
        pending.clear()
        _cameras.value = emptyList()
    }

    private companion object {
        const val RESOLVE_TIMEOUT_SECONDS = 4.0
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData =
    NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()

@OptIn(ExperimentalForeignApi::class)
private fun Map<Any?, *>?.stringFor(key: String): String? {
    val data = this?.get(key) as? NSData ?: return null
    return NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
}
