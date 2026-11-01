package app.posato.desktop.macos

import app.posato.feature.enforcement.DomainWindowPlan
import app.posato.feature.enforcement.WindowSegment
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

    /**
     * The window plans reach the helper in the bytes its decoder reads (WebsiteWindowTests in macosHelper); a
     * mismatch would leave every window shut or fail the session's start.
     */
    @Test
    fun `given window plans when encoded then the bytes match the helper vector`() {
        val payload = BrowserDomainConfigurePayload(
            domains = listOf("example.com", "example.org"),
            sessionEndEpochMilliseconds = 1_793_530_800_000,
            windows = listOf(
                DomainWindowPlan(
                    "example.com",
                    listOf(WindowSegment(1_793_527_800_000, 0, 0), WindowSegment(1_793_530_800_000, 30, 5)),
                ),
            ),
        )

        assertEquals(WINDOW_PAYLOAD_VECTOR, payload.encode().joinToString("") { byte -> "%02x".format(byte) })
        assertEquals(payload, BrowserDomainConfigurePayload.decode(payload.encode()))
    }

    @Test
    fun `given plans that do not fit the frame when encoded then they are left out and every website stays blocked`() {
        val domains = (0 until 1_000).map { index -> "w$index-${"a".repeat(200)}.example" }
        val segments = (1..22).map { index -> WindowSegment(1_793_527_800_000L + index * 60_000L, 30, 5) }
        val payload = BrowserDomainConfigurePayload(
            domains = domains,
            sessionEndEpochMilliseconds = 1_793_530_800_000,
            windows = domains.map { domain -> DomainWindowPlan(domain, segments) },
        )

        val decoded = BrowserDomainConfigurePayload.decode(payload.encode())

        assertEquals(domains, decoded.domains)
        assertTrue(decoded.windows.isEmpty())
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

private const val WINDOW_PAYLOAD_VECTOR: String =
    "0002" + "0b6578616d706c652e636f6d" + "0b6578616d706c652e6f7267" + "01" + "000001a196c42780" +
        "01" + "0001" + "0b6578616d706c652e636f6d" + "02" + "000001a1969660c0" + "00" + "00" + "000001a196c42780" + "1e" + "05"
