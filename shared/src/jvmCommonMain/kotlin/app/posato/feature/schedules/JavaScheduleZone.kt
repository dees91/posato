package app.posato.feature.schedules

import app.posato.feature.schedules.domain.LocalMinute
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleZone
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The Mac's clock and time zone, read at every call so travel and time-zone changes apply at once.
 * The JVM caches its default zone at the first use, so the current zone comes from the system's
 * `/etc/localtime` link instead.
 */
internal class JavaScheduleZone(
    private val zone: () -> ZoneId = { currentSystemZone() },
) : ScheduleZone {
    override fun localAt(epochMillis: Long): LocalMinute {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDateTime()
        return LocalMinute(ScheduleDate(local.year, local.monthValue, local.dayOfMonth), local.hour * MINUTES_PER_HOUR + local.minute)
    }

    override fun instantOf(
        date: ScheduleDate,
        minuteOfDay: Int,
    ): Long {
        val local = LocalDateTime.of(date.year, date.month, date.day, minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        val rules = zone().rules
        val offsets = rules.getValidOffsets(local)
        // A skipped wall time starts at the change itself; a repeated one uses the offset before the change.
        return if (offsets.isEmpty()) {
            rules.getTransition(local).instant.toEpochMilli()
        } else {
            local.toInstant(offsets.first()).toEpochMilli()
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR: Int = 60
    }
}

/** The zone the system uses now: the `zoneinfo` link target, or the JVM default when it cannot be read. */
internal fun currentSystemZone(localtime: Path = Paths.get("/etc/localtime")): ZoneId {
    return runCatching { ZoneId.of(Files.readSymbolicLink(localtime).toString().substringAfter("zoneinfo/")) }
        .getOrElse { ZoneId.systemDefault() }
}
