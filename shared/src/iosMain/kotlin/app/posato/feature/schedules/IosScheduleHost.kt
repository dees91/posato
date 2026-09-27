package app.posato.feature.schedules

import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementOutcome
import app.posato.feature.enforcement.IosEnforcementRequest
import app.posato.feature.enforcement.ScheduleClaims
import app.posato.feature.notifications.SessionNotificationPlatform
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.domain.toSync
import app.posato.feature.schedules.host.ScheduleMonitorInput
import app.posato.feature.schedules.host.ScheduleMonitorTables
import app.posato.feature.schedules.host.ScheduleStartGate
import app.posato.feature.schedules.host.StartGate
import app.posato.feature.schedules.ui.minuteLabel
import app.posato.generated.resources.Res
import app.posato.generated.resources.notification_pause_over_body
import app.posato.generated.resources.notification_pause_over_title
import app.posato.generated.resources.notification_schedule_started_body
import app.posato.generated.resources.notification_schedule_started_title
import org.jetbrains.compose.resources.getString

/** The schedule's own Managed Settings store; stores combine, so it never lifts a manual session's shields. */
internal class IosScheduleClaims(
    private val enforcement: IosEnforcement,
) : ScheduleClaims {
    private var applied: IosEnforcementRequest? = null

    override suspend fun claimSchedule(request: EnforcementRequest): EnforcementApplyReport {
        val wanted = IosEnforcementRequest(request.domains, request.mappingIds)
        val outcome = enforcement.apply(wanted).toOutcome()
        applied = wanted.takeIf { outcome == EnforcementOutcome.APPLIED }
        return EnforcementApplyReport(outcome, false, false)
    }

    /** The store holds items, not a deadline, so only a change of paused items is written again. */
    override suspend fun updateSchedule(request: EnforcementRequest): EnforcementApplyReport {
        return if (applied == IosEnforcementRequest(request.domains, request.mappingIds)) {
            EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
        } else {
            claimSchedule(request)
        }
    }

    /** Clears what the store holds, including shields the monitor applied while the app was closed. */
    override suspend fun releaseSchedule(): EnforcementOutcome {
        return if (enforcement.status() == IosEnforcementOutcome.APPLIED) enforcement.clear().toOutcome() else EnforcementOutcome.CLEARED
    }

    override suspend fun scheduleStatus(): EnforcementOutcome {
        return enforcement.status().toOutcome()
    }

    override suspend fun forgetSchedule(): Unit = Unit
}

/** Screen Time authorization is the consent on iPhone; a phone that cannot enforce never starts a schedule. */
internal class IosScheduleStartGate(
    private val enforcement: IosEnforcement,
) : ScheduleStartGate {
    override suspend fun check(): StartGate {
        return when (enforcement.status()) {
            IosEnforcementOutcome.APPLIED, IosEnforcementOutcome.CLEARED, IosEnforcementOutcome.NOTHING_TO_ENFORCE -> StartGate.READY
            IosEnforcementOutcome.PLATFORM_FAILURE -> StartGate.TRANSIENT
            else -> StartGate.SETUP_REQUIRED
        }
    }
}

internal fun IosEnforcementOutcome.toOutcome(): EnforcementOutcome {
    return when (this) {
        IosEnforcementOutcome.APPLIED -> {
            EnforcementOutcome.APPLIED
        }

        IosEnforcementOutcome.CLEARED -> {
            EnforcementOutcome.CLEARED
        }

        IosEnforcementOutcome.NOTHING_TO_ENFORCE -> {
            EnforcementOutcome.NOTHING_TO_ENFORCE
        }

        IosEnforcementOutcome.AUTHORIZATION_REQUIRED, IosEnforcementOutcome.AUTHORIZATION_DENIED, IosEnforcementOutcome.RESTRICTED -> {
            EnforcementOutcome.AUTHORIZATION_REQUIRED
        }

        IosEnforcementOutcome.UNAVAILABLE -> {
            EnforcementOutcome.UNAVAILABLE
        }

        IosEnforcementOutcome.SELECTION_MISSING, IosEnforcementOutcome.PLATFORM_FAILURE -> {
            EnforcementOutcome.FAILED
        }
    }
}

/**
 * Hands every evaluation's table to the monitor. The Swift side writes the file only when it changed but
 * always reconciles the registrations, so a later Screen Time approval or a failed write is retried.
 */
internal class IosScheduleMonitorPublisher(
    private val provider: IosScheduleMonitorProvider,
    private val zone: ScheduleZone,
    private val notifications: SessionNotificationPlatform,
    private val manualEnd: () -> Long?,
) {
    suspend fun publish(input: ScheduleMonitorInput) {
        val table = ScheduleMonitorTables.build(input, zone, input.targets())
        val bridged = IosScheduleMonitorTable(
            schedules = table.schedules.map { schedule ->
                IosMonitorSchedule(
                    id = schedule.id,
                    weekdays = schedule.weekdays,
                    startMinute = schedule.startMinute,
                    endMinute = schedule.endMinute,
                    stoppedDates = schedule.stoppedDates.map { it.text() },
                    startTitle = getString(Res.string.notification_schedule_started_title),
                    startBody = getString(Res.string.notification_schedule_started_body, schedule.name, minuteLabel(schedule.endMinute)),
                )
            },
            running = table.running.map { IosMonitorRunning(it.id, it.date.text(), it.startEpochMillis / MILLIS, it.endEpochMillis / MILLIS) },
            domains = table.domains,
            mappingIds = table.mappingIds,
            noticesEnabled = notifications.isEnabled(),
            endTitle = getString(Res.string.notification_pause_over_title),
            endBody = getString(Res.string.notification_pause_over_body),
            manualSessionEndEpochSeconds = manualEnd()?.let { it / MILLIS } ?: 0L,
        )
        provider.publish(bridged)
    }

    fun startedOccurrences(): Set<OccurrenceKey> {
        return provider.startedOccurrences().mapNotNull { it.toOccurrenceKey() }.toSet()
    }
}

internal fun ScheduleDate.text(): String {
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun String.toOccurrenceKey(): OccurrenceKey? {
    val (id, date) = split(':').takeIf { it.size == 2 } ?: return null
    val parts = date.split('-').mapNotNull { it.toIntOrNull() }.takeIf { it.size == DATE_PARTS } ?: return null
    val schedule = ScheduleId(id).takeIf { it.toSync() != null } ?: return null
    return OccurrenceKey(schedule, ScheduleDate(parts[0], parts[1], parts[2]))
}

private const val MILLIS: Long = 1_000L
private const val DATE_PARTS: Int = 3
