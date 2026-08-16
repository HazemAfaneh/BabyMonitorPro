package com.hazemafaneh.babymonitorpro.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingUriTest {

    @Test
    fun buildsAndParsesTheSameLink() {
        val uri = PairingUri.build("192.168.1.42", 8080, "123456")
        assertEquals("bmpro://192.168.1.42:8080?pin=123456", uri)

        val parsed = PairingUri.parse(uri)
        assertEquals("192.168.1.42", parsed?.host)
        assertEquals(8080, parsed?.port)
        assertEquals("123456", parsed?.pin)
    }

    @Test
    fun omitsThePinWhenThereIsNone() {
        val uri = PairingUri.build("10.0.0.5", 8080, null)
        assertEquals("bmpro://10.0.0.5:8080", uri)
        assertNull(PairingUri.parse(uri)?.pin)
    }

    @Test
    fun rejectsLinksThatAreNotPairingLinks() {
        assertNull(PairingUri.parse("http://192.168.1.42:8080"))
        assertNull(PairingUri.parse("192.168.1.42"))
        assertNull(PairingUri.parse(""))
    }

    @Test
    fun manualEntryAcceptsHostWithAndWithoutPort() {
        assertEquals("192.168.1.42" to 8080, PairingUri.parseHostPort("192.168.1.42"))
        assertEquals("192.168.1.42" to 9000, PairingUri.parseHostPort("192.168.1.42:9000"))
        assertEquals("nursery.local" to 8080, PairingUri.parseHostPort(" nursery.local/ "))
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
