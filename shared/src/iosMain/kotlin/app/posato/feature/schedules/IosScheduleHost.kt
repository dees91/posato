package app.posato.feature.schedules

import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementOutcome
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The schedule's own Managed Settings store; stores combine, so it never lifts a manual session's shields. The
 * monitor's composer decides what it pauses, from the published table and what each occurrence already holds,
 * as the extension does while the app is closed; the request only says that occurrences run.
 */
internal class IosScheduleClaims(
    private val enforcement: IosEnforcement,
    private val monitor: IosScheduleMonitorProvider,
) : ScheduleClaims {
    override suspend fun claimSchedule(request: EnforcementRequest): EnforcementApplyReport {
        val outcome = suspendCoroutine { continuation -> monitor.applySchedule { outcome -> continuation.resume(outcome) } }
        // Nothing to pause: the composer cleared the store, which is what the running occurrences ask for.
        val applied = if (outcome == IosEnforcementOutcome.NOTHING_TO_ENFORCE) EnforcementOutcome.APPLIED else outcome.toOutcome()
        return EnforcementApplyReport(applied, false, false)
    }

    /** Composed again every time: a deferred item may fit now, and the composer writes the store only on a change. */
    override suspend fun updateSchedule(request: EnforcementRequest): EnforcementApplyReport {
        return claimSchedule(request)
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
        val table = ScheduleMonitorTables.build(input, zone)
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
                    setId = schedule.setId,
                )
            },
            running = table.running.map { IosMonitorRunning(it.id, it.date.text(), it.startEpochMillis / MILLIS, it.endEpochMillis / MILLIS) },
            noticesEnabled = notifications.isEnabled(),
            endTitle = getString(Res.string.notification_pause_over_title),
            endBody = getString(Res.string.notification_pause_over_body),
            manualSessionEndEpochSeconds = manualEnd()?.let { it / MILLIS } ?: 0L,
            sets = table.sets.map { set -> IosMonitorSet(set.id, set.domains, set.mappingIds) },
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
