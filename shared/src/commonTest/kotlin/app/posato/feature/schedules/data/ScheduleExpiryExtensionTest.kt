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
                assertEquals(zone.instantOf(date, 540), resumed.running.single().startEpochMillis)
                assertEquals(zone.instantOf(date, 720), resumed.running.single().endEpochMillis)
                store.recordHost(resumed.update)
                assertTrue(ScheduleHostPolicy.step(store.snapshot(), zone.instantOf(date, 570), zone).running.isEmpty())
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

    private suspend fun SqlScheduleStore.snapshot(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(read()).value
    }
}
