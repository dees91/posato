package app.posato.feature.schedules.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE: Long = 60_000L
private const val HOUR: Long = 60 * MINUTE
private const val MONDAY: Int = 1
private const val FRIDAY: Int = 1 shl 4
private const val SATURDAY: Int = 1 shl 5
private const val SUNDAY: Int = 1 shl 6
private const val EVERY_DAY: Int = 0b1111111

private fun plan(
    id: String,
    weekdays: Int,
    start: Int,
    end: Int,
    enabled: Boolean = true,
): SchedulePlan {
    return SchedulePlan(ScheduleId(id), "Plan $id", weekdays, start, end, enabled)
}

/** Local wall time in [CentralEuropeanZone] on 2026-09 (UTC+2 summer time). */
private fun summer(
    day: Int,
    hour: Int,
    minute: Int = 0,
): Long {
    return utc(ScheduleDate(2026, 9, day), hour * 60 + minute) - 2 * HOUR
}

class ScheduleOccurrencesTest {
    private val zone = CentralEuropeanZone

    @Test
    fun `given a weekday plan then it runs from its start until its end on that day only`() {
        // 2026-09-28 is a Monday.
        val focus = plan("a", MONDAY, 9 * 60, 12 * 60)

        assertTrue(ScheduleOccurrences.active(listOf(focus), ScheduleFacts(), summer(28, 8, 59), zone).isEmpty())
        val running = ScheduleOccurrences.active(listOf(focus), ScheduleFacts(), summer(28, 9), zone).single()
        assertEquals(OccurrenceKey(focus.id, ScheduleDate(2026, 9, 28)), running.key)
        assertEquals(summer(28, 12), running.endEpochMillis)
        assertTrue(ScheduleOccurrences.active(listOf(focus), ScheduleFacts(), summer(28, 12), zone).isEmpty())
        assertTrue(ScheduleOccurrences.active(listOf(focus), ScheduleFacts(), summer(29, 10), zone).isEmpty())
    }

    @Test
    fun `given a plan across midnight then the weekday of its start decides and it ends the next morning`() {
        // Friday 22:00 until Saturday 06:00; 2026-09-25 is a Friday.
        val night = plan("b", FRIDAY, 22 * 60, 6 * 60)

        val saturdayMorning = ScheduleOccurrences.active(listOf(night), ScheduleFacts(), summer(26, 5, 59), zone).single()
        assertEquals(ScheduleDate(2026, 9, 25), saturdayMorning.key.date)
        assertEquals(summer(26, 6), saturdayMorning.endEpochMillis)
        assertTrue(ScheduleOccurrences.active(listOf(night), ScheduleFacts(), summer(26, 22, 30), zone).isEmpty())
    }

    @Test
    fun `given a skipped or ended occurrence then it does not run and the plan keeps repeating`() {
        val daily = plan("c", EVERY_DAY, 9 * 60, 10 * 60)
        val today = OccurrenceKey(daily.id, ScheduleDate(2026, 9, 28))
        listOf(
            ScheduleFacts(skipped = setOf(today)),
            ScheduleFacts(ended = setOf(today)),
        ).forEach { facts ->
            assertTrue(ScheduleOccurrences.active(listOf(daily), facts, summer(28, 9, 30), zone).isEmpty(), "$facts")
            assertEquals(1, ScheduleOccurrences.active(listOf(daily), facts, summer(29, 9, 30), zone).size, "$facts")
        }
    }

    @Test
    fun `given a disabled plan then nothing runs and there is no next run`() {
        val off = plan("d", EVERY_DAY, 9 * 60, 10 * 60, enabled = false)

        assertTrue(ScheduleOccurrences.active(listOf(off), ScheduleFacts(), summer(28, 9, 30), zone).isEmpty())
        assertNull(ScheduleOccurrences.next(off, ScheduleFacts(), summer(28, 8), zone))
    }

    @Test
    fun `given overlapping plans then one pause shows the earliest started name and the latest end`() {
        val morning = plan("e", EVERY_DAY, 8 * 60, 10 * 60)
        val late = plan("f", EVERY_DAY, 9 * 60, 11 * 60)

        val pause = ScheduleOccurrences.pause(listOf(late, morning), ScheduleFacts(), summer(28, 9, 30), zone)

        assertEquals("Plan e", pause?.name)
        assertEquals(summer(28, 11), pause?.endEpochMillis)
        assertEquals(2, pause?.occurrences?.size)
        assertNull(ScheduleOccurrences.pause(listOf(late, morning), ScheduleFacts(), summer(28, 12), zone))
        val twin = plan("0", EVERY_DAY, 8 * 60, 9 * 60)
        val names = listOf(listOf(morning, twin), listOf(twin, morning)).map {
            ScheduleOccurrences.pause(it, ScheduleFacts(), summer(28, 8, 30), zone)?.name
        }
        assertEquals(listOf("Plan 0", "Plan 0"), names)
    }

    @Test
    fun `given a skipped next occurrence then the next run moves to the following one`() {
        val weekly = plan("g", MONDAY, 9 * 60, 10 * 60)
        val nextMonday = OccurrenceKey(weekly.id, ScheduleDate(2026, 9, 28))

        assertEquals(nextMonday, ScheduleOccurrences.next(weekly, ScheduleFacts(), summer(27, 12), zone)?.key)
        val afterSkip = ScheduleOccurrences.next(weekly, ScheduleFacts(skipped = setOf(nextMonday)), summer(27, 12), zone)
        assertEquals(ScheduleDate(2026, 10, 5), afterSkip?.key?.date)
        // While Monday's occurrence runs, the next run is the one after it.
        assertEquals(ScheduleDate(2026, 10, 5), ScheduleOccurrences.next(weekly, ScheduleFacts(), summer(28, 9, 30), zone)?.key?.date)
    }

    @Test
    fun `given a start inside the spring-forward gap then it starts at the first valid minute after it`() {
        // 2026-03-29 is a Sunday; local 02:00 to 03:00 does not exist.
        val early = plan("h", SUNDAY, 2 * 60 + 30, 5 * 60)
        val gap = utc(ScheduleDate(2026, 3, 29), 60)

        val running = ScheduleOccurrences.active(listOf(early), ScheduleFacts(), gap, zone).single()

        assertEquals(gap, running.startEpochMillis)
        assertEquals(utc(ScheduleDate(2026, 3, 29), 3 * 60), running.endEpochMillis)
        assertTrue(ScheduleOccurrences.active(listOf(early), ScheduleFacts(), gap - MINUTE, zone).isEmpty())
        // A plan wholly inside the gap has no occurrence that day, so the next run is the following Sunday.
        val inside = plan("k", SUNDAY, 2 * 60, 2 * 60 + 45)
        assertEquals(ScheduleDate(2026, 4, 5), ScheduleOccurrences.next(inside, ScheduleFacts(), gap - HOUR, zone)?.key?.date)
    }

    @Test
    fun `given a start in the repeated fall-back hour then it starts at its first instance and the occurrence is an hour longer`() {
        // 2026-10-25 is a Sunday; local 02:00 to 03:00 happens twice.
        val early = plan("i", SUNDAY, 2 * 60 + 30, 6 * 60)
        val firstInstance = utc(ScheduleDate(2026, 10, 25), 30)

        val running = ScheduleOccurrences.active(listOf(early), ScheduleFacts(), firstInstance, zone).single()

        assertEquals(firstInstance, running.startEpochMillis)
        assertEquals(utc(ScheduleDate(2026, 10, 25), 5 * 60), running.endEpochMillis)
        assertEquals(4 * HOUR + 30 * MINUTE, running.endEpochMillis - running.startEpochMillis)
    }

    @Test
    fun `given a near-full-day plan across the fall-back change then it ends after 24 hours of real time`() {
        // Saturday 04:00 until Sunday 03:50 local is 23 h 50 min on the wall clock but 24 h 50 min of real time.
        val long = plan("j", SATURDAY, 4 * 60, 3 * 60 + 50)
        val start = utc(ScheduleDate(2026, 10, 24), 2 * 60)

        val running = ScheduleOccurrences.active(listOf(long), ScheduleFacts(), start + HOUR, zone).single()

        assertEquals(start, running.startEpochMillis)
        assertEquals(start + 24 * HOUR, running.endEpochMillis)
    }
}
