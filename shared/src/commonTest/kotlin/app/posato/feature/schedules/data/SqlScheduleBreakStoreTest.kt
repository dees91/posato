package app.posato.feature.schedules.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleBreak
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val linked = ByteArray(16) { 9 }
private val day = ScheduleDate(2026, 9, 27)
private val focus = ScheduleId("000000000000400080000000000000c1")
private val evening = ScheduleId("000000000000400080000000000000c2")
private val focusPlan = SchedulePlan(focus, "Focus", 0b1111111, 540, 600, true)
private val eveningPlan = SchedulePlan(evening, "Evening", 0b1111111, 1200, 1320, true)

/**
 * Break facts survive a relaunch before the first sync, are published at first link, and merge with the shared
 * facts by earliest start. Failure modes: a lost local break lets a second break happen after a restart; a
 * shared later start overwriting the local press releases outside the window the person took.
 */
class SqlScheduleBreakStoreTest {
    private suspend fun withStores(
        name: String,
        block: suspend (SqlScheduleStore, () -> SqlScheduleStore) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            block(SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default)) { SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default) }
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private suspend fun SqlScheduleStore.snapshot(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(read()).value
    }

    private suspend fun SqlScheduleStore.intents(): List<ScheduleIntent> {
        return assertIs<ScheduleResult.Success<List<SequencedScheduleIntent>>>(readIntents()).value.map { it.intent }
    }

    @Test
    fun `given running occurrences when a break is taken while linked then each gets a fact and an intent that survive a reopen`() = runTest {
        withStores("breaks-take.db") { store, reopen ->
            store.save(focusPlan, null)
            store.save(eveningPlan, null)
            val keys = setOf(OccurrenceKey(focus, day), OccurrenceKey(evening, day))
            val taken = ScheduleBreak(1_000_000, 10)

            assertIs<ScheduleResult.Success<Unit>>(store.recordFact(keys, OccurrenceFact.Break(taken), day, linked))

            val facts = reopen().snapshot().facts
            assertEquals(keys.associateWith { taken }, facts.breaks)
            assertEquals(keys.map { ScheduleIntent.Break(it, day, taken) }.toSet(), reopen().intents().toSet())
        }
    }

    @Test
    fun `given a break when ended early while linked then the end is a fact with one intent and the break stays recorded`() = runTest {
        withStores("breaks-end.db") { store, reopen ->
            store.save(focusPlan, null)
            val key = OccurrenceKey(focus, day)
            store.recordFact(setOf(key), OccurrenceFact.Break(ScheduleBreak(1_000_000, 5)), day, null)

            assertIs<ScheduleResult.Success<Unit>>(store.recordFact(setOf(key), OccurrenceFact.BreakEnd, day, linked))

            val facts = reopen().snapshot().facts
            assertEquals(setOf(key), facts.breakEnds)
            assertEquals(mapOf(key to ScheduleBreak(1_000_000, 5)), facts.breaks)
            assertEquals(listOf<ScheduleIntent>(ScheduleIntent.BreakEnd(key, day)), store.intents())
        }
    }

    @Test
    fun `given local breaks from before linking when a workspace is seeded then those from yesterday on are published once`() = runTest {
        withStores("breaks-seed.db") { store, _ ->
            store.save(focusPlan, null)
            val old = OccurrenceKey(focus, day.plusDays(-2))
            val yesterday = OccurrenceKey(focus, day.plusDays(-1))
            val today = OccurrenceKey(focus, day)
            val shared = OccurrenceKey(evening, day)
            store.recordFact(setOf(old), OccurrenceFact.Break(ScheduleBreak(100_000, 5)), day, null)
            store.recordFact(setOf(yesterday), OccurrenceFact.Break(ScheduleBreak(200_000, 10)), day, null)
            store.recordFact(setOf(today), OccurrenceFact.Break(ScheduleBreak(300_000, 15)), day, null)
            store.recordFact(setOf(today), OccurrenceFact.BreakEnd, day, null)
            val synced = SyncedSchedules(live = listOf(focusPlan), breaks = mapOf(shared to ScheduleBreak(1, 5)))

            assertEquals(ScheduleResult.Success(true), store.seedOnce(linked, synced, day))

            assertEquals(
                setOf(
                    ScheduleIntent.Break(yesterday, day, ScheduleBreak(200_000, 10)),
                    ScheduleIntent.Break(today, day, ScheduleBreak(300_000, 15)),
                    ScheduleIntent.BreakEnd(today, day),
                ),
                store.intents().toSet(),
            )
        }
    }

    @Test
    fun `given shared breaks when materialized then the earliest start wins over the local press and ends are kept for live plans`() = runTest {
        withStores("breaks-materialize.db") { store, _ ->
            store.save(focusPlan, null)
            store.save(eveningPlan, null)
            val earlierShared = OccurrenceKey(focus, day)
            val laterShared = OccurrenceKey(evening, day)
            val orphan = OccurrenceKey(ScheduleId("000000000000400080000000000000c3"), day)
            store.recordFact(setOf(earlierShared, laterShared), OccurrenceFact.Break(ScheduleBreak(500_000, 10)), day, null)

            store.materialize(
                linked,
                SyncedSchedules(
                    live = listOf(focusPlan, eveningPlan),
                    breaks = mapOf(
                        earlierShared to ScheduleBreak(400_000, 10),
                        laterShared to ScheduleBreak(600_000, 5),
                        orphan to ScheduleBreak(1, 5),
                    ),
                    breakEnds = setOf(laterShared, orphan),
                ),
            )

            val facts = store.snapshot().facts
            assertEquals(
                mapOf(earlierShared to ScheduleBreak(400_000, 10), laterShared to ScheduleBreak(500_000, 10)),
                facts.breaks,
            )
            assertEquals(setOf(laterShared), facts.breakEnds)
        }
    }

    @Test
    fun `given breaks the host saw end when recorded then they persist and older dates are dropped`() = runTest {
        withStores("breaks-over.db") { store, reopen ->
            val old = OccurrenceKey(focus, day.plusDays(-2))
            val recent = OccurrenceKey(focus, day)
            store.recordHost(ScheduleHostUpdate(breaksOver = setOf(old, recent)))
            assertEquals(setOf(old, recent), reopen().snapshot().facts.breaksOver)

            store.recordHost(ScheduleHostUpdate(breaksOverBefore = day.plusDays(-1)))

            assertEquals(setOf(recent), reopen().snapshot().facts.breaksOver)
        }
    }

    @Test
    fun `given a schedule with break facts when removed then its breaks go with it`() = runTest {
        withStores("breaks-remove.db") { store, _ ->
            store.save(focusPlan, null)
            val key = OccurrenceKey(focus, day)
            store.recordFact(setOf(key), OccurrenceFact.Break(ScheduleBreak(500_000, 5)), day, null)
            store.recordFact(setOf(key), OccurrenceFact.BreakEnd, day, null)
            store.recordHost(ScheduleHostUpdate(breaksOver = setOf(key)))

            store.remove(focus, null)

            val facts = store.snapshot().facts
            assertTrue(facts.breaks.isEmpty() && facts.breakEnds.isEmpty() && facts.breaksOver.isEmpty())
        }
    }

    @Test
    fun `given a received break and one taken here when the workspace is removed then only the received one ends here`() = runTest {
        // Failure mode: a break received from a workspace this device left would keep releasing it.
        val testDatabase = createLocalPolicyTestDatabase("breaks-unlink.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlScheduleStore(database, Dispatchers.Default)
            store.save(focusPlan, null)
            store.save(eveningPlan, null)
            val here = OccurrenceKey(focus, day)
            val received = OccurrenceKey(evening, day)
            store.recordFact(setOf(here), OccurrenceFact.Break(ScheduleBreak(500_000, 10)), day, null)
            store.materialize(
                linked,
                SyncedSchedules(
                    live = listOf(focusPlan, eveningPlan),
                    breaks = mapOf(here to ScheduleBreak(400_000, 10), received to ScheduleBreak(450_000, 5)),
                ),
            )

            database.endReceivedBreaksAfterUnlink()

            assertEquals(setOf(received), store.snapshot().facts.breaksOver)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }
}
