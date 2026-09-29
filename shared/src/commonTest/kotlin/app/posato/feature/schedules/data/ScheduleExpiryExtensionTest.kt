package app.posato.feature.schedules.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.host.ScheduleHostPolicy
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScheduleExpiryExtensionTest {
    @Test
    fun `remote extension resumes persisted expiry but replay and rollback do not`() {
        runTest {
            val fixture = createLocalPolicyTestDatabase("schedule-expiry-extension.db")
            val driver = fixture.openDriver()
            try {
                val store = SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default)
                val date = ScheduleDate(2026, 9, 28)
                val zone = CentralEuropeanZone
                val plan = SchedulePlan(ScheduleId("000000000000400080000000000000a1"), "Focus", 1, 540, 600, true)
                val workspace = ByteArray(16) { 9 }
                store.save(plan, null)
                val start = ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 570), zone)
                assertEquals(1, start.running.size)
                store.recordHost(start.update)
                val expired = ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 600), zone)
                assertTrue(expired.running.isEmpty())
                store.recordHost(expired.update)
                store.materialize(workspace, SyncedSchedules(live = listOf(plan)))
                assertTrue(ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 570), zone).running.isEmpty())
                val extended = plan.copy(startMinute = 550, endMinute = 720)
                store.materialize(workspace, SyncedSchedules(live = listOf(extended)))
                assertTrue(ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 570), zone).running.isEmpty())
                val resumed = ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 660), zone)
                assertEquals(1, resumed.running.size)
                assertEquals(zone.instantOf(date, 600), resumed.running.single().startEpochMillis)
                assertEquals(zone.instantOf(date, 720), resumed.running.single().endEpochMillis)
                store.recordHost(resumed.update)
                val rollback = ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 570), zone)
                assertTrue(rollback.running.isEmpty())
                store.recordHost(rollback.update)
                val returned = ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 660), zone)
                assertEquals(zone.instantOf(date, 600), returned.running.single().startEpochMillis)
                store.recordHost(expired.update)
                store.materialize(workspace, SyncedSchedules(live = listOf(extended)))
                assertEquals(1, ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 660), zone).running.size)
                store.stop(setOf(resumed.running.single().key), OccurrenceStop.END, date, workspace)
                assertTrue(ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 660), zone).running.isEmpty())
            } finally {
                driver.close()
                fixture.delete()
            }
        }
    }

    @Test
    fun `given a resumed occurrence when its end is set back to the observed end then no pin holds an update`() {
        runTest {
            val fixture = createLocalPolicyTestDatabase("schedule-expiry-undo.db")
            val driver = fixture.openDriver()
            try {
                val database = PosatoDatabase(driver)
                val store = SqlScheduleStore(database, Dispatchers.Default)
                val date = ScheduleDate(2026, 9, 28)
                val zone = CentralEuropeanZone
                val plan = SchedulePlan(ScheduleId("000000000000400080000000000000a2"), "Focus", 1, 540, 600, true)
                val workspace = ByteArray(16) { 9 }
                store.save(plan, null)
                store.recordHost(ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 570), zone).update)
                store.recordHost(ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 600), zone).update)
                store.materialize(workspace, SyncedSchedules(live = listOf(plan.copy(endMinute = 720))))
                val resumed = ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 660), zone)
                assertEquals(1, resumed.running.size)
                store.recordHost(resumed.update)
                store.materialize(workspace, SyncedSchedules(live = listOf(plan)))
                val undone = ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 675), zone)
                assertTrue(undone.running.isEmpty())
                store.recordHost(undone.update)
                assertFalse(database.hasRunningSchedule(zone.instantOf(date, 680)))
                assertTrue(ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 680), zone).update.isEmpty)
            } finally {
                driver.close()
                fixture.delete()
            }
        }
    }

    private suspend fun SqlScheduleStore.snapshot(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(read()).value
    }
}
