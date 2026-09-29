package app.posato.feature.schedules.host

import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.schedules.domain.ScheduleOccurrence
import app.posato.feature.schedules.domain.ScheduleOccurrences
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.ui.SessionTargetsState

/** What an evaluation hands to the process that starts schedules while the app is closed. */
internal class ScheduleMonitorInput(
    val snapshot: ScheduleSnapshot,
    val running: List<ScheduleOccurrence>,
    val nowEpochMillis: Long,
    val targets: suspend () -> SessionTargetsState,
) {
    override fun toString(): String {
        return "ScheduleMonitorInput(redacted)"
    }
}

internal data class MonitorSchedule(
    val id: String,
    val name: String,
    val weekdays: Int,
    val startMinute: Int,
    val endMinute: Int,
    val stoppedDates: List<ScheduleDate>,
) {
    override fun toString(): String {
        return "MonitorSchedule(redacted)"
    }
}

internal data class MonitorRunning(
    val id: String,
    val date: ScheduleDate,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)

/** The schedule table a monitor reads: enabled plans with their stopped dates, running occurrences, and the paused items. */
internal data class ScheduleMonitorTable(
    val schedules: List<MonitorSchedule>,
    val running: List<MonitorRunning>,
    val domains: List<String>,
    val mappingIds: List<String>,
) {
    override fun toString(): String {
        return "ScheduleMonitorTable(redacted)"
    }
}

internal object ScheduleMonitorTables {
    /**
     * Only enabled plans the workspace accepted are listed. A date is stopped by a skip or an early end, and by
     * a natural end observed here that its current interval starts before, from yesterday until the furthest
     * date a fact may name. A running occurrence carries its own start, which may be that observed end.
     */
    fun build(
        input: ScheduleMonitorInput,
        zone: ScheduleZone,
        targets: SessionTargetsState,
    ): ScheduleMonitorTable {
        val today = zone.localAt(input.nowEpochMillis).date
        val first = today.plusDays(-1)
        val last = today.plusDays(ScheduleLimits.MAX_FACT_DAYS_AHEAD.toLong())
        val facts = input.snapshot.facts
        val schedules = input.snapshot.runnable.filter { it.enabled }.map { plan ->
            val cutShort = facts.expired.filter { (key, end) ->
                key.schedule == plan.id && (ScheduleOccurrences.planned(plan, key.date, zone)?.let { it.startEpochMillis < end } ?: false)
            }.keys
            val stopped = (facts.skipped + facts.ended + cutShort).filter { it.schedule == plan.id && it.date in first..last }
            MonitorSchedule(
                id = plan.id.hex,
                name = plan.name,
                weekdays = plan.weekdays,
                startMinute = plan.startMinute,
                endMinute = plan.endMinute,
                stoppedDates = stopped.map { it.date }.distinct().sorted(),
            )
        }.sortedBy { it.id }
        val running = input.running.map { occurrence ->
            MonitorRunning(occurrence.key.schedule.hex, occurrence.key.date, occurrence.startEpochMillis, occurrence.endEpochMillis)
        }
        val selection = targets.scheduleSelection()
        return ScheduleMonitorTable(schedules, running, selection.domains, selection.mappingIds)
    }
}
