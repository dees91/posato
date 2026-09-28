package app.posato.feature.schedules.domain

/** One occurrence is a schedule on the local date it starts. */
internal data class OccurrenceKey(
    val schedule: ScheduleId,
    val date: ScheduleDate,
)

internal data class ScheduleFacts(
    val skipped: Set<OccurrenceKey> = emptySet(),
    val ended: Set<OccurrenceKey> = emptySet(),
    val terminal: Set<OccurrenceKey> = emptySet(),
    val expired: List<OccurrenceExpiry> = emptyList(),
) {
    fun stops(key: OccurrenceKey): Boolean {
        return key in skipped || key in ended || key in terminal
    }
}

/**
 * An occurrence a host saw running, with its original start. It keeps running under later edits to
 * the plan's days or start, and ends at the plan's current end time.
 */
internal data class OccurrencePin(
    val key: OccurrenceKey,
    val startEpochMillis: Long,
    val notices: Int = 0,
    val resumedAfter: Long? = null,
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

internal object ScheduleOccurrences {
    /** Occurrences running at [nowEpochMillis] on this device's clock. */
    fun active(
        plans: List<SchedulePlan>,
        facts: ScheduleFacts,
        nowEpochMillis: Long,
        zone: ScheduleZone,
        pins: List<OccurrencePin> = emptyList(),
    ): List<ScheduleOccurrence> {
        val today = zone.localAt(nowEpochMillis).date
        val expiredPins = facts.expired.filter { expiry ->
            expiry.canResume(plans, facts, nowEpochMillis, zone)
        }.map { it.pin() }
        val effectivePins = pins + expiredPins.filter { expiry -> pins.none { it.key == expiry.key } }
        val pinned = effectivePins.filter { pin ->
            pin.resumedAfter == null || nowEpochMillis >= pin.resumedAfter
        }.mapNotNull { pin -> pinnedOccurrence(pin, plans, facts, zone) }
        val pinnedKeys = (pins.map { it.key } + facts.expired.map { it.key }).toSet()
        // An occurrence lasts less than a day, so one running now started today or yesterday.
        val derived = plans.filter { it.enabled }.flatMap { plan ->
            listOf(today.plusDays(-1), today).mapNotNull { date -> occurrence(plan, date, facts, zone) }
        }.filterNot { it.key in pinnedKeys }
        return (pinned + derived).filter { nowEpochMillis >= it.startEpochMillis && nowEpochMillis < it.endEpochMillis }
            .sortedWith(compareBy({ it.startEpochMillis }, { it.key.schedule.hex }))
    }

    fun pause(
        plans: List<SchedulePlan>,
        facts: ScheduleFacts,
        nowEpochMillis: Long,
        zone: ScheduleZone,
        pins: List<OccurrencePin> = emptyList(),
    ): SchedulePause? {
        val running = active(plans, facts, nowEpochMillis, zone, pins)
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
     * A pinned occurrence ignores later edits to the days and the start; it ends at the plan's current
     * end time on its date, or the next day when that end is at or before the pinned start's time.
     */
    fun pinnedOccurrence(
        pin: OccurrencePin,
        plans: List<SchedulePlan>,
        facts: ScheduleFacts,
        zone: ScheduleZone,
    ): ScheduleOccurrence? {
        val plan = plans.firstOrNull { it.id == pin.key.schedule }?.takeIf { it.enabled && !facts.stops(pin.key) } ?: return null
        val startMinute = zone.localAt(pin.startEpochMillis).minuteOfDay
        val endDate = if (plan.endMinute <= startMinute) pin.key.date.plusDays(1) else pin.key.date
        val end = minOf(zone.instantOf(endDate, plan.endMinute), pin.startEpochMillis + ScheduleLimits.MAX_OCCURRENCE_MILLIS)
        return ScheduleOccurrence(pin.key, plan.name, pin.startEpochMillis, end).takeIf { end > pin.startEpochMillis }
    }

    private fun occurrence(
        plan: SchedulePlan,
        date: ScheduleDate,
        facts: ScheduleFacts,
        zone: ScheduleZone,
    ): ScheduleOccurrence? {
        val key = OccurrenceKey(plan.id, date)
        if (!plan.runsOn(date.weekday) || facts.stops(key) || facts.expired.any { it.key == key }) {
            return null
        }
        val start = zone.instantOf(date, plan.startMinute)
        val endDate = if (plan.endMinute <= plan.startMinute) date.plusDays(1) else date
        val end = minOf(zone.instantOf(endDate, plan.endMinute), start + ScheduleLimits.MAX_OCCURRENCE_MILLIS)
        // A plan wholly inside a spring-forward gap has no time on that day.
        return ScheduleOccurrence(key, plan.name, start, end).takeIf { end > start }
    }
}
