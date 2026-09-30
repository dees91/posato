package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ExactDomain
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Golden vectors for the pause set kinds 12-19. Every byte is written here by hand from the ADR 0006
 * pause set amendment, so the layout is pinned independently of the codec, and the same vectors run on
 * the JVM and on iOS.
 */
class PauseSetOperationCodecTest {
    private val workSet = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val domain = checkNotNull(ExactDomain.restore("news.example"))
    private val firstSetBytes = ByteArray(SyncFormatLimits.IDENTIFIER_BYTES)
    private val domainBytes = u16(12) + "news.example".encodeToByteArray()
    private val sessionBytes = identifier(90) + u64(1_000) + u64(61_000)
    private val scheduleBytes = identifier(70) + u16(5) + "Focus".encodeToByteArray() + byteArrayOf(0x1F) + u16(540) + u16(720) +
        byteArrayOf(1)

    @Test
    fun `given each pause set kind when encoded then the full plaintext equals its hand-written vector and decodes back`() {
        val sessionId = SessionId(testIdentifier(90))
        val scheduleId = ScheduleSyncId(testIdentifier(70))
        val vectors = listOf(
            SyncOperationPayload.PauseSetPut(workSet, "Work") to byteArrayOf(12) + identifier(80) + u16(4) + "Work".encodeToByteArray(),
            SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Mine") to byteArrayOf(12) + firstSetBytes + u16(4) + "Mine".encodeToByteArray(),
            SyncOperationPayload.PauseSetRemove(workSet) to byteArrayOf(13) + identifier(80),
            SyncOperationPayload.DomainPresent(domain, workSet) to byteArrayOf(14) + identifier(80) + domainBytes,
            SyncOperationPayload.DomainPresent(domain, PauseSetId.FIRST) to byteArrayOf(14) + firstSetBytes + domainBytes,
            SyncOperationPayload.DomainAbsent(domain, workSet) to byteArrayOf(15) + identifier(80) + domainBytes,
            SyncOperationPayload.PauseSetDefault(PauseSetId.FIRST) to byteArrayOf(16) + firstSetBytes,
            SyncOperationPayload.SchedulePut(scheduleId, "Focus", 0b0011111, 540, 720, true, workSet) to
                byteArrayOf(17) + scheduleBytes + identifier(80),
            SyncOperationPayload.SessionStart(sessionId, 1_000, 61_000, workSet) to byteArrayOf(18) + sessionBytes + identifier(80),
            SyncOperationPayload.PauseSetsEnabled to byteArrayOf(19),
        )

        vectors.forEachIndexed { index, (payload, payloadBytes) ->
            val operation = testOperation(60 + index, 2, payload)
            val expected = header(60 + index, 2) + payloadBytes

            assertContentEquals(expected, SyncOperationCodec.encode(operation), "vector $index")
            assertEquals(operation, SyncOperationCodec.decode(expected), "vector $index")
        }
    }

    @Test
    fun `given legacy payloads without a set when encoded then they keep kinds 2 3 6 and 8`() {
        val legacy = listOf(
            SyncOperationPayload.DomainPresent(domain) to 2,
            SyncOperationPayload.DomainAbsent(domain) to 3,
            SyncOperationPayload.SessionStart(SessionId(testIdentifier(90)), 1_000, 61_000) to 6,
            SyncOperationPayload.SchedulePut(ScheduleSyncId(testIdentifier(70)), "Focus", 0b0011111, 540, 720, true) to 8,
        )

        legacy.forEach { (payload, kind) ->
            val encoded = assertNotNull(SyncOperationCodec.encode(testOperation(70, 2, payload)))

            assertEquals(kind, encoded[header(70, 2).size].toInt(), "kind $kind")
        }
    }

    @Test
    fun `given a set identifier that is neither a UUIDv4 nor all zero when decoded then every kind carrying it is rejected`() {
        val versionOne = identifier(80).also { bytes -> bytes[6] = 0x10 }
        val wrongVariant = identifier(80).also { bytes -> bytes[8] = 0xC0.toByte() }
        listOf<(ByteArray) -> ByteArray>(
            { set -> byteArrayOf(12) + set + u16(4) + "Work".encodeToByteArray() },
            { set -> byteArrayOf(13) + set },
            { set -> byteArrayOf(14) + set + domainBytes },
            { set -> byteArrayOf(15) + set + domainBytes },
            { set -> byteArrayOf(16) + set },
            { set -> byteArrayOf(17) + scheduleBytes + set },
            { set -> byteArrayOf(18) + sessionBytes + set },
        ).forEach { payload ->
            val kind = payload(firstSetBytes)[0]
            assertNotNull(SyncOperationCodec.decode(header(71, 2) + payload(identifier(80))), "kind $kind with a UUIDv4")
            assertNotNull(SyncOperationCodec.decode(header(71, 2) + payload(firstSetBytes)), "kind $kind with the first set")
            assertNull(SyncOperationCodec.decode(header(71, 2) + payload(versionOne)), "kind $kind with a version 1 identifier")
            assertNull(SyncOperationCodec.decode(header(71, 2) + payload(wrongVariant)), "kind $kind with a non-RFC variant")
        }
    }

    @Test
    fun `given set names at and past the rules when decoded then only one to eighty bytes without controls are accepted`() {
        assertNotNull(SyncOperationCodec.decode(setPut("a".repeat(80).encodeToByteArray())))
        assertNotNull(SyncOperationCodec.decode(setPut("été".encodeToByteArray())))
        listOf(
            ByteArray(0),
            "a".repeat(81).encodeToByteArray(),
            ("a".repeat(79) + "é").encodeToByteArray(),
            "Work\u0009".encodeToByteArray(),
            "Work\u0085".encodeToByteArray(),
            byteArrayOf(0x41, 0xC0.toByte(), 0x80.toByte()),
        ).forEach { name ->
            assertNull(SyncOperationCodec.decode(setPut(name)), name.contentToString())
        }
    }

    @Test
    fun `given invalid set names when encoded then the codec refuses them`() {
        assertNotNull(SyncOperationCodec.encode(testOperation(72, 2, SyncOperationPayload.PauseSetPut(workSet, "Work"))))
        listOf("", "a".repeat(81), "Work\u0000").forEach { name ->
            assertNull(SyncOperationCodec.encode(testOperation(72, 2, SyncOperationPayload.PauseSetPut(workSet, name))), name)
        }
    }

    @Test
    fun `given pause set payloads with trailing or missing bytes when decoded then they are rejected`() {
        listOf(
            byteArrayOf(19) to byteArrayOf(19, 0),
            byteArrayOf(13) + identifier(80) to byteArrayOf(13) + identifier(80) + byteArrayOf(0),
            byteArrayOf(16) + identifier(80) to byteArrayOf(16) + identifier(80).copyOf(15),
            byteArrayOf(17) + scheduleBytes + identifier(80) to byteArrayOf(17) + scheduleBytes,
            byteArrayOf(18) + sessionBytes + identifier(80) to byteArrayOf(18) + sessionBytes,
            byteArrayOf(14) + identifier(80) + domainBytes to byteArrayOf(14) + identifier(80),
        ).forEach { (valid, invalid) ->
            assertNotNull(SyncOperationCodec.decode(header(73, 2) + valid), "kind ${valid[0]}")
            assertNull(SyncOperationCodec.decode(header(73, 2) + invalid), "kind ${invalid[0]}")
        }
    }

    @Test
    fun `given a kind 18 start with an invalid duration when decoded or encoded then it is refused like kind 6`() {
        val tooLong = identifier(90) + u64(1_000) + u64(1_000 + SyncFormatLimits.MAX_SESSION_DURATION_MILLIS + 1)
        val payload = SyncOperationPayload.SessionStart(SessionId(testIdentifier(90)), 5_000, 5_000, workSet)

        assertNotNull(SyncOperationCodec.decode(header(74, 2) + byteArrayOf(18) + sessionBytes + identifier(80)))
        assertNull(SyncOperationCodec.decode(header(74, 2) + byteArrayOf(18) + tooLong + identifier(80)))
        assertNull(SyncOperationCodec.encode(testOperation(74, 2, payload)))
    }

    private fun setPut(name: ByteArray): ByteArray {
        return header(75, 2) + byteArrayOf(12) + identifier(80) + u16(name.size) + name
    }
}
