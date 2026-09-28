package app.posato.feature.schedules.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.OccurrencePin
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

private val workspace = ByteArray(16) { 9 }
private val otherWorkspace = ByteArray(16) { 8 }
private val today = ScheduleDate(2026, 9, 27)

/** UUIDv4 identifiers written out, so the store's wire check accepts them. */
private fun id(value: Int): ScheduleId {
    return ScheduleId("00000000000040008000${value.toString(16).padStart(12, '0')}")
}

private fun plan(
    value: Int,
    name: String = "Plan $value",
    enabled: Boolean = true,
): SchedulePlan {
    return SchedulePlan(id(value), name, 0b0011111, 540, 600, enabled)
}

private suspend fun withStore(
    name: String,
    block: suspend (SqlScheduleStore) -> Unit,
) {
    val testDatabase = createLocalPolicyTestDatabase(name)
    val driver = testDatabase.openDriver()
    try {
        block(SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default))
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

class SqlScheduleStoreTest {
    @Test
    fun `given a save when unlinked then no intent is recorded and when linked exactly one is`() = runTest {
        withStore("schedules-save.db") { store ->
            store.save(plan(1), workspaceId = null)
            assertEquals(emptyList(), store.intents())

            store.save(plan(2), workspace)

            assertEquals(listOf(ScheduleIntent.Put(plan(2))), store.intents())
            assertEquals(listOf(plan(1), plan(2)), store.snapshot().schedules.map { it.plan })
        }
    }

    @Test
    fun `given a padded decomposed name when saved then the store keeps it trimmed and in NFC`() = runTest {
        withStore("schedules-nfc.db") { store ->
            val saved = assertIs<ScheduleResult.Success<SchedulePlan>>(store.save(plan(1, "  Café "), null)).value

            assertEquals("Café", saved.name)
            assertEquals("Café", store.snapshot().schedules.single().plan.name)
        }
    }

    @Test
    fun `given plans the wire would refuse when saved then nothing is stored`() = runTest {
        withStore("schedules-invalid.db") { store ->
            listOf(
                plan(1, "Line\nbreak"),
                plan(2, "   "),
                plan(3).copy(endMinute = 545),
                plan(4).copy(id = ScheduleId("00".repeat(16))),
            ).forEach { invalid ->
                assertEquals(ScheduleResult.Failure(ScheduleStoreFailure.INVALID_SCHEDULE), store.save(invalid, workspace))
            }
            assertEquals(emptyList(), store.snapshot().schedules)
            assertEquals(emptyList(), store.intents())
        }
    }

    @Test
    fun `given ten schedules when an eleventh is saved then it is refused but an edit still saves`() = runTest {
        withStore("schedules-cap.db") { store ->
            (1..10).forEach { store.save(plan(it), null) }

            assertEquals(ScheduleResult.Failure(ScheduleStoreFailure.CAPACITY), store.save(plan(11), null))
            assertIs<ScheduleResult.Success<SchedulePlan>>(store.save(plan(3, "Renamed"), null))
        }
    }

    @Test
    fun `given a schedule with facts when removed then its facts go and the removal is recorded`() = runTest {
        withStore("schedules-remove.db") { store ->
            store.save(plan(1), null)
            store.stop(setOf(OccurrenceKey(id(1), today)), OccurrenceStop.SKIP, today, null)

            store.remove(id(1), workspace)

            assertEquals(ScheduleSnapshot(), store.snapshot())
            assertEquals(listOf(ScheduleIntent.Remove(id(1))), store.intents())
        }
    }

    @Test
    fun `given a skip when linked then the fact and its intent are recorded together`() = runTest {
        withStore("schedules-skip.db") { store ->
            store.save(plan(1), null)
            val key = OccurrenceKey(id(1), today.plusDays(1))

            store.stop(setOf(key), OccurrenceStop.SKIP, today, workspace)

            assertEquals(setOf(key), store.snapshot().facts.skipped)
            assertEquals(listOf(ScheduleIntent.Skip(key, today)), store.intents())
        }
    }

    @Test
    fun `given running occurrences when the host records them then pins keep their notices and finished ones become terminal`() = runTest {
        withStore("schedules-host.db") { store ->
            val key = OccurrenceKey(id(1), today)
            store.save(plan(1), null)

            store.recordHost(ScheduleHostUpdate(pins = listOf(OccurrencePin(key, 1_000L))))
            store.recordHost(ScheduleHostUpdate(notices = mapOf(key to 1)))
            store.recordHost(ScheduleHostUpdate(pins = listOf(OccurrencePin(key, 2_000L)), notices = mapOf(key to 2)))
            assertEquals(listOf(OccurrencePin(key, 1_000L, 3)), store.snapshot().pins)

            store.recordHost(ScheduleHostUpdate(finished = setOf(key)))
            store.recordHost(ScheduleHostUpdate(finished = setOf(key)))

            assertEquals(emptyList(), store.snapshot().pins)
            assertEquals(setOf(key), store.snapshot().facts.terminal)
            assertEquals(emptyList(), store.intents())

            store.recordHost(ScheduleHostUpdate(pins = listOf(OccurrencePin(key, 1_000L))))
            assertEquals(emptyList(), store.snapshot().pins)
        }
    }

    @Test
    fun `given running occurrences when ended early while linked then each gets an end fact and one intent`() = runTest {
        withStore("schedules-end.db") { store ->
            val keys = setOf(OccurrenceKey(id(1), today), OccurrenceKey(id(2), today))

            store.stop(keys, OccurrenceStop.END, today, workspace)

            assertEquals(keys, store.snapshot().facts.ended)
            assertEquals(keys.map { ScheduleIntent.End(it, today) }.toSet(), store.intents().toSet())
        }
    }

    @Test
    fun `given local plans when a workspace is seeded then they are published once and removed ones are dropped`() = runTest {
        withStore("schedules-seed.db") { store ->
            store.save(plan(1), null)
            store.save(plan(2), null)
            store.save(plan(3), null)
            store.stop(setOf(OccurrenceKey(id(1), today.plusDays(-5))), OccurrenceStop.SKIP, today, null)
            store.stop(setOf(OccurrenceKey(id(1), today)), OccurrenceStop.SKIP, today, null)
            val synced = SyncedSchedules(live = listOf(plan(2, "Shared")), removed = setOf(id(3)))

            assertEquals(ScheduleResult.Success(true), store.seedOnce(workspace, synced, today))
            assertEquals(ScheduleResult.Success(false), store.seedOnce(workspace, synced, today))

            assertEquals(
                listOf(ScheduleIntent.Put(plan(1)), ScheduleIntent.Skip(OccurrenceKey(id(1), today), today)),
                store.intents(),
            )
            assertEquals(listOf(id(1), id(2)), store.snapshot().schedules.map { it.plan.id })
        }
    }

    @Test
    fun `given a pending edit when an older shared plan is materialized then the edit is still shown`() = runTest {
        withStore("schedules-overlay.db") { store ->
            store.save(plan(1, "Mine"), workspace)

            store.materialize(workspace, SyncedSchedules(live = listOf(plan(1, "Older"))))

            assertEquals(listOf("Mine"), store.snapshot().schedules.map { it.plan.name })
        }
    }

    @Test
    fun `given a pending edit when the schedule was removed elsewhere then the removal wins and the edit is dropped`() = runTest {
        withStore("schedules-removed.db") { store ->
            store.save(plan(1, "Mine"), workspace)

            store.materialize(workspace, SyncedSchedules(removed = setOf(id(1))))

            assertEquals(emptyList(), store.snapshot().schedules)
            assertEquals(emptyList(), store.intents())
        }
    }

    @Test
    fun `given a pending local removal when the shared plan still exists then it stays hidden`() = runTest {
        withStore("schedules-local-remove.db") { store ->
            store.save(plan(1), null)
            store.remove(id(1), workspace)

            store.materialize(workspace, SyncedSchedules(live = listOf(plan(1))))

            assertEquals(emptyList(), store.snapshot().schedules)
        }
    }

    @Test
    fun `given a plan the shared cap refused when materialized then it is kept and marked refused`() = runTest {
        withStore("schedules-refused.db") { store ->
            store.materialize(workspace, SyncedSchedules(live = listOf(plan(1)), refused = listOf(plan(2))))

            val shown = store.snapshot().schedules

            assertEquals(listOf(false, true), shown.map { it.refused })
            assertEquals(listOf(plan(1)), store.snapshot().runnable)
        }
    }

    @Test
    fun `given refused plans when a plan is removed without a workspace then they rejoin up to the cap`() = runTest {
        withStore("schedules-promote.db") { store ->
            store.materialize(workspace, SyncedSchedules(live = (1..10).map(::plan), refused = listOf(plan(11), plan(12))))

            store.remove(id(1), workspaceId = null)

            val shown = store.snapshot().schedules
            assertEquals(listOf(plan(11)), shown.filter { it.plan.id in setOf(id(11), id(12)) && !it.refused }.map { it.plan })
            assertEquals(10, store.snapshot().runnable.size)
        }
    }

    @Test
    fun `given another workspace's pending change when materialized then it does not overlay this workspace`() = runTest {
        withStore("schedules-other.db") { store ->
            store.save(plan(1, "Other"), otherWorkspace)

            store.materialize(workspace, SyncedSchedules(live = listOf(plan(1, "Shared"))))

            assertEquals(listOf("Shared"), store.snapshot().schedules.map { it.plan.name })
        }
    }

    @Test
    fun `given shared facts when materialized then they are merged and kept for live plans only`() = runTest {
        withStore("schedules-facts.db") { store ->
            val live = OccurrenceKey(id(1), today)
            val orphan = OccurrenceKey(id(9), today)

            store.materialize(workspace, SyncedSchedules(live = listOf(plan(1)), skips = setOf(live, orphan), ends = setOf(live)))

            val facts = store.snapshot().facts
            assertEquals(setOf(live), facts.skipped)
            assertTrue(live in facts.ended)
        }
    }
}
