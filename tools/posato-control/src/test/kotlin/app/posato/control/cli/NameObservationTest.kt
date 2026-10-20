package app.posato.control.cli

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * On Android and Linux a paused website is proven by the address a name lookup returns, read from `ping` and `getent`
 * output. A misread line would report a pause as working while the site loads, or the reverse.
 */
class NameObservationTest {
    @Test
    fun `the address comes from ping's first line or getent's first field`() {
        assertEquals("127.0.0.1", lookupAddress("PING example.net (127.0.0.1) 56(84) bytes of data.\n"))
        assertEquals("104.20.26.136", lookupAddress("PING example.org (104.20.26.136) 56(84) bytes of data.\n64 bytes from ..."))
        assertEquals("0.0.0.0", lookupAddress("0.0.0.0         STREAM example.org\n0.0.0.0         DGRAM\n"))
        assertEquals("::", lookupAddress("::              STREAM example.org\n"))
        assertEquals(null, lookupAddress("ping: unknown host example.invalid\n"))
        assertEquals(null, lookupAddress(""))
    }

    @Test
    fun `a pause answers with an unroutable address, anything else is the site`() {
        listOf("0.0.0.0", "127.0.0.1", "::", "::1").forEach { assertEquals("paused", nameOutcome(it)) }
        assertEquals("loaded", nameOutcome("104.20.26.136"))
        assertEquals("loaded", nameOutcome("2606:4700::6812:1a88"))
        assertEquals("unreachable", nameOutcome(null))
    }
}
