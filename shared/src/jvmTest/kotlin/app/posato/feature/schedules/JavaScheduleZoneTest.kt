package app.posato.feature.schedules

import app.posato.feature.schedules.domain.LocalMinute
import app.posato.feature.schedules.domain.ScheduleDate
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaScheduleZoneTest {
    private val warsaw = JavaScheduleZone { ZoneId.of("Europe/Warsaw") }

    private fun instant(text: String): Long {
        return Instant.parse(text).toEpochMilli()
    }

    @Test
    fun `given ordinary and changed wall times then they resolve by the schedule rules`() {
        assertEquals(instant("2026-09-28T07:00:00Z"), warsaw.instantOf(ScheduleDate(2026, 9, 28), 9 * 60))
        // 02:30 does not exist on 2026-03-29: the start is the change at 03:00 local.
        assertEquals(instant("2026-03-29T01:00:00Z"), warsaw.instantOf(ScheduleDate(2026, 3, 29), 2 * 60 + 30))
        // 02:30 happens twice on 2026-10-25: the first instance, still in summer time.
        assertEquals(instant("2026-10-25T00:30:00Z"), warsaw.instantOf(ScheduleDate(2026, 10, 25), 2 * 60 + 30))
    }

    @Test
    fun `given the system zone link changes then the next read follows it`() {
        val directory = Files.createTempDirectory("zone")
        val link = directory.resolve("localtime")
        Files.createSymbolicLink(link, Paths.get("/var/db/timezone/zoneinfo/Europe/Warsaw"))
        assertEquals(ZoneId.of("Europe/Warsaw"), currentSystemZone(link))

        Files.delete(link)
        Files.createSymbolicLink(link, Paths.get("/var/db/timezone/zoneinfo/America/New_York"))
        assertEquals(ZoneId.of("America/New_York"), currentSystemZone(link))

        Files.delete(link)
        assertEquals(ZoneId.systemDefault(), currentSystemZone(link))
    }

    @Test
    fun `given an instant then the local date and minute follow the zone`() {
        assertEquals(LocalMinute(ScheduleDate(2026, 9, 28), 9 * 60), warsaw.localAt(instant("2026-09-28T07:00:00Z")))
        assertEquals(LocalMinute(ScheduleDate(2026, 9, 27), 23 * 60 + 30), warsaw.localAt(instant("2026-09-27T21:30:00Z")))
    }
}
