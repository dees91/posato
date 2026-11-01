package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.WebsiteWindow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Golden vectors for kind 22 `set-domain-window`, written by hand from the ADR 0006 TARGETS-013 amendment:
 * 16-byte set, the kind 2 domain payload, `u8` period, `u8` length; 0 and 0 is none.
 */
class WindowOperationCodecTest {
    private val workSet = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val domain = checkNotNull(ExactDomain.restore("news.example"))
    private val domainBytes = u16(12) + "news.example".encodeToByteArray()

    private fun windowBytes(
        set: ByteArray,
        period: Int,
        length: Int,
    ): ByteArray {
        return byteArrayOf(22) + set + domainBytes + byteArrayOf(period.toByte(), length.toByte())
    }

    @Test
    fun `given every valid pair and the none pair when encoded then the plaintext is the hand-written vector and decodes back`() {
        val vectors = WebsiteWindow.ALL.map { window ->
            SyncOperationPayload.SetDomainWindow(workSet, domain, window) to windowBytes(identifier(80), window.periodMinutes, window.lengthMinutes)
        } + listOf(
            SyncOperationPayload.SetDomainWindow(workSet, domain, null) to windowBytes(identifier(80), 0, 0),
            SyncOperationPayload.SetDomainWindow(PauseSetId.FIRST, domain, null) to windowBytes(ByteArray(SyncFormatLimits.IDENTIFIER_BYTES), 0, 0),
        )

        vectors.forEachIndexed { index, (payload, payloadBytes) ->
            val operation = testOperation(80 + index, 2, payload)
            val expected = header(80 + index, 2) + payloadBytes

            assertContentEquals(expected, SyncOperationCodec.encode(operation), "vector $index")
            assertEquals(operation, SyncOperationCodec.decode(expected), "vector $index")
        }
    }

    @Test
    fun `given a pair outside the fixed choices when decoded then the operation is refused`() {
        listOf(0 to 5, 30 to 0, 45 to 5, 30 to 7, 60 to 20, 240 to 15, 255 to 255, 5 to 30).forEach { (period, length) ->
            assertNull(SyncOperationCodec.decode(header(90, 2) + windowBytes(identifier(80), period, length)), "$period/$length")
        }
    }

    @Test
    fun `given an invalid set identifier or trailing or missing bytes when decoded then the operation is refused`() {
        val versionOne = identifier(80).also { bytes -> bytes[6] = 0x10 }
        val valid = windowBytes(identifier(80), 30, 5)

        assertNotNull(SyncOperationCodec.decode(header(91, 2) + valid))
        assertNull(SyncOperationCodec.decode(header(91, 2) + windowBytes(versionOne, 30, 5)))
        assertNull(SyncOperationCodec.decode(header(91, 2) + valid + byteArrayOf(0)))
        assertNull(SyncOperationCodec.decode(header(91, 2) + valid.copyOf(valid.size - 1)))
        assertNull(SyncOperationCodec.decode(header(91, 2) + byteArrayOf(22) + identifier(80) + byteArrayOf(30, 5)))
    }
}
