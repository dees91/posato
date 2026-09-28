package app.posato.feature.schedules.host

import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.schedules.domain.ScheduleOccurrence
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
     * Only enabled plans the workspace accepted are listed. A date is stopped by a skip, an early end or
     * an occurrence that already ended here, from yesterday until the furthest date a fact may name.
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
        val stopped = (facts.skipped + facts.ended + facts.terminal).filter { it.date in first..last }
        val schedules = input.snapshot.runnable.filter { it.enabled }.map { plan ->
            MonitorSchedule(
                id = plan.id.hex,
                name = plan.name,
                weekdays = plan.weekdays,
                startMinute = plan.startMinute,
                endMinute = plan.endMinute,
                stoppedDates = stopped.filter { it.schedule == plan.id }.map { it.date }.distinct().sorted(),
            )
        }.sortedBy { it.id }
        val running = input.running.map { MonitorRunning(it.key.schedule.hex, it.key.date, it.startEpochMillis, it.endEpochMillis) }
        val selection = targets.scheduleSelection()
        return ScheduleMonitorTable(schedules, running, selection.domains, selection.mappingIds)
    }
}
