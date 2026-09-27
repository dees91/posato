package app.posato.feature.schedules.ui

import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.StoredSchedule
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleFacts
import app.posato.feature.schedules.domain.ScheduleOccurrence
import app.posato.feature.schedules.domain.ScheduleOccurrences
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionTimeFormat

private val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Builds every row from the stored plans and facts; nothing here is kept between refreshes. */
internal fun buildScheduleRows(
    snapshot: ScheduleSnapshot,
    nowEpochMillis: Long,
    zone: ScheduleZone,
    timeFormat: SessionTimeFormat,
): List<ScheduleRowModel> {
    return snapshot.schedules.map { stored -> stored.toRow(snapshot.facts, nowEpochMillis, zone, timeFormat) }
}

private fun StoredSchedule.toRow(
    facts: ScheduleFacts,
    nowEpochMillis: Long,
    zone: ScheduleZone,
    timeFormat: SessionTimeFormat,
): ScheduleRowModel {
    val next = if (refused) null else ScheduleOccurrences.next(plan, facts, nowEpochMillis, zone)
    // The skipped occurrence is the next one this plan would run if the skips were not there.
    val skipped = if (refused || !plan.enabled) null else nextSkipped(facts, nowEpochMillis, zone, next)
    val endsNextDay = plan.endMinute <= plan.startMinute
    return ScheduleRowModel(
        id = plan.id,
        name = plan.name,
        daysLabel = ScheduleDay.entries.filter { plan.runsOn(it.ordinal) }.joinToString(", ") { WEEKDAY_SHORT[it.ordinal] },
        hoursLabel = "${minuteLabel(plan.startMinute)} - ${minuteLabel(plan.endMinute)}" + if (endsNextDay) " (ends next day)" else "",
        enabled = plan.enabled,
        refused = refused,
        nextRunLabel = next?.let { occurrence -> "Next: ${occurrence.label(nowEpochMillis, timeFormat)}" },
        skippedLabel = skipped?.let { occurrence -> "Skipped: ${occurrence.label(nowEpochMillis, timeFormat)}" },
        canSkip = next != null,
    )
}

private fun StoredSchedule.nextSkipped(
    facts: ScheduleFacts,
    nowEpochMillis: Long,
    zone: ScheduleZone,
    next: ScheduleOccurrence?,
): ScheduleOccurrence? {
    val unskipped = ScheduleOccurrences.next(plan, facts.copy(skipped = emptySet()), nowEpochMillis, zone) ?: return null
    return unskipped.takeIf { it.key in facts.skipped && it.key != next?.key }
}

private fun ScheduleOccurrence.label(
    nowEpochMillis: Long,
    timeFormat: SessionTimeFormat,
): String {
    return "${WEEKDAY_SHORT[key.date.weekday]}, ${timeFormat.formatTime(startEpochMillis, nowEpochMillis)}"
}

/** The key Skip next writes: the plan's next occurrence that is not skipped or ended yet. */
internal fun ScheduleSnapshot.nextKey(
    row: ScheduleRowModel,
    nowEpochMillis: Long,
    zone: ScheduleZone,
): OccurrenceKey? {
    val plan = schedules.firstOrNull { it.plan.id == row.id && !it.refused }?.plan ?: return null
    return ScheduleOccurrences.next(plan, facts, nowEpochMillis, zone)?.key
}
