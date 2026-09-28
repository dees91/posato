package app.posato.feature.schedules.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OffsetScheduleZoneTest {
    private val springForward = utc(ScheduleDate(2026, 3, 29), 60)
    private val fallBack = utc(ScheduleDate(2026, 10, 25), 60)

    /** The same 2026 Central European rules as the synthetic zone, given only as an offset function. */
    private val zone = OffsetScheduleZone { epochMillis ->
        if (epochMillis in springForward until fallBack) 7_200_000L else 3_600_000L
    }

    @Test
    fun `given the offset rules when resolving wall times then the answers match the synthetic zone`() {
        listOf(
            ScheduleDate(2026, 9, 28) to 9 * 60,
            ScheduleDate(2026, 3, 29) to 2 * 60 + 30,
            ScheduleDate(2026, 3, 29) to 2 * 60,
            ScheduleDate(2026, 10, 25) to 2 * 60 + 30,
            ScheduleDate(2026, 10, 25) to 3 * 60,
            ScheduleDate(2026, 1, 1) to 0,
        ).forEach { (date, minute) ->
            assertEquals(CentralEuropeanZone.instantOf(date, minute), zone.instantOf(date, minute), "$date $minute")
        }
    }

    @Test
    fun `given instants around the changes when read locally then the answers match the synthetic zone`() {
        listOf(springForward - 1, springForward, fallBack - 1, fallBack, utc(ScheduleDate(2026, 9, 27), 22 * 60)).forEach { instant ->
            assertEquals(CentralEuropeanZone.localAt(instant), zone.localAt(instant), "$instant")
        }
    }
}
