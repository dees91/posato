package app.posato.feature.schedules.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleOccurrences
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.host.HostStep
import app.posato.feature.schedules.host.ScheduleHostPolicy
import app.posato.feature.schedules.host.ScheduleMonitorInput
import app.posato.feature.schedules.host.ScheduleMonitorTables
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val monday = ScheduleDate(2026, 9, 28)
private val zone = CentralEuropeanZone
private const val MONDAY: Int = 1
private const val TUESDAY: Int = 1 shl 1

private fun at(
    hour: Int,
    minute: Int = 0,
): Long {
    return zone.instantOf(monday, hour * 60 + minute)
}

private class EditFixture(
    val store: SqlScheduleStore,
    var plan: SchedulePlan,
) {
    val key = OccurrenceKey(plan.id, monday)

    suspend fun snapshot(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(store.read()).value
    }

    suspend fun edit(change: SchedulePlan.() -> SchedulePlan) {
        plan = plan.change()
        store.save(plan, null)
    }

    /** One host evaluation at [now], persisted as the host does. */
    suspend fun step(now: Long): HostStep {
        val step = ScheduleHostPolicy.step(snapshot(), now, zone)
        store.recordHost(step.update)
        return step
    }
}

class ScheduleEditRuleTest {
    private suspend fun withPlan(
        name: String,
        plan: SchedulePlan = SchedulePlan(ScheduleId("000000000000400080000000000000b1"), "Focus", MONDAY, 9 * 60, 10 * 60, true),
        block: suspend (EditFixture) -> Unit,
    ) {
        val database = createLocalPolicyTestDatabase(name)
        val driver = database.openDriver()
        try {
            val store = SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default)
            store.save(plan, null)
            block(EditFixture(store, plan))
        } finally {
            driver.close()
            database.delete()
        }
    }

    @Test
    fun `given a running occurrence when its start moves past now then it stops and runs again from the new start`() = runTest {
        withPlan("edit-start-later.db") { fixture ->
            assertEquals(1, fixture.step(at(9, 30)).running.size)
            fixture.edit { copy(startMinute = 9 * 60 + 45) }

            assertTrue(fixture.step(at(9, 31)).running.isEmpty())
            assertEquals(at(9, 45), fixture.step(at(9, 50)).running.single().startEpochMillis)
        }
    }

    @Test
    fun `given a running occurrence when its weekday is removed then it stops and returns when the weekday is back`() = runTest {
        withPlan("edit-weekday.db") { fixture ->
            fixture.step(at(9, 30))
            fixture.edit { copy(weekdays = TUESDAY) }
            assertTrue(fixture.step(at(9, 31)).running.isEmpty())

            fixture.edit { copy(weekdays = MONDAY or TUESDAY) }
            assertEquals(1, fixture.step(at(9, 32)).running.size)
        }
    }

    @Test
    fun `given a running occurrence when its schedule is turned off and on inside the interval then it runs again`() = runTest {
        withPlan("edit-off-on.db") { fixture ->
            fixture.step(at(9, 30))
            fixture.edit { copy(enabled = false) }
            assertTrue(fixture.step(at(9, 31)).running.isEmpty())

            fixture.edit { copy(enabled = true) }
            assertEquals(1, fixture.step(at(9, 32)).running.size)
        }
    }

    @Test
    fun `given an expired occurrence when the plan moves later the same day then it waits and runs at the new interval`() = runTest {
        withPlan("edit-later-after-expiry.db") { fixture ->
            fixture.step(at(9, 30))
            fixture.step(at(10))
            fixture.edit { copy(startMinute = 22 * 60, endMinute = 8 * 60) }

            assertTrue(fixture.step(at(10, 30)).running.isEmpty())
            val next = ScheduleOccurrences.next(fixture.plan, fixture.snapshot().facts, at(10, 30), zone)
            assertEquals(fixture.key, next?.key)
            assertEquals(at(22), next?.startEpochMillis)
            val table = monitorTable(fixture.snapshot(), at(10, 30))
            assertEquals(emptyList(), table.schedules.single().stoppedDates)

            val evening = fixture.step(at(22, 5)).running.single()
            assertEquals(at(22), evening.startEpochMillis)
        }
    }

    @Test
    fun `given an expired occurrence when its end is extended past now then it resumes from the observed end`() = runTest {
        withPlan("edit-extend-after-expiry.db") { fixture ->
            fixture.step(at(9, 30))
            fixture.step(at(10))
            fixture.edit { copy(endMinute = 12 * 60) }

            val resumed = fixture.step(at(11)).running.single()
            assertEquals(at(10), resumed.startEpochMillis)
            assertEquals(at(12), resumed.endEpochMillis)

            assertTrue(fixture.step(at(9, 30)).running.isEmpty())
            assertEquals(1, fixture.step(at(11, 5)).running.size)
        }
    }

    @Test
    fun `given an expired occurrence when the plan is unchanged and the clock goes back then it does not run again`() = runTest {
        withPlan("edit-expiry-rollback.db") { fixture ->
            fixture.step(at(9, 30))
            fixture.step(at(10))

            assertTrue(fixture.step(at(9, 30)).running.isEmpty())
            fixture.edit { copy(name = "Renamed") }
            assertTrue(fixture.step(at(9, 40)).running.isEmpty())
        }
    }

    @Test
    fun `given a skipped or ended occurrence when the plan moves later the same day then it stays stopped`() = runTest {
        withPlan("edit-after-skip.db") { fixture ->
            fixture.store.stop(setOf(fixture.key), OccurrenceStop.SKIP, monday, null)
            fixture.edit { copy(startMinute = 22 * 60, endMinute = 23 * 60) }
            assertTrue(fixture.step(at(22, 5)).running.isEmpty())
        }
        withPlan("edit-after-end.db") { fixture ->
            fixture.step(at(9, 30))
            fixture.store.stop(setOf(fixture.key), OccurrenceStop.END, monday, null)
            fixture.edit { copy(startMinute = 22 * 60, endMinute = 23 * 60) }
            assertTrue(fixture.step(at(22, 5)).running.isEmpty())
        }
    }

    private fun monitorTable(
        snapshot: ScheduleSnapshot,
        now: Long,
    ) = ScheduleMonitorTables.build(ScheduleMonitorInput(snapshot, emptyList(), now) { targets() }, zone, targets())

    private fun targets(): SessionTargetsState {
        val policy = assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(listOf("example.com"), null)).policy
        return SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Unavailable())
    }
}
