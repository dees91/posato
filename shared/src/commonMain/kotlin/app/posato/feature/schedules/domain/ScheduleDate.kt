package app.posato.feature.schedules.domain

/** A local calendar date on the proleptic Gregorian calendar, independent of any time zone. */
internal data class ScheduleDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<ScheduleDate> {
    /** Days since 1970-01-01. */
    val epochDay: Long
        get() {
            // Days from civil, after Howard Hinnant's algorithm.
            val shifted = if (month <= 2) year - 1L else year.toLong()
            val era = shifted.floorDiv(YEARS_PER_ERA)
            val yearOfEra = shifted - era * YEARS_PER_ERA
            val monthIndex = if (month > 2) month - 3 else month + 9
            val dayOfYear = (153L * monthIndex + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * DAYS_PER_ERA + dayOfEra - EPOCH_SHIFT
        }

    /** 0 for Monday through 6 for Sunday, matching the schedule weekday mask. */
    val weekday: Int
        get() {
            // 1970-01-01 was a Thursday.
            return (epochDay + 3).mod(7L).toInt()
        }

    fun plusDays(days: Long): ScheduleDate {
        return ofEpochDay(epochDay + days)
    }

    override fun compareTo(other: ScheduleDate): Int {
        return epochDay.compareTo(other.epochDay)
    }

    companion object {
        private const val YEARS_PER_ERA: Long = 400
        private const val DAYS_PER_ERA: Long = 146_097
        private const val EPOCH_SHIFT: Long = 719_468

        fun ofEpochDay(epochDay: Long): ScheduleDate {
            val shifted = epochDay + EPOCH_SHIFT
            val era = shifted.floorDiv(DAYS_PER_ERA)
            val dayOfEra = shifted - era * DAYS_PER_ERA
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val monthIndex = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * monthIndex + 2) / 5 + 1).toInt()
            val month = (if (monthIndex < 10) monthIndex + 3 else monthIndex - 9).toInt()
            val year = (yearOfEra + era * YEARS_PER_ERA + if (month <= 2) 1 else 0).toInt()
            return ScheduleDate(year, month, day)
        }
    }
}

/** A wall-clock minute on a local date. */
internal data class LocalMinute(
    val date: ScheduleDate,
    val minuteOfDay: Int,
)

/**
 * The device's clock and time zone. A wall time that a spring-forward change skips resolves to the
 * first valid instant after it; a wall time that a fall-back change repeats resolves to its first
 * instance.
 */
internal interface ScheduleZone {
    fun localAt(epochMillis: Long): LocalMinute

    fun instantOf(
        date: ScheduleDate,
        minuteOfDay: Int,
    ): Long
}
