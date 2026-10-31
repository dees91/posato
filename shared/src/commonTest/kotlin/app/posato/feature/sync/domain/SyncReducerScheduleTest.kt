package app.posato.feature.sync.domain

import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.ImmutableBytes
import app.posato.feature.sync.data.canonicalDigest
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ExactDomain
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncReducerScheduleTest {
    private val register = testOperation(1, 1, SyncOperationPayload.AuthorRegister)

    private fun id(value: Int): ScheduleSyncId {
        return ScheduleSyncId(testIdentifier(value))
    }

    private fun put(
        id: Int,
        name: String = "Plan $id",
    ): SyncOperationPayload.SchedulePut {
        return SyncOperationPayload.SchedulePut(id(id), name, 0b1111111, 540, 600, true)
    }

    private fun ref(
        id: Int,
        day: Int = 28,
    ): ScheduleOccurrenceRef {
        return ScheduleOccurrenceRef(id(id), ScheduleDate(2026, 9, day))
    }

    @Test
    fun `given two puts for one schedule when reduced in either order then the greater total order wins`() {
        val older = testOperation(2, 2, put(50, "Morning"))
        val newer = testOperation(3, 3, put(50, "Evening"))

        listOf(listOf(register, older, newer), listOf(newer, register, older)).forEach { operations ->
            val projection = SyncReducer.reduce(operations)

            assertEquals(listOf("Evening"), projection.schedules.map { it.name })
        }
    }

    @Test
    fun `given a remove before or after its put in total order when reduced then the schedule is gone for good`() {
        val remove = SyncOperationPayload.ScheduleRemove(id(51))
        listOf(
            listOf(register, testOperation(2, 2, put(51)), testOperation(3, 3, remove)),
            listOf(register, testOperation(2, 2, remove), testOperation(3, 3, put(51))),
        ).forEach { operations ->
            val projection = SyncReducer.reduce(operations)

            assertEquals(emptyList(), projection.schedules)
            assertTrue(id(51) in projection.removedScheduleIds)
        }
    }

    @Test
    fun `given a remove behind a sequence gap when reduced then the schedule stays live`() {
        val operations = listOf(register, testOperation(2, 2, put(52)), testOperation(4, 4, SyncOperationPayload.ScheduleRemove(id(52))))

        val projection = SyncReducer.reduce(operations)

        assertEquals(listOf(id(52)), projection.schedules.map { it.scheduleId })
        assertEquals(emptySet(), projection.removedScheduleIds)
    }

    @Test
    fun `given eleven new schedules when reduced then the eleventh is refused without eviction in every order`() {
        val puts = (1..11).map { index -> testOperation(index + 1, index.toLong() + 1, put(100 + index)) }

        listOf(listOf(register) + puts, (listOf(register) + puts).reversed()).forEach { operations ->
            val projection = SyncReducer.reduce(operations)

            assertEquals((101..110).map(::id), projection.schedules.map { it.scheduleId })
            val refused = projection.audit.single { it.outcome == SyncAuditOutcome.SCHEDULE_CAPACITY }
            assertEquals(puts.last().operationId, refused.operationId)
        }
    }

    @Test
    fun `given a schedule refused by capacity when reduced then its latest put is kept as refused until a slot frees`() {
        val puts = (1..11).map { index -> testOperation(index + 1, index.toLong() + 1, put(100 + index)) }
        val renamed = testOperation(13, 13, put(111, "Renamed"))

        val refused = SyncReducer.reduce(listOf(register) + puts + renamed)
        val freed = SyncReducer.reduce(listOf(register) + puts + renamed + testOperation(14, 14, SyncOperationPayload.ScheduleRemove(id(101))))

        assertEquals(listOf("Renamed"), refused.refusedSchedules.map { it.name })
        assertEquals(emptyList(), freed.refusedSchedules)
        assertTrue(id(111) in freed.schedules.map { it.scheduleId })
    }

    @Test
    fun `given a later remove of one of ten schedules when reduced then the eleventh fits in natural and shuffled order`() {
        val puts = (1..11).map { index -> testOperation(index + 1, index.toLong() + 1, put(100 + index)) }
        val remove = testOperation(13, 13, SyncOperationPayload.ScheduleRemove(id(101)))

        listOf(listOf(register) + puts + remove, (listOf(register) + puts + remove).shuffled(Random(3))).forEach { operations ->
            val projection = SyncReducer.reduce(operations)

            assertEquals((102..111).map(::id), projection.schedules.map { it.scheduleId })
            assertEquals(0, projection.audit.count { it.outcome == SyncAuditOutcome.SCHEDULE_CAPACITY })
        }
    }

    @Test
    fun `given skips and ends when reduced then they grow only and are ignored for removed schedules`() {
        val operations = listOf(
            register,
            testOperation(2, 2, SyncOperationPayload.ScheduleSkip(ref(60))),
            testOperation(3, 3, put(60)),
            testOperation(4, 4, SyncOperationPayload.ScheduleSkip(ref(60))),
            testOperation(5, 5, SyncOperationPayload.ScheduleOccurrenceEnd(ref(60, 29))),
            testOperation(6, 6, SyncOperationPayload.ScheduleSkip(ref(61))),
            testOperation(7, 7, SyncOperationPayload.ScheduleRemove(id(61))),
        )

        val projection = SyncReducer.reduce(operations)

        assertEquals(setOf(ref(60)), projection.scheduleSkips)
        assertEquals(setOf(ref(60, 29)), projection.scheduleEnds)
        assertEquals(SyncAuditOutcome.NO_OP, projection.audit.single { it.operationId == operations[3].operationId }.outcome)
    }

    @Test
    fun `given a skip for a schedule refused by capacity when reduced then the fact still counts`() {
        val puts = (1..11).map { index -> testOperation(index + 1, index.toLong() + 1, put(100 + index)) }
        val skip = testOperation(13, 13, SyncOperationPayload.ScheduleSkip(ref(111)))

        val projection = SyncReducer.reduce(listOf(register) + puts + skip)

        assertEquals(setOf(ref(111)), projection.scheduleSkips)
    }

    @Test
    fun `given an optional operation between two domain changes of one author then it advances the sequence and changes nothing`() {
        val first = checkNotNull(ExactDomain.restore("first.example"))
        val second = checkNotNull(ExactDomain.restore("second.example"))
        val optional = testOperation(3, 3, SyncOperationPayload.OptionalExtension(200, ImmutableBytes(byteArrayOf(1, 2))))
        val operations = listOf(
            register,
            testOperation(2, 2, SyncOperationPayload.DomainPresent(first)),
            optional,
            testOperation(4, 4, SyncOperationPayload.DomainPresent(second)),
        )

        val projection = SyncReducer.reduce(operations)

        assertEquals(listOf(first, second), projection.pauseSetDomains(PauseSetId.FIRST))
        assertEquals(SyncAuditOutcome.NO_OP, projection.audit.single { it.operationId == optional.operationId }.outcome)
    }

    @Test
    fun `given mixed schedule operations from two authors when reduced in random orders then state and audit converge`() {
        val operations = buildList {
            add(register)
            add(testOperation(2, 1, SyncOperationPayload.AuthorRegister, author = 11))
            (1..11).forEach { index -> add(testOperation(10 + index, index.toLong() + 1, put(100 + index))) }
            add(testOperation(30, 13, SyncOperationPayload.ScheduleRemove(id(103))))
            add(testOperation(31, 2, put(105, "Renamed"), author = 11))
            add(testOperation(32, 3, SyncOperationPayload.ScheduleSkip(ref(104)), author = 11))
            add(testOperation(33, 4, SyncOperationPayload.ScheduleOccurrenceEnd(ref(106)), author = 11))
            add(testOperation(34, 5, SyncOperationPayload.OptionalExtension(128, ImmutableBytes(ByteArray(0))), author = 11))
        }
        val provider = FakeSyncCryptoProvider()
        val expected = SyncReducer.reduce(operations)
        assertEquals(SyncAuditOutcome.APPLIED, expected.audit.single { it.operationId == operations[13].operationId }.outcome)
        assertTrue(id(103) in expected.removedScheduleIds)
        assertEquals(0, expected.audit.count { it.outcome == SyncAuditOutcome.SCHEDULE_CAPACITY })

        repeat(40) { seed ->
            val shuffled = SyncReducer.reduce(operations.shuffled(Random(seed)))

            assertEquals(expected, shuffled, "seed $seed")
            assertEquals(expected.canonicalDigest(provider), shuffled.canonicalDigest(provider), "seed $seed")
        }
    }

    // Failure mode for the break tests: replicas that see the same operations in another order must agree on
    // which window an occurrence has, or one device releases while another restricts for the same break.
    private fun takeBreak(
        operationId: Int,
        sequence: Long,
        ref: ScheduleOccurrenceRef,
        start: Long,
        minutes: Int = 5,
        author: Int = 10,
    ): SyncOperation {
        return testOperation(operationId, sequence, SyncOperationPayload.ScheduleBreak(ref, start, minutes), author, physical = start + 1)
    }

    @Test
    fun `given concurrent breaks for one occurrence when reduced in any order then the earliest start wins everywhere`() {
        val second = testOperation(2, 1, SyncOperationPayload.AuthorRegister, author = 11)
        val operations = listOf(
            register,
            second,
            testOperation(3, 2, put(60)),
            takeBreak(4, 3, ref(60), start = 1_000_000, minutes = 10),
            takeBreak(5, 2, ref(60), start = 900_000, minutes = 15, author = 11),
        )
        val expected = SyncReducer.reduce(operations)

        assertEquals(mapOf(ref(60) to SynchronizedBreak(900_000, 15)), expected.scheduleBreaks)
        repeat(20) { seed -> assertEquals(expected, SyncReducer.reduce(operations.shuffled(Random(seed))), "seed $seed") }
    }

    @Test
    fun `given two breaks with the same start when reduced then the smaller total order wins in every order`() {
        val second = testOperation(2, 1, SyncOperationPayload.AuthorRegister, author = 11)
        val first = takeBreak(4, 2, ref(61), start = 500_000, minutes = 5)
        val later = takeBreak(5, 2, ref(61), start = 500_000, minutes = 15, author = 11)
        val operations = listOf(register, second, first, later)

        listOf(operations, operations.reversed(), operations.shuffled(Random(7))).forEach { ordered ->
            assertEquals(mapOf(ref(61) to SynchronizedBreak(500_000, 5)), SyncReducer.reduce(ordered).scheduleBreaks)
        }
    }

    @Test
    fun `given a break end before or after its break when reduced then the break is ended for good`() {
        val end = testOperation(6, 3, SyncOperationPayload.ScheduleBreakEnd(ref(62)))
        val onlyEnd = SyncReducer.reduce(listOf(register, end.copy(authorSequence = 2)))
        assertEquals(setOf(ref(62)), onlyEnd.scheduleBreakEnds)
        assertEquals(emptyMap(), onlyEnd.scheduleBreaks)

        listOf(
            listOf(register, takeBreak(5, 2, ref(62), start = 400_000), end),
            listOf(end, takeBreak(5, 2, ref(62), start = 400_000), register),
        ).forEach { operations ->
            val projection = SyncReducer.reduce(operations)

            assertEquals(setOf(ref(62)), projection.scheduleBreakEnds)
            assertEquals(mapOf(ref(62) to SynchronizedBreak(400_000, 5)), projection.scheduleBreaks)
        }
    }

    @Test
    fun `given breaks for a removed schedule when reduced then they are ignored whatever the order`() {
        val remove = testOperation(7, 4, SyncOperationPayload.ScheduleRemove(id(63)))
        listOf(
            listOf(register, takeBreak(5, 2, ref(63), start = 300_000), testOperation(6, 3, SyncOperationPayload.ScheduleBreakEnd(ref(63))), remove),
            listOf(remove, register, testOperation(6, 3, SyncOperationPayload.ScheduleBreakEnd(ref(63))), takeBreak(5, 2, ref(63), start = 300_000)),
        ).forEach { operations ->
            val projection = SyncReducer.reduce(operations)

            assertEquals(emptyMap(), projection.scheduleBreaks)
            assertEquals(emptySet(), projection.scheduleBreakEnds)
        }
    }

    @Test
    fun `given breaks on two dates of one schedule when reduced then each date keeps its own break`() {
        val projection = SyncReducer.reduce(
            listOf(register, takeBreak(5, 2, ref(64, day = 28), start = 300_000), takeBreak(6, 3, ref(64, day = 29), start = 700_000, minutes = 10)),
        )

        assertEquals(
            mapOf(ref(64, day = 28) to SynchronizedBreak(300_000, 5), ref(64, day = 29) to SynchronizedBreak(700_000, 10)),
            projection.scheduleBreaks,
        )
    }
}
