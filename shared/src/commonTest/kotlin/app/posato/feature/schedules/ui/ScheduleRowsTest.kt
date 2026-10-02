package app.posato.feature.schedules.ui

import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.StoredSchedule
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.session.ui.FakeSessionTimeFormat
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.ui.PauseSetRow
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** A schedule whose set this device lacks says whether to wait for it or to choose another set. */
class ScheduleRowsTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val plan = SchedulePlan(ScheduleId("000000000000400080000000000000a1"), "Focus", 1, 540, 600, true, work)
    private val first = PauseSetRow(PauseSetId.FIRST, "My set", true, 1, null, persistentListOf(), refused = false, inUse = false)

    @Test
    fun `given a linked device whose workspace removed the schedule's set then the row asks to choose a set`() {
        val rows = buildScheduleRows(
            ScheduleSnapshot(schedules = listOf(StoredSchedule(plan))),
            0L,
            CentralEuropeanZone,
            FakeSessionTimeFormat(),
            ScheduleSetContext(listOf(first), linked = true, removed = setOf(work)),
        )

        assertEquals("This schedule's set was deleted. Choose a set.", rows.single().setProblem)
    }

    @Test
    fun `given a linked device that has not received the schedule's set then the row says it is waiting`() {
        val rows = buildScheduleRows(
            ScheduleSnapshot(schedules = listOf(StoredSchedule(plan))),
            0L,
            CentralEuropeanZone,
            FakeSessionTimeFormat(),
            ScheduleSetContext(listOf(first), linked = true),
        )

        assertEquals("Waiting for this set from your other devices.", rows.single().setProblem)
    }
}
