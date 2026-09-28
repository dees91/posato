package app.posato.feature.schedules.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SchedulePlanTest {
    private fun plan(
        name: String = "Focus",
        weekdays: Int = 0b0011111,
        start: Int = 9 * 60,
        end: Int = 12 * 60,
    ): SchedulePlan {
        return SchedulePlan(ScheduleId("00".repeat(16)), name, weekdays, start, end, enabled = true)
    }

    @Test
    fun `given valid plans including one across midnight then there is no problem`() {
        assertNull(plan().problem())
        assertNull(plan(start = 22 * 60, end = 6 * 60).problem())
        assertNull(plan(start = 23 * 60 + 50, end = 5).problem())
        assertNull(plan(name = "é".repeat(40)).problem())
    }

    @Test
    fun `given invalid plans then each names its problem`() {
        assertEquals(SchedulePlanProblem.NAME_LENGTH, plan(name = "").problem())
        assertEquals(SchedulePlanProblem.NAME_LENGTH, plan(name = "é".repeat(41)).problem())
        assertEquals(SchedulePlanProblem.NO_WEEKDAY, plan(weekdays = 0).problem())
        assertEquals(SchedulePlanProblem.NO_WEEKDAY, plan(weekdays = 0b10000000).problem())
        assertEquals(SchedulePlanProblem.MINUTE_RANGE, plan(start = 1440).problem())
        assertEquals(SchedulePlanProblem.MINUTE_RANGE, plan(end = -1).problem())
        assertEquals(SchedulePlanProblem.SAME_TIMES, plan(start = 600, end = 600).problem())
        assertEquals(SchedulePlanProblem.TOO_SHORT, plan(start = 600, end = 614).problem())
        assertEquals(SchedulePlanProblem.TOO_SHORT, plan(start = 1435, end = 9).problem())
        assertNull(plan(start = 1435, end = 10).problem())
    }
}
