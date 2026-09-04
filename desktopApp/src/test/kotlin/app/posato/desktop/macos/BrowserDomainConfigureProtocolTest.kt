package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserDomainConfigureProtocolTest {
    @Test
    fun `given canonical domains when encoded then the payload round trips`() {
        val payload = BrowserDomainConfigurePayload(
            domains = listOf("example.com", "xn--nxasmq6b.example"),
            sessionEndEpochMilliseconds = 1_725_000_000_000,
        )

        assertEquals(payload, BrowserDomainConfigurePayload.decode(payload.encode()))
    }

    @Test
    fun `given empty or duplicate domains when created then they are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BrowserDomainConfigurePayload(domains = emptyList(), sessionEndEpochMilliseconds = null)
        }
        assertFailsWith<IllegalArgumentException> {
            BrowserDomainConfigurePayload(
                domains = listOf("example.com", "example.com"),
                sessionEndEpochMilliseconds = null,
            )
        }
    }

    @Test
    fun `given configure deadline when decoded then lifecycle maximum is rejected`() {
        val configure = message(
            HelperOperation.ConfigureBrowserDomains,
            MacOsHelperProtocol.MAXIMUM_CONFIGURE_DEADLINE_MILLISECONDS,
        )
        assertEquals(configure, MacOsHelperProtocol.decode(MacOsHelperProtocol.encode(configure)))

        val oversized = message(
            HelperOperation.ConfigureBrowserDomains,
            MacOsHelperProtocol.MAXIMUM_LIFECYCLE_DEADLINE_MILLISECONDS,
        )
        assertFailsWith<IllegalArgumentException> {
            MacOsHelperProtocol.decode(MacOsHelperProtocol.encode(oversized))
        }
    }

    @Test
    fun `given successful configure bytes when decoded then the port is preserved`() {
        val payload = byteArrayOf(1, 3, 1, 0, 0, 0x11, 0x5B)
        val response = BrowserDomainConfigureResponse.decode(payload)

        assertEquals(HelperResult.Outcome.Success, response.result.outcome)
        assertEquals(4443.toUShort(), response.port)
    }

    @Test
    fun `given failed configure bytes when port is nonzero then they are rejected`() {
        val payload = byteArrayOf(5, 3, 1, 0, 1, 0, 1)
        assertFailsWith<IllegalArgumentException> {
            BrowserDomainConfigureResponse.decode(payload)
        }
    }

    @Test
    fun `given sensitive configure values when rendered then they remain redacted`() {
        val secret = "secret.example"
        val payload = BrowserDomainConfigurePayload(listOf(secret), 42)
        val response = BrowserDomainConfigureResponse.decode(byteArrayOf(1, 3, 1, 0, 0, 0x45, 0x69.toByte()))

        assertFalse(payload.toString().contains(secret))
        assertTrue(payload.toString().contains("redacted"))
        assertTrue(response.toString().contains("redacted"))
    }

    private fun message(
        operation: HelperOperation,
        deadline: Int,
    ): HelperMessage {
        return HelperMessage(
            kind = HelperMessageKind.Request,
            operation = operation,
            sequence = 1,
            deadlineMilliseconds = deadline,
            connectionIdentifier = ByteArray(16),
            sessionIdentifier = ByteArray(16),
            requestIdentifier = ByteArray(16),
            payload = byteArrayOf(),
        )
    }
}
