package com.hazemafaneh.babymonitorpro.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TailnetTest {

    @Test
    fun `a name from the recents list is asked directly, and asked first`() {
        val candidates = Tailnet.candidates(
            ownAddresses = listOf("100.101.5.9", "192.168.1.32"),
            knownHosts = listOf("nursery.example-tailnet.ts.net", "192.168.1.38"),
        )

        // The host that has answered before is the first thing asked, ahead of 700-odd
        // guesses. Before this it was not asked at all.
        assertEquals("nursery.example-tailnet.ts.net", candidates.first())
        // The LAN address in recents is left to the WiFi search, which already covers it.
        assertFalse("192.168.1.38" in candidates)
    }

    @Test
    fun `a host from any other VPN is asked too, with nothing tailscale-specific about it`() {
        val candidates = Tailnet.candidates(
            ownAddresses = listOf("100.101.5.9"),
            knownHosts = listOf("cot-phone.internal"),
        )

        assertEquals("cot-phone.internal", candidates.first())
    }

    @Test
    fun `a known tailnet address contributes its own block`() {
        val candidates = Tailnet.candidates(
            ownAddresses = listOf("100.101.5.9"),
            knownHosts = listOf("100.77.31.4"),
        )

        assertEquals("100.77.31.4", candidates.first())
        assertTrue("100.77.31.200" in candidates)
        assertTrue("100.101.5.1" in candidates)
    }

    @Test
    fun `nothing is probed without a tailnet address of our own`() {
        assertTrue(
            Tailnet.candidates(
                ownAddresses = listOf("192.168.1.32"),
                knownHosts = listOf("stk-l21.tail20268e.ts.net"),
            ).isEmpty(),
        )
    }

    @Test
    fun `this device is never asked about itself`() {
        val own = "100.101.5.9"
        assertFalse(own in Tailnet.candidates(ownAddresses = listOf(own)))
    }
}

class LanTest {

    @Test
    fun `a known host on this network is asked first`() {
        val candidates = Lan.candidates(
            ownAddresses = listOf("192.168.1.32"),
            knownHosts = listOf("192.168.1.38", "nursery.example-tailnet.ts.net"),
        )

        assertEquals("192.168.1.38", candidates.first())
        // A tailnet name is not this network's business; the tailnet probe has it.
        assertFalse("nursery.example-tailnet.ts.net" in candidates)
    }

    @Test
    fun `the block around this device is swept`() {
        val candidates = Lan.candidates(ownAddresses = listOf("10.0.0.7"))

        assertTrue("10.0.0.1" in candidates)
        assertTrue("10.0.0.254" in candidates)
        assertFalse("10.0.0.7" in candidates)
    }

    @Test
    fun `loopback and link-local are never swept`() {
        assertTrue(Lan.candidates(ownAddresses = listOf("127.0.0.1", "169.254.4.4")).isEmpty())
    }
}
