package app.posato.control

import app.posato.control.core.ControlJson
import app.posato.control.model.Envelope
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainTest {
    private fun capture(vararg args: String): Pair<Int, String> {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        return try {
            run(arrayOf(*args)) to buffer.toString()
        } finally {
            System.setOut(original)
        }
    }

    @Test
    fun `an invalid target produces a usage envelope and exit code 2`() {
        val (exitCode, output) = capture("status", "-t", "mars")
        assertEquals(2, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals(false, envelope.ok)
        assertEquals("USAGE", envelope.error?.code)
        assertTrue(envelope.error?.message.orEmpty().contains("mars"), envelope.error?.message)
    }

    @Test
    fun `a missing required option produces a usage envelope`() {
        val (exitCode, output) = capture("type", "-t", "desktop")
        assertEquals(2, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("USAGE", envelope.error?.code)
        assertTrue(envelope.error?.message.orEmpty().contains("--text-input"), envelope.error?.message)
        assertTrue(envelope.error?.message.orEmpty().lowercase().contains("missing"), envelope.error?.message)
    }

    @Test
    fun `an unknown option names the option in the envelope`() {
        val (exitCode, output) = capture("--bogus")
        assertEquals(2, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertTrue(envelope.error?.message.orEmpty().contains("--bogus"), envelope.error?.message)
    }

    @Test
    fun `help still exits with zero`() {
        val (exitCode, output) = capture("--help")
        assertEquals(0, exitCode)
        assertTrue(output.contains("Usage"), output)
    }
}
