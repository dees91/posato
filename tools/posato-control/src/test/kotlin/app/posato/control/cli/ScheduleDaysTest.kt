package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A verification schedule must run on the day of the run; the editor starts a new plan on weekdays only, so a weekend
 * run silently saved a plan that never started. These cases pin which day buttons a run presses and what it expects the
 * saved row to list.
 */
class ScheduleDaysTest {
    @Test
    fun `reads every day, weekdays, and a list of short day names`() {
        assertEquals(ScheduleDays.ALL, ScheduleDays.parse("every"))
        assertEquals(ScheduleDays.ALL.take(5), ScheduleDays.parse("weekdays"))
        assertEquals(listOf("Monday", "Sunday"), ScheduleDays.parse("sun, MON"))
    }

    @Test
    fun `refuses an unknown day or an empty list`() {
        assertEquals(ErrorCode.USAGE, assertFailsWith<ControlException> { ScheduleDays.parse("mon,funday") }.code)
        assertEquals(ErrorCode.USAGE, assertFailsWith<ControlException> { ScheduleDays.parse(" , ") }.code)
    }

    @Test
    fun `presses only the days that differ from the editor's weekday default`() {
        assertEquals(listOf("Saturday", "Sunday"), ScheduleDays.toggles(ScheduleDays.ALL))
        assertEquals(emptyList(), ScheduleDays.toggles(ScheduleDays.parse("weekdays")))
        assertEquals(listOf("Tuesday", "Wednesday", "Thursday", "Friday", "Sunday"), ScheduleDays.toggles(ScheduleDays.parse("mon,sun")))
    }

    @Test
    fun `expects the saved row to list the days as the Schedules screen does`() {
        assertEquals("Mon, Tue, Wed, Thu, Fri, Sat, Sun", ScheduleDays.summary(ScheduleDays.ALL))
        assertEquals("Mon, Sun", ScheduleDays.summary(ScheduleDays.parse("sun,mon")))
    }
}
