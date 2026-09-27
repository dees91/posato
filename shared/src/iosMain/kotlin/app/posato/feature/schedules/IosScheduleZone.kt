package app.posato.feature.schedules

import app.posato.feature.schedules.domain.LocalMinute
import app.posato.feature.schedules.domain.OffsetScheduleZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleZone
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.resetSystemTimeZone
import platform.Foundation.systemTimeZone

/**
 * The iPhone's clock and time zone. Foundation caches the system zone, so each call resets it first;
 * travel and time-zone changes then apply at once.
 */
internal class IosScheduleZone : ScheduleZone {
    private val offsets = OffsetScheduleZone { epochMillis ->
        val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND)
        NSTimeZone.systemTimeZone.secondsFromGMTForDate(date) * MILLIS_PER_SECOND.toLong()
    }

    override fun localAt(epochMillis: Long): LocalMinute {
        NSTimeZone.resetSystemTimeZone()
        return offsets.localAt(epochMillis)
    }

    override fun instantOf(
        date: ScheduleDate,
        minuteOfDay: Int,
    ): Long {
        NSTimeZone.resetSystemTimeZone()
        return offsets.instantOf(date, minuteOfDay)
    }

    private companion object {
        const val MILLIS_PER_SECOND: Double = 1_000.0
    }
}
