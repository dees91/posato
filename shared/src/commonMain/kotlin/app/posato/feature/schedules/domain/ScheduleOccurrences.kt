package app.posato.feature.schedules.domain

/** One occurrence is a schedule on the local date it starts. */
internal data class OccurrenceKey(
    val schedule: ScheduleId,
    val date: ScheduleDate,
)

/**
 * Grow-only facts: skips and early ends synchronized between devices, which stop their occurrence for good,
 * and the latest natural end this device observed for an occurrence, which it never runs before again.
 */
internal data class ScheduleFacts(
    val skipped: Set<OccurrenceKey> = emptySet(),
    val ended: Set<OccurrenceKey> = emptySet(),
    val expired: Map<OccurrenceKey, Long> = emptyMap(),
) {
    fun stops(key: OccurrenceKey): Boolean {
        return key in skipped || key in ended
    }
}

/** An occurrence a host saw running: it carries the notices posted for this run and marks it for the update gate. */
internal data class OccurrencePin(
    val key: OccurrenceKey,
    val startEpochMillis: Long,
    val notices: Int = 0,
) {
    override fun toString(): String {
        return "OccurrencePin(redacted)"
    }
}

internal data class ScheduleOccurrence(
    val key: OccurrenceKey,
    val name: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)

/** Session shows one pause: the earliest-started running schedule's name and the latest end. */
internal data class SchedulePause(
    val name: String,
    val endEpochMillis: Long,
    val occurrences: List<ScheduleOccurrence>,
)

/** Every occurrence follows its plan as it is now; an edit that no longer covers the current time stops it. */
internal object ScheduleOccurrences {
    /** Occurrences running at [nowEpochMillis] on this device's clock. */
    fun active(
        plans: List<SchedulePlan>,
        facts: ScheduleFacts,
        nowEpochMillis: Long,
        zone: ScheduleZone,
    ): List<ScheduleOccurrence> {
        val today = zone.localAt(nowEpochMillis).date
        // An occurrence lasts less than a day, so one running now started today or yesterday.
        return plans.filter { it.enabled }.flatMap { plan ->
            listOf(today.plusDays(-1), today).mapNotNull { date -> occurrence(plan, date, facts, zone) }
        }.filter { nowEpochMillis >= it.startEpochMillis && nowEpochMillis < it.endEpochMillis }
            .sortedWith(compareBy({ it.startEpochMillis }, { it.key.schedule.hex }))
    }

    fun pause(
        plans: List<SchedulePlan>,
        facts: ScheduleFacts,
        nowEpochMillis: Long,
        zone: ScheduleZone,
    ): SchedulePause? {
        val running = active(plans, facts, nowEpochMillis, zone)
        val first = running.firstOrNull() ?: return null
        return SchedulePause(first.name, running.maxOf { it.endEpochMillis }, running)
    }

    /** The first occurrence of [plan] that starts after [nowEpochMillis] and is not skipped or ended. */
    fun next(
        plan: SchedulePlan,
        facts: ScheduleFacts,
        nowEpochMillis: Long,
        zone: ScheduleZone,
    ): ScheduleOccurrence? {
        if (!plan.enabled) {
            return null
        }
        val today = zone.localAt(nowEpochMillis).date
        return (0L..ScheduleLimits.MAX_FACT_DAYS_AHEAD.toLong()).asSequence()
            .mapNotNull { offset -> occurrence(plan, today.plusDays(offset), facts, zone) }
            .firstOrNull { it.startEpochMillis > nowEpochMillis }
    }

    /**
     * The interval the plan's times give on [date], whatever its days, on-off state, and facts: it ends on
     * that date, or the next day when the end is at or before the start, and lasts at most 24 hours.
     */
    fun planned(
        plan: SchedulePlan,
        date: ScheduleDate,
        zone: ScheduleZone,
    ): ScheduleOccurrence? {
        val start = zone.instantOf(date, plan.startMinute)
        val endDate = if (plan.endMinute <= plan.startMinute) date.plusDays(1) else date
        val end = minOf(zone.instantOf(endDate, plan.endMinute), start + ScheduleLimits.MAX_OCCURRENCE_MILLIS)
        // A plan wholly inside a spring-forward gap has no time on that day.
        return ScheduleOccurrence(OccurrenceKey(plan.id, date), plan.name, start, end).takeIf { end > start }
    }

    /** After a natural end observed here, the same date runs again only from that end. */
    private fun occurrence(
        plan: SchedulePlan,
        date: ScheduleDate,
        facts: ScheduleFacts,
        zone: ScheduleZone,
    ): ScheduleOccurrence? {
        val key = OccurrenceKey(plan.id, date)
        if (!plan.runsOn(date.weekday) || facts.stops(key)) {
            return null
        }
        val planned = planned(plan, date, zone) ?: return null
        val start = maxOf(planned.startEpochMillis, facts.expired[key] ?: planned.startEpochMillis)
        return planned.copy(startEpochMillis = start).takeIf { start < planned.endEpochMillis }
    }
}
