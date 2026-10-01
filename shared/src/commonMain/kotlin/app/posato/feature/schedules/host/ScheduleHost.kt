package app.posato.feature.schedules.host

import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.enforcement.ScheduleClaims
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.data.OccurrenceStop
import app.posato.feature.schedules.data.ScheduleHostUpdate
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleOccurrence
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.data.PartRetentionStore
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.session.ui.toFrozenStartSet
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.data.KeptApplication
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
    val claims: ScheduleClaims,
    val gate: ScheduleStartGate,
    val hadConsent: () -> Boolean,
    /** The websites and this device's apps of a set; null is the default set. */
    val targets: suspend (PauseSetId?) -> SessionTargetsState,
    val maintenanceClosed: () -> Boolean,
    /** Paused-item changes, so a running pause restricts the current items within moments. */
    val targetChanges: Flow<Unit> = emptyFlow(),
    /** False where another process announces starts, such as the iPhone's monitor extension. */
    val announcesStarts: Boolean = true,
    /** Keys whose start another process already announced. */
    val announcedElsewhere: suspend () -> Set<OccurrenceKey> = { emptySet() },
    /** How much this device can pause at once. */
    val limits: PauseLimits = PauseLimits.MAC,
    /** What a running occurrence needs to keep pausing chosen apps after they leave every set (Mac only). */
    val keptApplications: suspend (Set<String>) -> List<KeptApplication> = { emptyList() },
    /** What each running occurrence has paused on this device; null keeps no retention. */
    val retention: PartRetentionStore? = null,
    /** Hands the plans, facts and running occurrences to whatever starts schedules while the app is closed. */
    val publish: suspend (ScheduleMonitorInput) -> Unit = {},
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

    override suspend fun run() {
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

    override val ownsNotices: Boolean
        get() = ports.announcesStarts

    override fun refresh() {
        triggers.trySend(Unit)
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
        val now = clock.currentEpochMillis()
        val step = ScheduleHostPolicy.step(snapshot, now, zone)
        if (!step.update.isEmpty) {
            store.recordHost(step.update)
        }
        if (step.update.released.isNotEmpty()) {
            // A run that stopped is announced again when it starts again.
            announced.update { marks -> marks.filterNot { it.first in step.update.released }.toSet() }
        }
        val observed = (snapshot.facts.expired.keys + step.update.expired.keys - step.update.forgotten).associateWith { key ->
            maxOf(snapshot.facts.expired[key] ?: Long.MIN_VALUE, step.update.expired[key] ?: Long.MIN_VALUE)
        }
        val published = ScheduleMonitorInput(
            snapshot.copy(facts = snapshot.facts.copy(expired = observed), pins = step.pins),
            step.running,
            now,
            { ports.targets(null) },
        )
        if (step.running.isEmpty()) {
            // Idempotent: it clears only a held claim, and retries a clear that failed. It runs before the monitor
            // sees the new table, so a pause an edit stopped is not reported as over.
            ports.claims.releaseSchedule()
            ports.publish(published)
            unknownReads = 0
            mutablePause.value = null
            return
        }
        ports.publish(published)
        recordAnnouncedElsewhere(step.running.map { it.key }.toSet())
        val hadConsent = ports.hadConsent()
        // A held claim is kept current: paused items and the latest end may have changed since it was applied.
        val state = if (holdsRestrictions()) apply(step.running, snapshot) else attempt(step.running, snapshot)
        val pause = ScheduleHostPolicy.pause(step.running, step.pins, state, hadConsent)?.withoutAnnounced(announced.value)
        mutablePause.value = if (ports.announcesStarts) pause else pause?.copy(unannounced = emptySet())
    }

    /** Starts another process announced are marked like the host's own, so a relaunch never announces them. */
    private suspend fun recordAnnouncedElsewhere(running: Set<OccurrenceKey>) {
        val elsewhere =
            ports.announcedElsewhere().intersect(running) - announced.value.filter { it.second == ScheduleNotices.STARTED }.map { it.first }.toSet()
        if (elsewhere.isNotEmpty()) {
            markAnnounced(elsewhere, ScheduleNotices.STARTED)
        }
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

    private suspend fun attempt(
        running: List<ScheduleOccurrence>,
        snapshot: ScheduleSnapshot,
    ): ScheduledPauseState {
        if (ports.maintenanceClosed()) {
            return ScheduledPauseState.WAITING
        }
        val gate = ports.gate.check()
        return if (gate == StartGate.READY) apply(running, snapshot) else gate.toState()
    }

    private suspend fun apply(
        running: List<ScheduleOccurrence>,
        snapshot: ScheduleSnapshot,
    ): ScheduledPauseState {
        val request = composeScheduledRequest(running, snapshot, ports)
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

/** The paused items a scheduled pause restricts: the websites, and the apps while apps are paused. */
internal class ScheduleSelection(
    val domains: List<String>,
    val mappingIds: List<String>,
)

internal fun SessionTargetsState.scheduleSelection(): ScheduleSelection {
    val frozen = toFrozenStartSet()
    val mappingIds = if (frozen.applicationCount != null) {
        (mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings?.map { it.id.canonicalValue }.orEmpty()
    } else {
        emptyList()
    }
    return ScheduleSelection(frozen.domains, mappingIds)
}

/** The earliest-started occurrence names the request; it ends at the latest end. */
internal fun List<ScheduleOccurrence>.toRequest(targets: SessionTargetsState): EnforcementRequest {
    val first = first()
    val selection = targets.scheduleSelection()
    val date = first.key.date
    return EnforcementRequest(
        domains = selection.domains,
        mappingIds = selection.mappingIds,
        sessionId = "schedule-${first.key.schedule.hex}-${date.year}-${date.month}-${date.day}",
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
