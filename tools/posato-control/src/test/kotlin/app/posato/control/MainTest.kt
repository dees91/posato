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
    fun `every desktop element command accepts the process selector`() {
        val commands = listOf("snapshot", "find", "tap", "type", "press", "wait", "screenshot")
        commands.forEach { command ->
            val (exitCode, output) = capture(command, "-t", "desktop", "--process", "PosatoMacOSHelper", "--help")
            assertEquals(0, exitCode, command)
            assertTrue(output.contains("--process"), command)
        }
    }

    @Test
    fun `the process selector is refused on an iOS target`() {
        val (exitCode, output) = capture("snapshot", "-t", "sim", "--process", "PosatoMacOSHelper")
        assertEquals(6, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("UNSUPPORTED_ON_TARGET", envelope.error?.code)
        assertTrue(envelope.error?.message.orEmpty().contains("--process"), envelope.error?.message)
    }

    @Test
    fun `a scenario run does not accept the process selector`() {
        val (exitCode, output) = capture("run", "-t", "desktop", "--scenario", "-", "--process", "PosatoMacOSHelper")
        assertEquals(2, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("USAGE", envelope.error?.code)
    }

    @Test
    fun `an element command without a query or a process selector still needs a query`() {
        val (exitCode, output) = capture("type", "-t", "desktop", "--input", "text")
        assertEquals(2, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("USAGE", envelope.error?.code)
        assertTrue(envelope.error?.message.orEmpty().contains("query"), envelope.error?.message)
    }

    @Test
    fun `typing into the focused element is refused on an iOS target`() {
        val (exitCode, output) = capture("type", "-t", "sim", "--input", "text", "--process", "PosatoMacOSHelper")
        assertEquals(6, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("UNSUPPORTED_ON_TARGET", envelope.error?.code)
    }

    @Test
    fun `a queryless process wait refuses a state that has no window meaning`() {
        val (exitCode, output) = capture("wait", "-t", "desktop", "--process", "PosatoMacOSHelper", "--for", "settled")
        assertEquals(2, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("USAGE", envelope.error?.code)
        assertTrue(envelope.error?.message.orEmpty().contains("exists or absent"), envelope.error?.message)
    }

    @Test
    fun `given the desktop target when orient runs then it is refused before any launch`() {
        val (exitCode, output) = capture("orient", "-t", "desktop", "--to", "landscapeLeft")
        assertEquals(6, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("UNSUPPORTED_ON_TARGET", envelope.error?.code)
    }

    @Test
    fun `given an unknown orientation when orient runs then a usage envelope names the choices`() {
        val (exitCode, output) = capture("orient", "-t", "sim", "--to", "sideways")
        assertEquals(2, exitCode)
        val envelope = ControlJson.lenient.decodeFromString(Envelope.serializer(), output.substring(output.indexOf('{')))
        assertEquals("USAGE", envelope.error?.code)
        assertTrue(envelope.error?.message.orEmpty().contains("landscapeLeft"), envelope.error?.message)
    }

    @Test
    fun `help still exits with zero`() {
        val (exitCode, output) = capture("--help")
        assertEquals(0, exitCode)
        assertTrue(output.contains("Usage"), output)
    }
}
