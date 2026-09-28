package app.posato.feature.schedules.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MINUTE: Long = 60_000L
private const val HOUR: Long = 60 * MINUTE
private const val MONDAY: Int = 1
private const val TUESDAY: Int = 1 shl 1
private const val SATURDAY: Int = 1 shl 5

private val focusId = ScheduleId("000000000000400080000000000000a1")

private fun focus(
    weekdays: Int = MONDAY,
    start: Int = 9 * 60,
    end: Int = 10 * 60,
    enabled: Boolean = true,
): SchedulePlan {
    return SchedulePlan(focusId, "Focus", weekdays, start, end, enabled)
}

/** Local wall time in [CentralEuropeanZone]; October 25 2026 falls back from UTC+2 to UTC+1 at 03:00. */
private fun local(
    month: Int,
    day: Int,
    hour: Int,
    minute: Int = 0,
): Long {
    return CentralEuropeanZone.instantOf(ScheduleDate(2026, month, day), hour * 60 + minute)
}

/** Monday 2026-09-28, pinned when it started at 09:00. */
private val mondayKey = OccurrenceKey(focusId, ScheduleDate(2026, 9, 28))
private val mondayPin = OccurrencePin(mondayKey, local(9, 28, 9))

class ScheduleOccurrencePinTest {
    private val zone = CentralEuropeanZone

    private fun running(
        plan: SchedulePlan?,
        now: Long,
        facts: ScheduleFacts = ScheduleFacts(),
        pins: List<OccurrencePin> = listOf(mondayPin),
    ): List<ScheduleOccurrence> {
        return ScheduleOccurrences.active(listOfNotNull(plan), facts, now, zone, pins)
    }

    @Test
    fun `given a pinned occurrence when its start moves later or its weekday is removed then it keeps running to the current end`() {
        val moved = running(focus(start = 11 * 60, end = 12 * 60), local(9, 28, 9, 45)).single()
        assertEquals(mondayKey, moved.key)
        assertEquals(local(9, 28, 9), moved.startEpochMillis)
        assertEquals(local(9, 28, 12), moved.endEpochMillis)

        val weekdayRemoved = running(focus(weekdays = TUESDAY), local(9, 28, 9, 45)).single()
        assertEquals(local(9, 28, 10), weekdayRemoved.endEpochMillis)
    }

    @Test
    fun `given a pinned occurrence when its end is shortened to a time already passed then it ends`() {
        assertTrue(running(focus(end = 9 * 60 + 30), local(9, 28, 9, 45)).isEmpty())
    }

    @Test
    fun `given an end at or before the pinned start time then it ends the next day`() {
        val overnight = running(focus(end = 8 * 60), local(9, 28, 23)).single()
        assertEquals(local(9, 29, 8), overnight.endEpochMillis)
    }

    @Test
    fun `given a pinned occurrence across the autumn change then it lasts at most 24 hours`() {
        // Saturday 2026-10-24 10:00 until Sunday 10:00 local is 25 hours.
        val key = OccurrenceKey(focusId, ScheduleDate(2026, 10, 24))
        val pin = OccurrencePin(key, local(10, 24, 10))
        val long = running(focus(weekdays = SATURDAY, start = 10 * 60, end = 10 * 60), local(10, 24, 12), pins = listOf(pin)).single()

        assertEquals(local(10, 24, 10) + 24 * HOUR, long.endEpochMillis)
    }

    @Test
    fun `given a pinned occurrence when the plan is off deleted skipped ended or terminal then it stops`() {
        val now = local(9, 28, 9, 30)
        assertTrue(running(focus(enabled = false), now).isEmpty())
        assertTrue(running(null, now).isEmpty())
        assertTrue(running(focus(), now, ScheduleFacts(skipped = setOf(mondayKey))).isEmpty())
        assertTrue(running(focus(), now, ScheduleFacts(ended = setOf(mondayKey))).isEmpty())
        assertTrue(running(focus(), now, ScheduleFacts(terminal = setOf(mondayKey))).isEmpty())
    }

    @Test
    fun `given a pinned occurrence whose plan also derives it then it runs once from the pinned start`() {
        val once = running(focus(start = 9 * 60 + 15), local(9, 28, 9, 30)).single()

        assertEquals(local(9, 28, 9), once.startEpochMillis)
    }

    @Test
    fun `given the clock set back before the pinned start then the pinned occurrence is not running`() {
        assertTrue(running(focus(start = 8 * 60), local(9, 28, 8, 30)).none { it.startEpochMillis == mondayPin.startEpochMillis })
    }

    @Test
    fun `given a pinned occurrence then the pause shows it with its end`() {
        val pause = ScheduleOccurrences.pause(listOf(focus(end = 11 * 60)), ScheduleFacts(), local(9, 28, 9, 30), zone, listOf(mondayPin))

        assertEquals("Focus", pause?.name)
        assertEquals(local(9, 28, 11), pause?.endEpochMillis)
    }
}
