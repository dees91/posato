package app.posato.feature.schedules.domain

/** The 16-byte schedule identifier as lowercase hexadecimal. */
internal data class ScheduleId(
    val hex: String,
)

internal enum class SchedulePlanProblem {
    NAME_LENGTH,
    NO_WEEKDAY,
    MINUTE_RANGE,
    SAME_TIMES,
    TOO_SHORT,
}

/**
 * A named weekly plan. The weekdays name the day an occurrence starts (bit 0 is Monday). An end at
 * or before the start ends on the next day.
 */
internal data class SchedulePlan(
    val id: ScheduleId,
    val name: String,
    val weekdays: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean,
) {
    override fun toString(): String {
        return "SchedulePlan(redacted)"
    }

    fun runsOn(weekday: Int): Boolean {
        return weekdays and (1 shl weekday) != 0
    }

    fun problem(): SchedulePlanProblem? {
        val length = (endMinute - startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return when {
            name.encodeToByteArray().size !in 1..ScheduleLimits.MAX_NAME_BYTES -> SchedulePlanProblem.NAME_LENGTH
            weekdays !in 1..ALL_WEEKDAYS -> SchedulePlanProblem.NO_WEEKDAY
            startMinute !in 0 until MINUTES_PER_DAY || endMinute !in 0 until MINUTES_PER_DAY -> SchedulePlanProblem.MINUTE_RANGE
            length == 0 -> SchedulePlanProblem.SAME_TIMES
            length < ScheduleLimits.MIN_MINUTES -> SchedulePlanProblem.TOO_SHORT
            else -> null
        }
    }
}

internal const val MINUTES_PER_DAY: Int = 1440
private const val ALL_WEEKDAYS: Int = 0b1111111

internal object ScheduleLimits {
    const val MAX_SCHEDULES: Int = 10
    const val MAX_NAME_BYTES: Int = 80
    const val MIN_MINUTES: Int = 15
    const val MAX_FACT_DAYS_AHEAD: Int = 400
    const val MAX_OCCURRENCE_MILLIS: Long = 24L * 60 * 60 * 1000
}
