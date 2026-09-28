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

        assertEquals(listOf(first, second), projection.domains)
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
}
