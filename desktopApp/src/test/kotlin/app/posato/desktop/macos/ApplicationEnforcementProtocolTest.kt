package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationEnforcementProtocolTest {
    @Test
    fun `given requirements when encoded then the payload round trips`() {
        val payload = ApplicationEnforcementPayload(
            requirements = listOf(byteArrayOf(7, 7, 7), byteArrayOf(8)),
            sessionEndEpochMilliseconds = 1_750_000_000_000,
        )

        assertEquals(payload, ApplicationEnforcementPayload.decode(payload.encode()))
    }

    @Test
    fun `given empty set when encoded then clear round trips`() {
        val payload = ApplicationEnforcementPayload(
            requirements = emptyList(),
            sessionEndEpochMilliseconds = null,
        )

        assertEquals(payload, ApplicationEnforcementPayload.decode(payload.encode()))
    }

    @Test
    fun `given duplicate or oversized requirements when created then they are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ApplicationEnforcementPayload(
                requirements = listOf(byteArrayOf(1), byteArrayOf(1)),
                sessionEndEpochMilliseconds = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ApplicationEnforcementPayload(
                requirements = listOf(ByteArray(ApplicationEnforcementLimits.MAXIMUM_REQUIREMENT_BYTES + 1)),
                sessionEndEpochMilliseconds = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ApplicationEnforcementPayload(
                requirements = listOf(byteArrayOf()),
                sessionEndEpochMilliseconds = null,
            )
        }
    }

    @Test
    fun `given configure deadline when decoded then lifecycle maximum is rejected`() {
        val configure = message(
            HelperOperation.ConfigureApplications,
            MacOsHelperProtocol.MAXIMUM_CONFIGURE_DEADLINE_MILLISECONDS,
        )
        assertEquals(configure, MacOsHelperProtocol.decode(MacOsHelperProtocol.encode(configure)))

        val oversized = message(
            HelperOperation.ConfigureApplications,
            MacOsHelperProtocol.MAXIMUM_LIFECYCLE_DEADLINE_MILLISECONDS,
        )
        assertFailsWith<IllegalArgumentException> {
            MacOsHelperProtocol.decode(MacOsHelperProtocol.encode(oversized))
        }
    }

    @Test
    fun `given successful response bytes when decoded then the accepted count is preserved`() {
        val payload = byteArrayOf(1, 3, 1, 0, 0, 0, 2)
        val response = ApplicationEnforcementResponse.decode(payload)

        assertEquals(HelperResult.Outcome.Success, response.result.outcome)
        assertEquals(2, response.acceptedCount)
    }

    @Test
    fun `given failed response bytes when accepted count is nonzero then they are rejected`() {
        val payload = byteArrayOf(5, 3, 1, 0, 1, 0, 1)
        assertFailsWith<IllegalArgumentException> {
            ApplicationEnforcementResponse.decode(payload)
        }
    }

    @Test
    fun `given sensitive enforcement values when rendered then they remain redacted`() {
        val secret = "canary-requirement".encodeToByteArray()
        val payload = ApplicationEnforcementPayload(listOf(secret), 42)
        val response = ApplicationEnforcementResponse.decode(byteArrayOf(1, 3, 1, 0, 0, 0, 1))

        assertFalse(payload.toString().contains("canary-requirement"))
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
