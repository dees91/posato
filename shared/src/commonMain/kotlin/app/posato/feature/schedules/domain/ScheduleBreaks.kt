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

    /**
     * Whether this break keeps its occurrence's place against [candidate]: the earliest start wins, and an equal start
     * keeps the break already known, so publishing [candidate] would change nothing.
     */
    fun prevailsOver(candidate: ScheduleBreak): Boolean {
        return startEpochMillis <= candidate.startEpochMillis
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

/**
 * The clock invariant of ADR 0006 (`SCHEDULE-007`), evaluated on this device's own clock. A break releases only
 * inside its own window, never after End break, and never again once this device saw it end.
 */
internal object ScheduleBreakEvaluation {
    /** The break of [key] that releases it at [nowEpochMillis], given the breaks this device already saw end. */
    fun active(
        facts: ScheduleFacts,
        key: OccurrenceKey,
        nowEpochMillis: Long,
        over: Set<OccurrenceKey> = facts.breaksOver,
    ): ScheduleBreak? {
        val taken = facts.breaks[key] ?: return null
        val inWindow = nowEpochMillis >= taken.startEpochMillis && nowEpochMillis < taken.endEpochMillis
        return taken.takeIf { inWindow && key !in facts.breakEnds && key !in over }
    }

    /**
     * Breaks this evaluation sees end for the first time: their window passed, End break ended them, or they start
     * further ahead of this clock than the bound. Each is recorded before restrictions return for it.
     */
    fun newlyOver(
        facts: ScheduleFacts,
        nowEpochMillis: Long,
        since: ScheduleDate,
    ): Set<OccurrenceKey> {
        return facts.breaks.filter { (key, taken) ->
            val over = nowEpochMillis >= taken.endEpochMillis || key in facts.breakEnds ||
                taken.startEpochMillis > nowEpochMillis + ScheduleBreakRules.FUTURE_START_BOUND_MILLIS
            over && key !in facts.breaksOver && key.date >= since
        }.keys
    }

    /** The next instant at which a break of [keys] starts or ends, so the host evaluates exactly then. */
    fun nextChange(
        facts: ScheduleFacts,
        keys: Collection<OccurrenceKey>,
        nowEpochMillis: Long,
        over: Set<OccurrenceKey>,
    ): Long? {
        return keys.mapNotNull { key ->
            val taken = facts.breaks[key]?.takeIf { key !in facts.breakEnds && key !in over } ?: return@mapNotNull null
            when {
                nowEpochMillis < taken.startEpochMillis -> taken.startEpochMillis
                nowEpochMillis < taken.endEpochMillis -> taken.endEpochMillis
                else -> null
            }
        }.minOrNull()
    }
}

private const val MINUTE_MILLIS: Long = 60_000L
