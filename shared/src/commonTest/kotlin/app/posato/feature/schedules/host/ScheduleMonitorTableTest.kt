package app.posato.feature.schedules.host

import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.StoredSchedule
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleFacts
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleOccurrence
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val monday = ScheduleDate(2026, 9, 28)

private fun id(value: Int): ScheduleId {
    return ScheduleId("00000000000040008000${value.toString(16).padStart(12, '0')}")
}

private fun plan(
    value: Int,
    enabled: Boolean = true,
): SchedulePlan {
    return SchedulePlan(id(value), "Plan $value", 1, 540, 600, enabled)
}

class ScheduleMonitorTableTest {
    private val targets = SessionTargetsState(
        assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(listOf("example.com"), null)).policy,
        LocalApplicationMappingsLoadResult.Unavailable(),
    )

    @Test
    fun `given plans and facts then only enabled accepted plans are listed with their stopped dates in the window`() {
        val snapshot = ScheduleSnapshot(
            schedules = listOf(
                StoredSchedule(plan(2)),
                StoredSchedule(plan(1)),
                StoredSchedule(plan(3, enabled = false)),
                StoredSchedule(plan(4), refused = true),
            ),
            facts = ScheduleFacts(
                skipped = setOf(OccurrenceKey(id(1), monday.plusDays(7)), OccurrenceKey(id(1), monday.plusDays(-10))),
                ended = setOf(OccurrenceKey(id(1), monday)),
                terminal = setOf(OccurrenceKey(id(2), monday.plusDays(-1))),
            ),
        )
        val now = CentralEuropeanZone.instantOf(monday, 8 * 60)
        val input = ScheduleMonitorInput(snapshot, emptyList(), now) { targets }

        val table = ScheduleMonitorTables.build(input, CentralEuropeanZone, targets)

        assertEquals(listOf(id(1).hex, id(2).hex), table.schedules.map { it.id })
        assertEquals(listOf(monday, monday.plusDays(7)), table.schedules[0].stoppedDates)
        assertEquals(listOf(monday.plusDays(-1)), table.schedules[1].stoppedDates)
        assertEquals(listOf("example.com"), table.domains)
        assertEquals(emptyList(), table.mappingIds)
    }

    @Test
    fun `given a running occurrence then it is listed with its pinned start and end`() {
        val occurrence = ScheduleOccurrence(OccurrenceKey(id(1), monday), "Plan 1", 1_000L, 5_000L)
        val input = ScheduleMonitorInput(ScheduleSnapshot(schedules = listOf(StoredSchedule(plan(1)))), listOf(occurrence), 2_000L) { targets }

        val table = ScheduleMonitorTables.build(input, CentralEuropeanZone, targets)

        assertEquals(listOf(MonitorRunning(id(1).hex, monday, 1_000L, 5_000L)), table.running)
    }
}
