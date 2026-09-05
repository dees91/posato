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
    fun `ignores absent and very short values so ordinary words survive`() {
        val redaction = Redaction()
        redaction.register(null)
        redaction.register("US")

        assertEquals("a US subject", redaction.redact("a US subject"))
    }
}
