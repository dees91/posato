package app.posato.feature.schedules.host

import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.PauseItems
import app.posato.feature.enforcement.RunningPartItems
import app.posato.feature.enforcement.planPause
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.domain.ScheduleOccurrence
import app.posato.feature.schedules.domain.toBytes
import app.posato.feature.session.data.PART_OCCURRENCE
import app.posato.feature.session.data.RetainedItems
import app.posato.feature.session.data.RetainedPart

/** The request for the running occurrences and how many of their added items wait for room. */
internal class ScheduledComposition(
    val request: EnforcementRequest,
    val notPausedYet: Int,
)

/**
 * The request for the running occurrences: each pauses its own schedule's set and what it already holds, all
 * together until the latest end, within the device's limits. What each occurrence now pauses is kept before the
 * request is applied, so a failed apply errs toward blocking.
 */
internal suspend fun composeScheduledRequest(
    running: List<ScheduleOccurrence>,
    snapshot: ScheduleSnapshot,
    ports: ScheduleHostPorts,
): ScheduledComposition {
    val parts = running.map { occurrence ->
        val setId = snapshot.schedules.firstOrNull { stored -> stored.plan.id == occurrence.key.schedule }?.plan?.setId
        val selection = ports.targets(setId).scheduleSelection()
        val retained = ports.retention?.read(occurrence.retainedPart()) ?: RetainedItems()
        RunningPartItems(
            partId = occurrence.partId(),
            startEpochMillis = occurrence.startEpochMillis,
            endEpochMillis = occurrence.endEpochMillis,
            current = PauseItems(selection.domains.toSet(), selection.mappingIds.toSet()),
            retained = PauseItems(retained.domains, retained.applications.map { kept -> kept.mappingId.toHex() }.toSet()),
        )
    }
    val plan = planPause(parts, ports.limits)
    parts.forEach { part ->
        val held = plan.held[part.partId] ?: return@forEach
        val occurrence = running.first { candidate -> candidate.partId() == part.partId }
        ports.retention?.hold(occurrence.retainedPart(), RetainedItems(held.domains, ports.keptApplications(held.appIds - part.retained.appIds)))
    }
    val first = running.minBy(ScheduleOccurrence::startEpochMillis)
    val date = first.key.date
    val request = EnforcementRequest(
        domains = plan.items.domains.sorted(),
        mappingIds = plan.items.appIds.sorted(),
        sessionId = "schedule-${first.key.schedule.hex}-${date.year}-${date.month}-${date.day}",
        sessionStartEpochMillis = first.startEpochMillis,
        sessionEndEpochMillis = plan.endEpochMillis,
    )
    return ScheduledComposition(request, plan.deferred.values.sum())
}

internal fun ScheduleOccurrence.retainedPart(): RetainedPart {
    val date = key.date
    return RetainedPart(PART_OCCURRENCE, key.schedule.toBytes(), date.year.toLong(), date.month.toLong(), date.day.toLong())
}

private fun ScheduleOccurrence.partId(): String {
    return "${key.schedule.hex}-${key.date.year}-${key.date.month}-${key.date.day}"
}

private fun ByteArray.toHex(): String {
    return joinToString("") { byte -> (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
}

private const val BYTE_MASK: Int = 0xFF
private const val HEX_RADIX: Int = 16
