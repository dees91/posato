package app.posato.feature.schedules.host

import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.data.OccurrenceStop
import app.posato.feature.schedules.data.ScheduleHostUpdate
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleOccurrence
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.session.ui.toFrozenStartSet
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the host needs besides the store; each piece is a port so the host is tested without a helper. */
internal class ScheduleHostPorts(
    val claims: PauseClaims,
    val gate: ScheduleStartGate,
    val hadConsent: () -> Boolean,
    val targets: suspend () -> SessionTargetsState,
    val maintenanceClosed: () -> Boolean,
    /** Paused-item changes, so a running pause restricts the current items within moments. */
    val targetChanges: Flow<Unit> = emptyFlow(),
)

/**
 * Starts and ends scheduled pauses on this device while the process runs, with the window open or
 * closed. It evaluates at launch, every wall-clock minute, and whenever plans or facts change; the
 * evaluations run one at a time, and each makes at most one attempt to apply.
 */
internal class ScheduleHost(
    private val store: LocalScheduleStore,
    private val zone: ScheduleZone,
    private val clock: SessionClock,
    private val ports: ScheduleHostPorts,
    private val ticks: Flow<Unit> = minuteTicks(clock),
) : ScheduledPauses {
    private val mutablePause = MutableStateFlow<ScheduledPause?>(null)
    private val triggers = Channel<Unit>(Channel.CONFLATED)
    private val announced = MutableStateFlow<Set<Pair<OccurrenceKey, Int>>>(emptySet())
    private var unknownReads = 0

    private val mutableAnyEnabled = MutableStateFlow(false)

    override val pause: StateFlow<ScheduledPause?> = mutablePause.asStateFlow()

    /** Whether any schedule can start here; Quit asks first while one can. */
    val anyEnabled: StateFlow<Boolean> = mutableAnyEnabled.asStateFlow()

    suspend fun run() {
        coroutineScope {
            launch { store.changes.collect { triggers.trySend(Unit) } }
            launch { ticks.collect { triggers.trySend(Unit) } }
            launch { ports.targetChanges.collect { triggers.trySend(Unit) } }
            triggers.trySend(Unit)
            for (ignored in triggers) {
                // One failed evaluation must never stop the resident process; the next trigger tries again.
                try {
                    evaluate()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    mutablePause.update { pause -> pause?.copy(state = ScheduledPauseState.RETRYING) }
                }
            }
        }
    }

    /** Ends the manual session's partner here: every occurrence running on this device, on every device that runs the same one. */
    override suspend fun endEarly(): Boolean {
        val keys = mutablePause.value?.keys.orEmpty()
        if (keys.isEmpty()) {
            return false
        }
        val now = clock.currentEpochMillis()
        val ended = store.stop(keys, OccurrenceStop.END, zone.localAt(now).date, workspaceId = null)
        triggers.trySend(Unit)
        return ended is ScheduleResult.Success
    }

    override suspend fun markAnnounced(
        keys: Set<OccurrenceKey>,
        bit: Int,
    ) {
        if (keys.isEmpty()) {
            return
        }
        // Kept in memory first, so an evaluation that read the pins before this write cannot announce again.
        announced.update { marks -> marks + keys.map { it to bit } }
        store.recordHost(ScheduleHostUpdate(notices = keys.associateWith { bit }))
        mutablePause.update { pause -> pause?.withoutAnnounced(announced.value) }
    }

    internal suspend fun evaluate() {
        val snapshot = (store.read() as? ScheduleResult.Success)?.value ?: return
        mutableAnyEnabled.value = snapshot.runnable.any { it.enabled }
        val step = ScheduleHostPolicy.step(snapshot, clock.currentEpochMillis(), zone)
        if (!step.update.isEmpty) {
            store.recordHost(step.update)
        }
        if (step.running.isEmpty()) {
            // Idempotent: it clears only a held claim, and retries a clear that failed.
            ports.claims.releaseSchedule()
            unknownReads = 0
            mutablePause.value = null
            return
        }
        val hadConsent = ports.hadConsent()
        // A held claim is kept current: paused items and the latest end may have changed since it was applied.
        val state = if (holdsRestrictions()) apply(step.running) else attempt(step.running)
        mutablePause.value = ScheduleHostPolicy.pause(step.running, step.pins, state, hadConsent)?.withoutAnnounced(announced.value)
    }

    /** A held claim is checked each minute; a helper that lost it, or twice gave no answer, is applied again. */
    private suspend fun holdsRestrictions(): Boolean {
        val status = ports.claims.scheduleStatus()
        unknownReads = if (status == EnforcementOutcome.UNKNOWN) unknownReads + 1 else 0
        if (unknownReads >= UNKNOWN_READS_BEFORE_RETRY) {
            // Restrictions may still hold, so the claim is dropped without clearing; the next apply replaces them.
            ports.claims.forgetSchedule()
            unknownReads = 0
            return false
        }
        return status == EnforcementOutcome.APPLIED || status == EnforcementOutcome.UNKNOWN
    }

    private suspend fun attempt(running: List<ScheduleOccurrence>): ScheduledPauseState {
        if (ports.maintenanceClosed()) {
            return ScheduledPauseState.WAITING
        }
        val gate = ports.gate.check()
        return if (gate == StartGate.READY) apply(running) else gate.toState()
    }

    private suspend fun apply(running: List<ScheduleOccurrence>): ScheduledPauseState {
        val request = running.toRequest(ports.targets())
        if (request.domains.isEmpty() && request.mappingIds.isEmpty()) {
            ports.claims.releaseSchedule()
            return ScheduledPauseState.APPLIED
        }
        return when (ports.claims.updateSchedule(request).outcome) {
            EnforcementOutcome.APPLIED -> ScheduledPauseState.APPLIED

            // The daemon also refuses the grant while another account has the console or the grant read had no answer.
            EnforcementOutcome.AUTHORIZATION_REQUIRED -> ports.gate.check().toState().takeIf { it != ScheduledPauseState.APPLIED }
                ?: ScheduledPauseState.RETRYING

            else -> if (ports.maintenanceClosed()) ScheduledPauseState.WAITING else ScheduledPauseState.RETRYING
        }
    }
}

private fun StartGate.toState(): ScheduledPauseState {
    return when (this) {
        StartGate.READY -> ScheduledPauseState.APPLIED
        StartGate.SETUP_REQUIRED -> ScheduledPauseState.SETUP_REQUIRED
        StartGate.OTHER_ACCOUNT -> ScheduledPauseState.WAITING
        StartGate.TRANSIENT -> ScheduledPauseState.RETRYING
    }
}

private fun ScheduledPause.withoutAnnounced(marks: Set<Pair<OccurrenceKey, Int>>): ScheduledPause {
    return copy(
        unannounced = unannounced.filterNot { (it to ScheduleNotices.STARTED) in marks }.toSet(),
        setupUnannounced = setupUnannounced.filterNot { (it to ScheduleNotices.SETUP_REQUIRED) in marks }.toSet(),
    )
}

/** The earliest-started occurrence names the request; it ends at the latest end. */
internal fun List<ScheduleOccurrence>.toRequest(targets: SessionTargetsState): EnforcementRequest {
    val first = first()
    val frozen = targets.toFrozenStartSet()
    val mappingIds = if (frozen.applicationCount != null) {
        (targets.mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings?.map { it.id.canonicalValue }.orEmpty()
    } else {
        emptyList()
    }
    return EnforcementRequest(
        domains = frozen.domains,
        mappingIds = mappingIds,
        sessionId = "schedule-${first.key.schedule.hex}-${first.key.date.year}-${first.key.date.month}-${first.key.date.day}",
        sessionStartEpochMillis = first.startEpochMillis,
        sessionEndEpochMillis = maxOf { it.endEpochMillis },
    )
}

/** Wakes at each wall-clock minute, sleeping at most a minute of monotonic time, so a wake or a clock change is seen within a minute. */
internal fun minuteTicks(clock: SessionClock): Flow<Unit> {
    return flow {
        while (true) {
            val now = clock.currentEpochMillis()
            delay(minOf(MINUTE_MILLIS - now.mod(MINUTE_MILLIS) + TICK_SLACK_MILLIS, MINUTE_MILLIS))
            emit(Unit)
        }
    }
}

private const val MINUTE_MILLIS: Long = 60_000L
private const val TICK_SLACK_MILLIS: Long = 250L
private const val UNKNOWN_READS_BEFORE_RETRY: Int = 2
