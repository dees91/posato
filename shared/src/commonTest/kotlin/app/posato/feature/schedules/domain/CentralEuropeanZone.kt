package app.posato.feature.schedules.domain

/**
 * A synthetic zone with the 2026 Central European rules: UTC+1, UTC+2 from 2026-03-29 01:00 UTC
 * (local 02:00 jumps to 03:00), and UTC+1 again from 2026-10-25 01:00 UTC (local 03:00 falls back to
 * 02:00).
 */
internal object CentralEuropeanZone : ScheduleZone {
    private const val MINUTE: Long = 60_000L
    private val springForward = utc(ScheduleDate(2026, 3, 29), 60)
    private val fallBack = utc(ScheduleDate(2026, 10, 25), 60)

    private fun offsetAt(epochMillis: Long): Long {
        return if (epochMillis in springForward until fallBack) 120 * MINUTE else 60 * MINUTE
    }

    override fun localAt(epochMillis: Long): LocalMinute {
        val local = epochMillis + offsetAt(epochMillis)
        val epochDay = local.floorDiv(DAY)
        return LocalMinute(ScheduleDate.ofEpochDay(epochDay), ((local - epochDay * DAY) / MINUTE).toInt())
    }

    override fun instantOf(
        date: ScheduleDate,
        minuteOfDay: Int,
    ): Long {
        val wall = date.epochDay * DAY + minuteOfDay * MINUTE
        val valid = listOf(60 * MINUTE, 120 * MINUTE).map { wall - it }.filter { offsetAt(it) == wall - it }
        return valid.minOrNull() ?: springForward
    }
}

internal const val DAY: Long = 86_400_000L

/** The instant of a UTC wall time. */
internal fun utc(
    date: ScheduleDate,
    minuteOfDay: Int,
): Long {
    return date.epochDay * DAY + minuteOfDay * 60_000L
}
