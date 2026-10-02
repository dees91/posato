package app.posato.feature.sync.domain

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.canonicalDigest
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.domain.ExactDomain
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ADR 0006 pause set amendment's reduction; every expectation is derived from its text, not from the reducer. */
class SyncReducerPauseSetTest {
    private val register = testOperation(1, 1, SyncOperationPayload.AuthorRegister)
    private val first = PauseSetId.FIRST

    private fun set(value: Int): PauseSetId {
        return checkNotNull(PauseSetId.of(testIdentifier(value)))
    }

    private fun domain(name: String): ExactDomain {
        return checkNotNull(ExactDomain.restore("$name.example"))
    }

    private fun ops(vararg payloads: SyncOperationPayload): List<SyncOperation> {
        return listOf(register) + payloads.mapIndexed { index, payload -> testOperation(index + 2, index.toLong() + 2, payload) }
    }

    private fun SyncProjection.domainsOf(setId: PauseSetId): List<String> {
        return pauseSetDomains(setId).map(ExactDomain::canonicalValue)
    }

    private fun SyncProjection.outcomeOf(operation: SyncOperation): SyncAuditOutcome {
        return audit.single { entry -> entry.operationId == operation.operationId }.outcome
    }

    @Test
    fun `given no pause set operations when reduced then the first set is live and unnamed and the default and holds legacy domains`() {
        val projection = SyncReducer.reduce(ops(SyncOperationPayload.DomainPresent(domain("a"))))

        assertEquals(listOf(first), projection.pauseSets.map(SynchronizedPauseSet::setId))
        assertNull(projection.pauseSets.single().name)
        assertEquals(first, projection.defaultPauseSetId)
        assertEquals(listOf("a.example"), projection.domainsOf(first))
        assertEquals(listOf(domain("a")), projection.domains)
        assertFalse(projection.pauseSetsEnabled)
    }

    @Test
    fun `given a domain ordered before its set's put when reduced then liveness is decided first and the domain counts`() {
        val operations = ops(
            SyncOperationPayload.DomainPresent(domain("a"), set(80)),
            SyncOperationPayload.PauseSetPut(set(80), "Work"),
        )

        listOf(operations, operations.reversed()).forEach { order ->
            val projection = SyncReducer.reduce(order)

            assertEquals(listOf("a.example"), projection.domainsOf(set(80)))
            assertEquals("Work", projection.pauseSets.single { it.setId == set(80) }.name)
            assertEquals(PauseSetStatus.LIVE, projection.pauseSetStatus(set(80)))
        }
    }

    @Test
    fun `given the greatest put names a set when reduced then later renames win and the first set can be named`() {
        val projection = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(set(80), "Work"),
                SyncOperationPayload.PauseSetPut(set(80), "Office"),
                SyncOperationPayload.PauseSetPut(first, "Mine"),
            ),
        )

        assertEquals("Office", projection.pauseSets.single { it.setId == set(80) }.name)
        assertEquals("Mine", projection.pauseSets.single { it.setId == first }.name)
    }

    @Test
    fun `given a set remove before or after its put when reduced then the set is gone and its domains leave the projection`() {
        val put = SyncOperationPayload.PauseSetPut(set(80), "Work")
        val present = SyncOperationPayload.DomainPresent(domain("a"), set(80))
        val remove = SyncOperationPayload.PauseSetRemove(set(80))

        listOf(ops(put, present, remove), ops(remove, put, present), ops(present, remove, put)).forEach { operations ->
            val projection = SyncReducer.reduce(operations)

            assertEquals(PauseSetStatus.REMOVED, projection.pauseSetStatus(set(80)))
            assertEquals(listOf(first), projection.pauseSets.map(SynchronizedPauseSet::setId))
            assertEquals(emptyList(), projection.domainsOf(set(80)))
            assertEquals(SyncAuditOutcome.NO_OP, projection.outcomeOf(operations.single { it.payload == present }))
        }
    }

    @Test
    fun `given a domain removed from one set when reduced then the same domain in another set stays`() {
        val projection = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(set(80), "Work"),
                SyncOperationPayload.DomainPresent(domain("a")),
                SyncOperationPayload.DomainPresent(domain("a"), set(80)),
                SyncOperationPayload.DomainAbsent(domain("a"), set(80)),
            ),
        )

        assertEquals(listOf("a.example"), projection.domainsOf(first))
        assertEquals(emptyList(), projection.domainsOf(set(80)))
    }

    @Test
    fun `given the domain cap when a domain already held by another live set is added then it counts once and applies`() {
        val fill = (0 until SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS).map { index ->
            SyncOperationPayload.DomainPresent(domain("d$index"))
        }
        val shared = SyncOperationPayload.DomainPresent(domain("d0"), set(80))
        val fresh = SyncOperationPayload.DomainPresent(domain("fresh"), set(80))
        val operations = ops(SyncOperationPayload.PauseSetPut(set(80), "Work"), *fill.toTypedArray(), shared, fresh)

        val projection = SyncReducer.reduce(operations)

        assertEquals(SyncAuditOutcome.APPLIED, projection.outcomeOf(operations[operations.size - 2]))
        assertEquals(SyncAuditOutcome.DOMAIN_CAPACITY, projection.outcomeOf(operations.last()))
        assertEquals(listOf("d0.example"), projection.domainsOf(set(80)))
    }

    @Test
    fun `given the domain cap when a domain leaves its only set then a new domain fits but not when another set still holds it`() {
        val fill = (0 until SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS).map { index ->
            SyncOperationPayload.DomainPresent(domain("d$index"), if (index % 2 == 0) first else set(80))
        }
        val base = listOf(
            SyncOperationPayload.PauseSetPut(set(80), "Work"),
            *fill.toTypedArray(),
            SyncOperationPayload.DomainPresent(domain("d0"), set(80)),
        )
        val freed = ops(
            *base.toTypedArray(),
            SyncOperationPayload.DomainAbsent(domain("d1"), set(80)),
            SyncOperationPayload.DomainPresent(domain("new")),
        )
        val stillHeld = ops(
            *base.toTypedArray(),
            SyncOperationPayload.DomainAbsent(domain("d0"), set(80)),
            SyncOperationPayload.DomainPresent(domain("new")),
        )

        assertEquals(SyncAuditOutcome.APPLIED, SyncReducer.reduce(freed).outcomeOf(freed.last()))
        assertEquals(SyncAuditOutcome.DOMAIN_CAPACITY, SyncReducer.reduce(stillHeld).outcomeOf(stillHeld.last()))
    }

    @Test
    fun `given a domain refused at the cap when an earlier or later set remove frees space then the domain becomes present`() {
        val fill = (0 until SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS).map { index ->
            SyncOperationPayload.DomainPresent(domain("d$index"), set(81))
        }
        val refused = SyncOperationPayload.DomainPresent(domain("late"), set(80))
        val base = listOf(
            SyncOperationPayload.PauseSetPut(set(80), "Work"),
            SyncOperationPayload.PauseSetPut(set(81), "Full"),
        ) + fill + refused

        val atCap = SyncReducer.reduce(ops(*base.toTypedArray()))
        val freedLater = SyncReducer.reduce(ops(*(base + SyncOperationPayload.PauseSetRemove(set(81))).toTypedArray()))
        val freedEarlier = SyncReducer.reduce(ops(SyncOperationPayload.PauseSetRemove(set(81)), *base.toTypedArray()))

        assertEquals(emptyList(), atCap.domainsOf(set(80)))
        assertEquals(listOf("late.example"), freedLater.domainsOf(set(80)))
        assertEquals(listOf("late.example"), freedEarlier.domainsOf(set(80)))
    }

    @Test
    fun `given ten live sets when an eleventh new set is put then it is refused without eviction in every order`() {
        val puts = (1..10).map { index -> SyncOperationPayload.PauseSetPut(set(100 + index), "Set $index") }
        val operations = ops(*puts.toTypedArray())

        listOf(operations, operations.reversed(), operations.shuffled(Random(5))).forEach { order ->
            val projection = SyncReducer.reduce(order)

            assertEquals(listOf(first) + (101..109).map(::set), projection.pauseSets.map(SynchronizedPauseSet::setId))
            assertEquals(PauseSetStatus.REFUSED, projection.pauseSetStatus(set(110)))
            assertEquals(SyncAuditOutcome.SET_CAPACITY, projection.outcomeOf(operations.last()))
        }
    }

    @Test
    fun `given a refused set when another set is removed then the refused set becomes live with its domains`() {
        val puts = (1..10).map { index -> SyncOperationPayload.PauseSetPut(set(100 + index), "Set $index") }
        val domain = SyncOperationPayload.DomainPresent(domain("a"), set(110))

        val refused = SyncReducer.reduce(ops(*puts.toTypedArray(), domain))
        val freed = SyncReducer.reduce(ops(*puts.toTypedArray(), domain, SyncOperationPayload.PauseSetRemove(set(101))))

        assertEquals(emptyList(), refused.domainsOf(set(110)))
        assertEquals(PauseSetStatus.LIVE, freed.pauseSetStatus(set(110)))
        assertEquals(listOf("a.example"), freed.domainsOf(set(110)))
    }

    @Test
    fun `given the first set is removed when reduced then its slot frees and kinds 2 3 6 and 8 resolve to nothing`() {
        val puts = (1..10).map { index -> SyncOperationPayload.PauseSetPut(set(100 + index), "Set $index") }
        val sessionId = SessionId(testIdentifier(90))
        val legacySchedule = SyncOperationPayload.SchedulePut(ScheduleSyncId(testIdentifier(70)), "Focus", 1, 540, 720, true)
        val operations = ops(
            SyncOperationPayload.DomainPresent(domain("a")),
            *puts.toTypedArray(),
            SyncOperationPayload.PauseSetRemove(first),
            SyncOperationPayload.DomainPresent(domain("b")),
            SyncOperationPayload.DomainAbsent(domain("a")),
            SyncOperationPayload.SessionStart(sessionId, 1_000, 2_000),
            legacySchedule,
        )

        val projection = SyncReducer.reduce(operations)

        assertEquals((101..110).map(::set), projection.pauseSets.map(SynchronizedPauseSet::setId))
        assertEquals(PauseSetStatus.REMOVED, projection.pauseSetStatus(first))
        assertEquals(SyncAuditOutcome.NO_OP, projection.outcomeOf(operations[13]))
        assertEquals(SyncAuditOutcome.NO_OP, projection.outcomeOf(operations[14]))
        assertEquals(emptyList(), projection.domains)
        assertEquals(emptyList(), projection.domainsOf(first))
        assertEquals(first, projection.schedules.single().setId)
        assertEquals(first, projection.eligibleSessionStarts.single().setId)
    }

    @Test
    fun `given kinds 8 and 17 for one schedule when reduced then they share one register and a later kind 8 means the first set`() {
        val scheduleId = ScheduleSyncId(testIdentifier(70))
        val legacy = SyncOperationPayload.SchedulePut(scheduleId, "Focus", 1, 540, 720, true)
        val named = legacy.copy(name = "Work hours", setId = set(80))

        val laterLegacy = SyncReducer.reduce(ops(SyncOperationPayload.PauseSetPut(set(80), "Work"), named, legacy))
        val laterNamed = SyncReducer.reduce(ops(SyncOperationPayload.PauseSetPut(set(80), "Work"), legacy, named))

        assertEquals(first, laterLegacy.schedules.single().setId)
        assertEquals("Focus", laterLegacy.schedules.single().name)
        assertEquals(set(80), laterNamed.schedules.single().setId)
    }

    @Test
    fun `given a kind 6 and a kind 18 start of one session when reduced then they conflict in every order`() {
        val sessionId = SessionId(testIdentifier(90))
        val operations = ops(
            SyncOperationPayload.SessionStart(sessionId, 1_000, 2_000),
            SyncOperationPayload.SessionStart(sessionId, 1_000, 2_000, set(80)),
        )

        listOf(operations, operations.reversed()).forEach { order ->
            val projection = SyncReducer.reduce(order)

            assertEquals(setOf(sessionId), projection.conflictedSessionIds)
            assertEquals(emptyList(), projection.eligibleSessionStarts)
        }
    }

    @Test
    fun `given references to removed and refused and unknown sets when reduced then each status is exposed and resolves to nothing`() {
        val puts = (1..10).map { index -> SyncOperationPayload.PauseSetPut(set(100 + index), "Set $index") }
        val projection = SyncReducer.reduce(
            ops(
                *puts.toTypedArray(),
                SyncOperationPayload.DomainPresent(domain("a"), set(110)),
                SyncOperationPayload.PauseSetPut(set(80), "Gone"),
                SyncOperationPayload.DomainPresent(domain("b"), set(80)),
                SyncOperationPayload.PauseSetRemove(set(80)),
                SyncOperationPayload.DomainPresent(domain("c"), set(99)),
                SyncOperationPayload.SessionStart(SessionId(testIdentifier(90)), 1_000, 2_000, set(99)),
            ),
        )

        assertEquals(PauseSetStatus.REFUSED, projection.pauseSetStatus(set(110)))
        assertEquals(PauseSetStatus.REMOVED, projection.pauseSetStatus(set(80)))
        assertEquals(PauseSetStatus.UNKNOWN, projection.pauseSetStatus(set(99)))
        listOf(110, 80, 99).forEach { value -> assertEquals(emptyList(), projection.domainsOf(set(value)), "set $value") }
        assertEquals(set(99), projection.eligibleSessionStarts.single().setId)
    }

    @Test
    fun `given set defaults when reduced then the greatest choice wins and a dead choice falls back to the first set`() {
        val chosen = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(set(80), "Work"),
                SyncOperationPayload.PauseSetPut(set(81), "Play"),
                SyncOperationPayload.PauseSetDefault(set(81)),
                SyncOperationPayload.PauseSetDefault(set(80)),
            ),
        )
        val deadChoice = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(set(80), "Work"),
                SyncOperationPayload.PauseSetPut(set(81), "Play"),
                SyncOperationPayload.PauseSetDefault(set(81)),
                SyncOperationPayload.PauseSetDefault(set(80)),
                SyncOperationPayload.PauseSetRemove(set(80)),
            ),
        )

        assertEquals(set(80), chosen.defaultPauseSetId)
        assertEquals(first, deadChoice.defaultPauseSetId)
    }

    @Test
    fun `given the first set removed with no live default when reduced then the set with the earliest put is the default`() {
        val projection = SyncReducer.reduce(
            ops(
                SyncOperationPayload.PauseSetPut(set(81), "Earlier"),
                SyncOperationPayload.PauseSetPut(set(80), "Later"),
                SyncOperationPayload.PauseSetPut(set(81), "Renamed"),
                SyncOperationPayload.PauseSetDefault(set(82)),
                SyncOperationPayload.PauseSetRemove(first),
            ),
        )
        val none = SyncReducer.reduce(ops(SyncOperationPayload.PauseSetRemove(first)))

        assertEquals(set(81), projection.defaultPauseSetId)
        assertNull(none.defaultPauseSetId)
        assertEquals(emptyList(), none.pauseSets)
    }

    @Test
    fun `given duplicate pause sets enabled operations when reduced then the flag is set and the duplicate changes nothing`() {
        val operations = ops(SyncOperationPayload.PauseSetsEnabled, SyncOperationPayload.PauseSetsEnabled)

        val projection = SyncReducer.reduce(operations)

        assertTrue(projection.pauseSetsEnabled)
        assertEquals(SyncAuditOutcome.APPLIED, projection.outcomeOf(operations[1]))
        assertEquals(SyncAuditOutcome.NO_OP, projection.outcomeOf(operations[2]))
    }

    @Test
    fun `given mixed pause set operations from two authors when reduced in random orders then state and digest converge`() {
        val sessionId = SessionId(testIdentifier(90))
        val operations = buildList {
            add(register)
            add(testOperation(2, 1, SyncOperationPayload.AuthorRegister, author = 11))
            (1..10).forEach { index ->
                add(testOperation(10 + index, index.toLong() + 1, SyncOperationPayload.PauseSetPut(set(100 + index), "S$index")))
            }
            add(testOperation(30, 12, SyncOperationPayload.DomainPresent(domain("a"), set(110))))
            add(testOperation(31, 13, SyncOperationPayload.DomainPresent(domain("a"))))
            add(testOperation(32, 2, SyncOperationPayload.PauseSetRemove(set(103)), author = 11))
            add(testOperation(33, 3, SyncOperationPayload.PauseSetDefault(set(103)), author = 11))
            add(testOperation(34, 4, SyncOperationPayload.DomainAbsent(domain("a"), set(110)), author = 11))
            add(testOperation(35, 5, SyncOperationPayload.SessionStart(sessionId, 1_000, 2_000, set(104)), author = 11))
            add(testOperation(36, 6, SyncOperationPayload.PauseSetsEnabled, author = 11))
            add(testOperation(37, 14, SyncOperationPayload.PauseSetsEnabled))
        }
        val provider = FakeSyncCryptoProvider()
        val expected = SyncReducer.reduce(operations)
        assertEquals(PauseSetStatus.LIVE, expected.pauseSetStatus(set(110)))
        assertEquals(first, expected.defaultPauseSetId)

        repeat(40) { seed ->
            val shuffled = SyncReducer.reduce(operations.shuffled(Random(seed)))

            assertEquals(expected, shuffled, "seed $seed")
            assertEquals(expected.canonicalDigest(provider), shuffled.canonicalDigest(provider), "seed $seed")
        }
    }
}
