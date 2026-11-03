package app.posato.feature.enforcement

import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.targets.domain.WebsiteWindow

/** The device's wall clock as windows read it: hour * 60 + minute in the current zone, never minutes since midnight. */
internal object WindowClock {
    fun wallMinute(
        epochMillis: Long,
        zone: ScheduleZone,
    ): Int {
        return zone.localAt(epochMillis).minuteOfDay
    }

    fun isOpen(
        rule: WebsiteWindow,
        epochMillis: Long,
        zone: ScheduleZone,
    ): Boolean {
        return rule.isOpenAt(wallMinute(epochMillis, zone))
    }

    /**
     * The first instant after [epochMillis] at which [rule]'s openness differs from its openness at [epochMillis].
     * The reading changes only at whole minutes, so without a clock change in between the answer is the next
     * aligned start or end; across a daylight saving change the minutes are read one by one, so a skipped window
     * never occurs and a repeated one opens again.
     */
    fun nextChange(
        rule: WebsiteWindow,
        epochMillis: Long,
        zone: ScheduleZone,
    ): Long {
        val minuteStart = epochMillis - epochMillis.mod(MINUTE_MILLIS)
        val open = isOpen(rule, epochMillis, zone)
        val phase = wallMinute(epochMillis, zone) % rule.periodMinutes
        val minutes = if (open) rule.lengthMinutes - phase else rule.periodMinutes - phase
        val candidate = minuteStart + minutes * MINUTE_MILLIS
        if (offsetMinutes(minuteStart, zone) == offsetMinutes(candidate, zone) && isOpen(rule, candidate, zone) != open) {
            return candidate
        }
        var probe = minuteStart + MINUTE_MILLIS
        repeat(MAX_SCAN_MINUTES) {
            if (isOpen(rule, probe, zone) != open) {
                return probe
            }
            probe += MINUTE_MILLIS
        }
        return probe
    }

    private fun offsetMinutes(
        epochMillis: Long,
        zone: ScheduleZone,
    ): Long {
        val local = zone.localAt(epochMillis)
        return local.date.epochDay * MINUTES_PER_DAY + local.minuteOfDay - epochMillis.floorDiv(MINUTE_MILLIS)
    }
}

private const val MINUTE_MILLIS: Long = 60_000L
private const val MINUTES_PER_DAY: Long = 1_440L

/** Two days of minutes: further than any clock change can push the next change of a rule. */
private const val MAX_SCAN_MINUTES: Int = 2 * 1_440
