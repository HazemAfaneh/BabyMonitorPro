package com.hazemafaneh.babymonitorpro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingUriTest {

    @Test
    fun buildsAndParsesTheSameLink() {
        val uri = PairingUri.build("192.168.1.42", 8080)
        assertEquals("bmpro://192.168.1.42:8080", uri)

        val parsed = PairingUri.parse(uri)
        assertEquals("192.168.1.42", parsed?.host)
        assertEquals(8080, parsed?.port)
    }

    @Test
    fun ignoresAnyQueryOnTheLink() {
        val parsed = PairingUri.parse("bmpro://10.0.0.5:8080?audio=1")
        assertEquals("10.0.0.5", parsed?.host)
        assertEquals(8080, parsed?.port)
    }

    @Test
    fun rejectsLinksThatAreNotPairingLinks() {
        assertNull(PairingUri.parse("http://192.168.1.42:8080"))
        assertNull(PairingUri.parse("192.168.1.42"))
        assertNull(PairingUri.parse(""))
    }

    @Test
    fun manualEntryAcceptsHostWithAndWithoutPort() {
        assertEquals("192.168.1.42" to Bmp.DEFAULT_PORT, PairingUri.parseHostPort("192.168.1.42"))
        assertEquals("192.168.1.42" to 9000, PairingUri.parseHostPort("192.168.1.42:9000"))
        assertEquals("nursery.local" to Bmp.DEFAULT_PORT, PairingUri.parseHostPort(" nursery.local/ "))
        assertNull(PairingUri.parseHostPort("192.168.1.42:notaport"))
        assertNull(PairingUri.parseHostPort("192.168.1.42:70000"))
    }

    @Test
    fun onlyLanAddressesCountAsPrivate() {
        assertTrue(isPrivateIpv4("192.168.0.10"))
        assertTrue(isPrivateIpv4("10.1.2.3"))
        assertTrue(isPrivateIpv4("172.20.0.1"))
        assertTrue(isPrivateIpv4("169.254.1.1"))

        assertTrue(!isPrivateIpv4("8.8.8.8"))
        assertTrue(!isPrivateIpv4("172.32.0.1"))
        assertTrue(!isPrivateIpv4("not.an.ip.address"))
    }
}
