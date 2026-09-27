package app.posato.feature.schedules.domain

/**
 * A [ScheduleZone] built only from the zone's offset at an instant, for platforms whose calendar API
 * does not expose gap and overlap resolution directly. A skipped wall time resolves to the change
 * itself (found by bisection); a repeated one to its first instance.
 */
internal class OffsetScheduleZone(
    private val offsetAt: (epochMillis: Long) -> Long,
) : ScheduleZone {
    override fun localAt(epochMillis: Long): LocalMinute {
        val local = epochMillis + offsetAt(epochMillis)
        val epochDay = local.floorDiv(DAY_MILLIS)
        return LocalMinute(ScheduleDate.ofEpochDay(epochDay), ((local - epochDay * DAY_MILLIS) / MINUTE_MILLIS).toInt())
    }

    override fun instantOf(
        date: ScheduleDate,
        minuteOfDay: Int,
    ): Long {
        val wall = date.epochDay * DAY_MILLIS + minuteOfDay * MINUTE_MILLIS
        // At most one change lies within a day of any wall time, so these are the offsets on either side of it.
        val before = offsetAt(wall - SEARCH_MILLIS)
        val after = offsetAt(wall + SEARCH_MILLIS)
        val valid = listOf(before, after).map { offset -> wall - offset }.filter { candidate -> wall - offsetAt(candidate) == candidate }
        return valid.minOrNull() ?: firstInstantWithOffset(after, wall - after, wall - before)
    }

    /** The change inside a skipped wall time: the first instant in [low, high] whose offset is already [target]. */
    private fun firstInstantWithOffset(
        target: Long,
        low: Long,
        high: Long,
    ): Long {
        var lower = low
        var upper = high
        while (lower < upper) {
            val middle = lower + (upper - lower) / 2
            if (offsetAt(middle) == target) upper = middle else lower = middle + 1
        }
        return upper
    }

    private companion object {
        const val MINUTE_MILLIS: Long = 60_000L
        const val DAY_MILLIS: Long = 86_400_000L
        const val SEARCH_MILLIS: Long = 26 * 60 * MINUTE_MILLIS
    }
}
