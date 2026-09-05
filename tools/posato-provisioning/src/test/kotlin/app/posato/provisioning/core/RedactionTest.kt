package app.posato.provisioning.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedactionTest {
    @Test
    fun `rewrites every registered value wherever it appears`() {
        val redaction = Redaction()
        redaction.register("ABCDE12345")
        redaction.register("00008030-000102030405061E")

        val line = "device 00008030-000102030405061E of team ABCDE12345 (ABCDE12345)"
        val redacted = redaction.redact(line)

        assertFalse(redacted.contains("ABCDE12345"))
        assertFalse(redacted.contains("00008030-000102030405061E"))
        assertEquals("device <redacted> of team <redacted> (<redacted>)", redacted)
    }

    @Test
    fun `rewrites a home directory that reaches a message through a path`() {
        // Helper commands quote the file they act on, tools echo the file they failed on, and an IOException carries
        // its path. Registering the home directory closes all of those at once, and envelopes get pasted into
        // records where a personal path is forbidden.
        val redaction = Redaction()
        redaction.register("/Users/someone")

        val line = "$ /usr/bin/security cms -D -i /Users/someone/Library/Developer/Posato/Posato_macOS_Sync_Development.provisionprofile"

        assertEquals(
            "$ /usr/bin/security cms -D -i <redacted>/Library/Developer/Posato/Posato_macOS_Sync_Development.provisionprofile",
            redaction.redact(line),
        )
    }

    @Test
    fun `ignores absent and very short values so ordinary words survive`() {
        val redaction = Redaction()
        redaction.register(null)
        redaction.register("US")

        assertEquals("a US subject", redaction.redact("a US subject"))
    }
}
