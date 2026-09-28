package app.posato.feature.schedules.domain

internal data class OccurrenceExpiry(
    val key: OccurrenceKey,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val endMinute: Int,
    val notices: Int = 0,
) {
    fun pin(): OccurrencePin {
        return OccurrencePin(key, startEpochMillis, notices, endEpochMillis)
    }

    fun canResume(
        plans: List<SchedulePlan>,
        facts: ScheduleFacts,
        nowEpochMillis: Long,
        zone: ScheduleZone,
    ): Boolean {
        val plan = plans.firstOrNull { it.id == key.schedule } ?: return false
        if (plan.endMinute == endMinute || nowEpochMillis < endEpochMillis) {
            return false
        }
        val occurrence = ScheduleOccurrences.pinnedOccurrence(pin(), plans, facts, zone) ?: return false
        return occurrence.endEpochMillis > endEpochMillis && nowEpochMillis < occurrence.endEpochMillis
    }
}
