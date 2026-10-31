package app.posato.feature.sync.data

import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.sync.domain.ScheduleOccurrenceRef
import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Golden vectors for the schedule kinds. Every byte is written here by hand from the ADR 0006
 * amendment, so the layout is pinned independently of the codec, and the same vectors run on the JVM
 * and on iOS.
 */
class ScheduleOperationCodecTest {
    private val scheduleId = ScheduleSyncId(testIdentifier(70))

    @Test
    fun `given the hand-built header when a known session end is encoded then the header builder matches the codec`() {
        val operation = testOperation(20, 3, SyncOperationPayload.SessionEnd(SessionId(testIdentifier(90))))

        assertContentEquals(header(20, 3) + byteArrayOf(7) + identifier(90), SyncOperationCodec.encode(operation))
    }

    @Test
    fun `given each schedule kind when encoded then the full plaintext equals its hand-written vector and decodes back`() {
        val date = ScheduleDate(2026, 9, 28)
        val vectors = listOf(
            SyncOperationPayload.SchedulePut(scheduleId, "Focus", 0b0011111, 540, 720, true) to
                byteArrayOf(8) + identifier(70) + u16(5) + "Focus".encodeToByteArray() + byteArrayOf(0x1F) + u16(540) + u16(720) +
                byteArrayOf(1),
            SyncOperationPayload.ScheduleRemove(scheduleId) to byteArrayOf(9) + identifier(70),
            SyncOperationPayload.ScheduleSkip(ScheduleOccurrenceRef(scheduleId, date)) to
                byteArrayOf(10) + identifier(70) + u16(2026) + byteArrayOf(9, 28),
            SyncOperationPayload.ScheduleOccurrenceEnd(ScheduleOccurrenceRef(scheduleId, date)) to
                byteArrayOf(11) + identifier(70) + u16(2026) + byteArrayOf(9, 28),
        )

        vectors.forEachIndexed { index, (payload, payloadBytes) ->
            val operation = testOperation(30 + index, 2, payload)
            val expected = header(30 + index, 2) + payloadBytes

            assertContentEquals(expected, SyncOperationCodec.encode(operation), "kind ${payloadBytes[0]}")
            assertEquals(operation, SyncOperationCodec.decode(expected), "kind ${payloadBytes[0]}")
        }
    }

    @Test
    fun `given a decomposed name when decoded then it is accepted and round-trips unchanged`() {
        val decomposed = "été"
        val bytes = put(name = decomposed.encodeToByteArray())

        val decoded = assertNotNull(SyncOperationCodec.decode(bytes))

        assertEquals(decomposed, (decoded.payload as SyncOperationPayload.SchedulePut).name)
        assertContentEquals(bytes, SyncOperationCodec.encode(decoded))
    }

    @Test
    fun `given names at and past the byte limit when decoded then eighty bytes are accepted and eighty-one are not`() {
        assertNotNull(SyncOperationCodec.decode(put(name = "a".repeat(80).encodeToByteArray())))
        assertNotNull(SyncOperationCodec.decode(put(name = ("a".repeat(78) + "\u00e9").encodeToByteArray())))
        assertNull(SyncOperationCodec.decode(put(name = "a".repeat(81).encodeToByteArray())))
        assertNull(SyncOperationCodec.decode(put(name = ("a".repeat(79) + "\u00e9").encodeToByteArray())))
        assertNull(SyncOperationCodec.decode(put(name = ByteArray(0))))
    }

    @Test
    fun `given control characters or invalid UTF-8 in a name when decoded then the operation is rejected`() {
        listOf(
            "Focus\u0009".encodeToByteArray(),
            "Focus\u007F".encodeToByteArray(),
            "Focus\u0085".encodeToByteArray(),
            byteArrayOf(0x41, 0xC0.toByte(), 0x80.toByte()),
            byteArrayOf(0x41, 0xED.toByte(), 0xA0.toByte(), 0x80.toByte()),
        ).forEach { name ->
            assertNull(SyncOperationCodec.decode(put(name = name)), name.contentToString())
        }
    }

    @Test
    fun `given a name length prefix past the end when decoded then the operation is rejected`() {
        val bytes = header(40, 2) + byteArrayOf(8) + identifier(70) + u16(200) + "Focus".encodeToByteArray()

        assertNull(SyncOperationCodec.decode(bytes))
    }

    @Test
    fun `given weekday masks at the boundary when decoded then one to one hundred twenty-seven are accepted`() {
        assertNotNull(SyncOperationCodec.decode(put(weekdays = 127)))
        assertNotNull(SyncOperationCodec.decode(put(weekdays = 1)))
        assertNull(SyncOperationCodec.decode(put(weekdays = 0)))
        assertNull(SyncOperationCodec.decode(put(weekdays = 128)))
    }

    @Test
    fun `given minutes and lengths at the boundary when decoded then only valid plans are accepted`() {
        assertNotNull(SyncOperationCodec.decode(put(start = 1439, end = 600)))
        assertNull(SyncOperationCodec.decode(put(start = 1440, end = 600)))
        assertNull(SyncOperationCodec.decode(put(start = 600, end = 0xFFFF)))
        assertNull(SyncOperationCodec.decode(put(start = 600, end = 600)))
        assertNotNull(SyncOperationCodec.decode(put(start = 1430, end = 5)))
        assertNull(SyncOperationCodec.decode(put(start = 1430, end = 4)))
        assertNotNull(SyncOperationCodec.decode(put(start = 600, end = 615)))
        assertNull(SyncOperationCodec.decode(put(start = 600, end = 614)))
    }

    @Test
    fun `given an enabled byte other than zero or one when decoded then the operation is rejected`() {
        assertNotNull(SyncOperationCodec.decode(put(enabled = 0)))
        assertNull(SyncOperationCodec.decode(put(enabled = 2)))
    }

    @Test
    fun `given dates at the calendar boundaries when decoded then only real dates from 2000 to 2100 are accepted`() {
        listOf(2000 to 2 to 29, 2024 to 2 to 29, 2100 to 12 to 31, 2000 to 1 to 1).forEach { (yearMonth, day) ->
            assertNotNull(SyncOperationCodec.decode(fact(yearMonth.first, yearMonth.second, day)), "$yearMonth $day")
        }
        listOf(
            1999 to 12 to 31,
            2101 to 1 to 1,
            2023 to 2 to 29,
            2100 to 2 to 29,
            2026 to 4 to 31,
            2026 to 13 to 1,
            2026 to 0 to 1,
            2026 to 1 to 0,
        ).forEach { (yearMonth, day) ->
            assertNull(SyncOperationCodec.decode(fact(yearMonth.first, yearMonth.second, day)), "$yearMonth $day")
        }
    }

    @Test
    fun `given invalid schedule payloads when encoded then the codec refuses them`() {
        listOf(
            SyncOperationPayload.SchedulePut(scheduleId, "", 1, 540, 720, true),
            SyncOperationPayload.SchedulePut(scheduleId, "a".repeat(81), 1, 540, 720, true),
            SyncOperationPayload.SchedulePut(scheduleId, "Focus\u0085", 1, 540, 720, true),
            SyncOperationPayload.SchedulePut(scheduleId, "Focus", 0, 540, 720, true),
            SyncOperationPayload.SchedulePut(scheduleId, "Focus", 1, 540, 550, true),
            SyncOperationPayload.SchedulePut(scheduleId, "Focus", 1, 1440, 720, true),
            SyncOperationPayload.ScheduleSkip(ScheduleOccurrenceRef(scheduleId, ScheduleDate(2023, 2, 29))),
            SyncOperationPayload.ScheduleOccurrenceEnd(ScheduleOccurrenceRef(scheduleId, ScheduleDate(1999, 1, 1))),
        ).forEach { payload ->
            assertNull(SyncOperationCodec.encode(testOperation(41, 2, payload)))
        }
    }

    @Test
    fun `given optional kinds when decoded then their tails round-trip unchanged including an empty one and one at the limit`() {
        val limitTail = SyncFormatLimits.PLAINTEXT_BYTES - header(50, 2).size - 1
        listOf(
            128 to ByteArray(0),
            200 to byteArrayOf(1, 2, 3, 0, 0xFF.toByte()),
            255 to ByteArray(limitTail) { 9 },
        ).forEach { (kind, tail) ->
            val bytes = header(50, 2) + byteArrayOf(kind.toByte()) + tail
            val decoded = assertNotNull(SyncOperationCodec.decode(bytes), "kind $kind")

            val extension = decoded.payload as SyncOperationPayload.OptionalExtension
            assertEquals(kind, extension.kind)
            assertContentEquals(tail, extension.tail.copyBytes())
            assertContentEquals(bytes, SyncOperationCodec.encode(decoded))
        }
        assertNull(SyncOperationCodec.decode(header(50, 2) + byteArrayOf(255.toByte()) + ByteArray(limitTail + 1)))
    }

    @Test
    fun `given a break and a break end when encoded then the plaintext equals the hand-written vector and decodes back`() {
        // Failure mode: a layout drift between targets would make one replica reject another's break as invalid.
        val ref = ScheduleOccurrenceRef(scheduleId, ScheduleDate(2026, 9, 28))
        val start = 1_790_000_000_000L
        val vectors = listOf(
            SyncOperationPayload.ScheduleBreak(ref, start, 10) to
                byteArrayOf(20) + identifier(70) + u16(2026) + byteArrayOf(9, 28) + u64(start) + byteArrayOf(10),
            SyncOperationPayload.ScheduleBreakEnd(ref) to byteArrayOf(21) + identifier(70) + u16(2026) + byteArrayOf(9, 28),
        )

        vectors.forEach { (payload, payloadBytes) ->
            val operation = testOperation(60, 2, payload, physical = start)
            val expected = breakHeader(60, start) + payloadBytes

            assertContentEquals(expected, SyncOperationCodec.encode(operation), "kind ${payloadBytes[0]}")
            assertEquals(operation, SyncOperationCodec.decode(expected), "kind ${payloadBytes[0]}")
        }
        assertEquals(29, vectors.first().second.size - 1)
    }

    @Test
    fun `given each offered break length when decoded then five ten and fifteen are accepted and every other length is rejected`() {
        val start = 1_790_000_000_000L
        listOf(5, 10, 15).forEach { minutes -> assertNotNull(SyncOperationCodec.decode(breakBytes(start, start, minutes)), "$minutes") }
        listOf(0, 1, 4, 6, 9, 11, 14, 16, 20, 30, 255).forEach { minutes ->
            assertNull(SyncOperationCodec.decode(breakBytes(start, start, minutes)), "$minutes")
            val payload = SyncOperationPayload.ScheduleBreak(ScheduleOccurrenceRef(scheduleId, ScheduleDate(2026, 9, 28)), start, minutes)
            assertNull(SyncOperationCodec.encode(testOperation(61, 2, payload, physical = start)), "$minutes")
        }
    }

    @Test
    fun `given a break start after the operation's own clock when coded then the encoder and the decoder refuse it`() {
        // Failure mode: a start ahead of its HLC would let one author's clock push a window into the future.
        val clock = 1_790_000_000_000L
        assertNotNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = clock - 1)))
        assertNotNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = clock)))
        assertNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = clock + 1)))
        val ahead = SyncOperationPayload.ScheduleBreak(ScheduleOccurrenceRef(scheduleId, ScheduleDate(2026, 9, 28)), clock + 1, 5)
        assertNull(SyncOperationCodec.encode(testOperation(62, 2, ahead, physical = clock)))
    }

    @Test
    fun `given a break start outside the instant range an invalid date or trailing bytes when decoded then it is rejected`() {
        val clock = 1_790_000_000_000L
        assertNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = -1)))
        assertNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = clock, year = 2023, month = 2, day = 29)))
        assertNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = clock, year = 2101, month = 1, day = 1)))
        assertNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = clock) + byteArrayOf(0)))
        assertNull(SyncOperationCodec.decode(breakBytes(physical = clock, start = clock).copyOf(breakBytes(clock, clock).size - 1)))
        val endBytes = breakHeader(63, clock) + byteArrayOf(21) + identifier(70) + u16(2026) + byteArrayOf(9, 28)
        assertNotNull(SyncOperationCodec.decode(endBytes))
        assertNull(SyncOperationCodec.decode(endBytes + byteArrayOf(0)))
        assertNull(SyncOperationCodec.decode(breakHeader(63, clock) + byteArrayOf(21) + identifier(70) + u16(2026) + byteArrayOf(2, 30)))
    }

    @Test
    fun `given mandatory kinds outside the known set when decoded then they are still rejected`() {
        listOf(0, 22, 99, 127).forEach { kind ->
            assertNull(SyncOperationCodec.decode(header(51, 2) + byteArrayOf(kind.toByte()) + identifier(70)), "kind $kind")
        }
    }

    @Test
    fun `given a kind outside the optional range when an extension is built then it is refused`() {
        assertFailsWith<IllegalArgumentException> { SyncOperationPayload.OptionalExtension(127, ImmutableBytes(ByteArray(0))) }
        assertFailsWith<IllegalArgumentException> { SyncOperationPayload.OptionalExtension(256, ImmutableBytes(ByteArray(0))) }
    }

    private fun put(
        name: ByteArray = "Focus".encodeToByteArray(),
        weekdays: Int = 0b0011111,
        start: Int = 540,
        end: Int = 720,
        enabled: Int = 1,
    ): ByteArray {
        return header(40, 2) + byteArrayOf(8) + identifier(70) + u16(name.size) + name + byteArrayOf(weekdays.toByte()) + u16(start) +
            u16(end) + byteArrayOf(enabled.toByte())
    }

    private fun breakBytes(
        physical: Long,
        start: Long,
        minutes: Int = 5,
        year: Int = 2026,
        month: Int = 9,
        day: Int = 28,
    ): ByteArray {
        return breakHeader(64, physical) + byteArrayOf(20) + identifier(70) + u16(year) + byteArrayOf(month.toByte(), day.toByte()) +
            u64(start) + byteArrayOf(minutes.toByte())
    }

    /** The header with sequence 2 and the given HLC physical time. */
    private fun breakHeader(
        id: Int,
        physical: Long,
    ): ByteArray {
        val header = header(id, 2)
        return header.copyOf(header.size - 10) + u64(physical) + u16(0)
    }

    private fun fact(
        year: Int,
        month: Int,
        day: Int,
    ): ByteArray {
        return header(42, 2) + byteArrayOf(10) + identifier(70) + u16(year) + byteArrayOf(month.toByte(), day.toByte())
    }
}

/** "PSO1", format 1, the five identifiers, the test key, then sequence and clock as `testOperation` sets them. */
internal fun header(
    id: Int,
    sequence: Long,
    author: Int = 10,
): ByteArray {
    return "PSO1".encodeToByteArray() + u16(1) + identifier(id) + identifier(1) + identifier(2) + identifier(3) + identifier(author) +
        ByteArray(SyncFormatLimits.PUBLIC_KEY_BYTES) { 7 } + u64(sequence) + u64(sequence) + u16(0)
}

internal fun identifier(value: Int): ByteArray {
    val bytes = ByteArray(SyncFormatLimits.IDENTIFIER_BYTES)
    bytes[6] = 0x40
    bytes[8] = 0x80.toByte()
    bytes[12] = (value ushr 24).toByte()
    bytes[13] = (value ushr 16).toByte()
    bytes[14] = (value ushr 8).toByte()
    bytes[15] = value.toByte()
    return bytes
}

internal fun u16(value: Int): ByteArray {
    return byteArrayOf((value ushr 8).toByte(), value.toByte())
}

internal fun u64(value: Long): ByteArray {
    return ByteArray(8) { index -> (value ushr ((7 - index) * 8)).toByte() }
}
