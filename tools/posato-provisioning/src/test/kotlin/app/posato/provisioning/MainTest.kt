package app.posato.provisioning

import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.Envelope
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun `an unknown command prints a usage envelope and exits with two`() {
        val (exitCode, output) = capture("teleport")

        assertEquals(2, exitCode)
        val envelope = decode(output)
        assertEquals(false, envelope.ok)
        assertEquals("USAGE", envelope.error?.code)
    }

    private fun decode(output: String): Envelope {
        val json = output.substring(output.indexOf('{'), output.lastIndexOf('}') + 1)
        return ProvisioningJson.lenient.decodeFromString(Envelope.serializer(), json)
    }

    private fun capture(vararg args: String): Pair<Int, String> {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        return try {
            System.setOut(PrintStream(buffer, true))
            run(arrayOf(*args)) to buffer.toString()
        } finally {
            System.setOut(original)
        }
    }
}
