package app.posato.feature.schedules.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleDateTest {
    @Test
    fun `given known dates then epoch days and weekdays match the calendar`() {
        val vectors = listOf(
            Triple(ScheduleDate(1970, 1, 1), 0L, 3),
            Triple(ScheduleDate(1969, 12, 31), -1L, 2),
            Triple(ScheduleDate(2000, 2, 29), 11_016L, 1),
            Triple(ScheduleDate(2000, 3, 1), 11_017L, 2),
            Triple(ScheduleDate(2026, 9, 27), 20_723L, 6),
            Triple(ScheduleDate(2100, 3, 1), 47_541L, 0),
        )
        vectors.forEach { (date, epochDay, weekday) ->
            assertEquals(epochDay, date.epochDay, "$date")
            assertEquals(weekday, date.weekday, "$date")
            assertEquals(date, ScheduleDate.ofEpochDay(epochDay), "$date")
        }
    }

    @Test
    fun `given month and year ends then adding days rolls over`() {
        assertEquals(ScheduleDate(2028, 3, 1), ScheduleDate(2028, 2, 28).plusDays(2))
        assertEquals(ScheduleDate(2027, 1, 1), ScheduleDate(2026, 12, 31).plusDays(1))
        assertEquals(ScheduleDate(2026, 9, 26), ScheduleDate(2026, 9, 27).plusDays(-1))
    }
}
