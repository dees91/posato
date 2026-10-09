package app.posato.feature.session.domain

import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.utc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionEndOfDayTest {
    @Test
    fun `given a short spring forward day when the end of day is resolved then it is the next local midnight`() {
        val now = utc(ScheduleDate(2026, 3, 28), 23 * 60 + 30)

        val end = SessionSetup.endOfDay(now, CentralEuropeanZone)

        assertEquals(utc(ScheduleDate(2026, 3, 29), 22 * 60), end)
    }

    @Test
    fun `given a long fall back day with more than a day left when the end of day is resolved then none is offered`() {
        val now = utc(ScheduleDate(2026, 10, 24), 22 * 60 + 30)

        val end = SessionSetup.endOfDay(now, CentralEuropeanZone)

        assertNull(end)
    }

    @Test
    fun `given a long fall back day with less than a day left when the end of day is resolved then it is the next local midnight`() {
        val now = utc(ScheduleDate(2026, 10, 24), 23 * 60 + 30)

        val end = SessionSetup.endOfDay(now, CentralEuropeanZone)

        assertEquals(utc(ScheduleDate(2026, 10, 25), 23 * 60), end)
    }

    @Test
    fun `given exactly five minutes before midnight when the end of day is resolved then it is offered`() {
        val midnight = utc(ScheduleDate(2026, 6, 10), 22 * 60)

        val end = SessionSetup.endOfDay(midnight - FIVE_MINUTES, CentralEuropeanZone)

        assertEquals(midnight, end)
    }

    @Test
    fun `given less than five minutes before midnight when the end of day is resolved then none is offered`() {
        val midnight = utc(ScheduleDate(2026, 6, 10), 22 * 60)

        val end = SessionSetup.endOfDay(midnight - FIVE_MINUTES + 1, CentralEuropeanZone)

        assertNull(end)
    }

    private companion object {
        const val FIVE_MINUTES: Long = 5 * 60_000L
    }
}
