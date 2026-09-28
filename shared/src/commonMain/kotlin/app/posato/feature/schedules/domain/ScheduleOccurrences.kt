package app.posato.feature.schedules.domain

/** One occurrence is a schedule on the local date it starts. */
internal data class OccurrenceKey(
    val schedule: ScheduleId,
    val date: ScheduleDate,
)

/**
 * Grow-only facts: skips and early ends synchronized between devices, and this device's terminal
 * markers for occurrences it saw end, so a clock rollback or an edit never recreates them.
 */
internal data class ScheduleFacts(
    val skipped: Set<OccurrenceKey> = emptySet(),
    val ended: Set<OccurrenceKey> = emptySet(),
    val terminal: Set<OccurrenceKey> = emptySet(),
) {
    fun stops(key: OccurrenceKey): Boolean {
        return key in skipped || key in ended || key in terminal
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
        val start = zone.instantOf(date, plan.startMinute)
        val endDate = if (plan.endMinute <= plan.startMinute) date.plusDays(1) else date
        val end = minOf(zone.instantOf(endDate, plan.endMinute), start + ScheduleLimits.MAX_OCCURRENCE_MILLIS)
        // A plan wholly inside a spring-forward gap has no time on that day.
        return ScheduleOccurrence(key, plan.name, start, end).takeIf { end > start }
    }
}
