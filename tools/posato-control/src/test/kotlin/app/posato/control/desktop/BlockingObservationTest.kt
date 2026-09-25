package app.posato.control.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockingObservationTest {
    @Test
    fun `reads the HTTP proxy Posato installs from scutil`() {
        val output =
            """
            <dictionary> {
              ExceptionsList : <array> {
                0 : *.local
              }
              HTTPEnable : 1
              HTTPPort : 49152
              HTTPProxy : 127.0.0.1
              HTTPSEnable : 1
            }
            """.trimIndent()

        assertEquals(HttpProxy("127.0.0.1", 49152), parseHttpProxy(output))
    }

    @Test
    fun `a disabled or absent HTTP proxy means a direct connection`() {
        assertNull(parseHttpProxy("<dictionary> {\n  HTTPEnable : 0\n  HTTPPort : 49152\n  HTTPProxy : 127.0.0.1\n}"))
        assertNull(parseHttpProxy("<dictionary> {\n  ExcludeSimpleHostnames : 0\n}"))
    }

    @Test
    fun `the pause page counts as paused whatever its end time`() {
        assertEquals(PageOutcome.PAUSED, classifyPage(200, "<title>This site is paused until 16:30 — Posato</title>"))
        assertEquals(PageOutcome.PAUSED, classifyPage(200, "<h1>This site is paused</h1>"))
    }

    @Test
    fun `a real page counts as loaded and a failed request as unreachable`() {
        assertEquals(PageOutcome.LOADED, classifyPage(200, "<title>Example Domain</title>"))
        assertEquals(PageOutcome.UNREACHABLE, classifyPage(0, ""))
        assertEquals(PageOutcome.UNREACHABLE, classifyPage(502, "Bad gateway"))
    }

    @Test
    fun `a redirect is followed, so only a final page counts as loaded`() {
        assertEquals(PageOutcome.UNREACHABLE, classifyPage(302, ""))
        val command = curlCommand("http://example.com/", HttpProxy("127.0.0.1", 49152))

        assertTrue("-L" in command && "--max-redirs" in command, command.toString())
        assertEquals(listOf("--proxy", "http://127.0.0.1:49152", "http://example.com/"), command.takeLast(3))
        assertFalse("--proxy" in curlCommand("http://example.com/", null))
    }

    @Test
    fun `an application runs when Launch Services lists a process for its bundle`() {
        assertTrue(isListed("ASN:0x0-0x2d02d:\n"))
        assertFalse(isListed(""))
        assertFalse(isListed("\n"))
    }

    @Test
    fun `splits the status code curl appends after the body`() {
        assertEquals("<p>body</p>\nmore" to 200, splitCurlOutput("<p>body</p>\nmore\n200"))
        assertEquals("" to 0, splitCurlOutput("000"))
    }
}
