package app.posato.feature.sync.data

import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncOperationCodecTest {
    @Test
    fun `given every format one payload when encoded and decoded then canonical bytes round trip`() {
        val domain = checkNotNull(ExactDomain.restore("alpha.example"))
        val applicationName = checkNotNull(ApplicationPolicyName.restore("Focused work"))
        val sessionId = SessionId(testIdentifier(90))
        val payloads = listOf(
            SyncOperationPayload.AuthorRegister,
            SyncOperationPayload.DomainPresent(domain),
            SyncOperationPayload.DomainAbsent(domain),
            SyncOperationPayload.ApplicationPolicyPresent(applicationName),
            SyncOperationPayload.ApplicationPolicyAbsent,
            SyncOperationPayload.SessionStart(sessionId, 1_000, 2_000),
            SyncOperationPayload.SessionEnd(sessionId),
        )

        payloads.forEachIndexed { index, payload ->
            val operation = testOperation(index + 20, index.toLong() + 1, payload)
            val encoded = checkNotNull(SyncOperationCodec.encode(operation))
            val decoded = checkNotNull(SyncOperationCodec.decode(encoded))

            assertEquals(operation, decoded)
            assertContentEquals(encoded, SyncOperationCodec.encode(decoded))
        }
    }

    @Test
    fun `given truncated trailing or unknown operation bytes when decoded then input is rejected`() {
        val operation = testOperation(20, 1, SyncOperationPayload.AuthorRegister)
        val encoded = checkNotNull(SyncOperationCodec.encode(operation))

        assertNull(SyncOperationCodec.decode(encoded.copyOf(encoded.size - 1)))
        assertNull(SyncOperationCodec.decode(encoded + 0))
        assertNull(SyncOperationCodec.decode(encoded.copyOf().also { bytes -> bytes[bytes.lastIndex] = 99 }))
    }
}
