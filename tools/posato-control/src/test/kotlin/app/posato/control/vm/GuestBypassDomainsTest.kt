package app.posato.control.vm

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `networksetup -getproxybypassdomains` prints one entry per line, or a sentence when the list is empty. MACOS-024
 * compares these lists before, during, and after a session, so reading the sentence as an entry would turn an empty
 * list into a one-entry list and hide or fake a restore failure.
 */
class GuestBypassDomainsTest {
    @Test
    fun `entries are read in order`() {
        val output = "*.local\n169.254/16\nlocalhost\n127.0.0.1\n::1\n"

        assertEquals(listOf("*.local", "169.254/16", "localhost", "127.0.0.1", "::1"), parseBypassDomains(output))
    }

    @Test
    fun `the empty-list sentence is no entry`() {
        assertEquals(emptyList(), parseBypassDomains("There aren't any bypass domains set on Ethernet.\n"))
    }
}
