package app.posato.feature.schedules.domain

/** One break in one occurrence: it releases the occurrence from [startEpochMillis] for [minutes]. */
internal data class ScheduleBreak(
    val startEpochMillis: Long,
    val minutes: Int,
) {
    val endEpochMillis: Long
        get() {
            return startEpochMillis + minutes * MINUTE_MILLIS
        }

    override fun toString(): String {
        return "ScheduleBreak(redacted)"
    }
}

internal object ScheduleBreakRules {
    /** The only lengths a break can have. */
    val MINUTES: Set<Int> = setOf(5, 10, 15)

    /** A break that starts further than this after this device's clock never releases here. */
    const val FUTURE_START_BOUND_MILLIS: Long = 2 * MINUTE_MILLIS
}

private const val MINUTE_MILLIS: Long = 60_000L
