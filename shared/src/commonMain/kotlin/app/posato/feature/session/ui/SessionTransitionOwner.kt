package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcedSet
import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.data.SessionExchangeObserver
import app.posato.feature.session.data.SessionSyncTriggers
import app.posato.feature.session.data.SessionWorkspaceCapture
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.sync.bootstrap.SessionReconcileResult
import app.posato.feature.sync.bootstrap.SessionReconciler
import app.posato.feature.sync.bootstrap.SessionSyncAuthoring
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("TooManyFunctions")
internal class SessionTransitionOwner(
    backgroundDispatcher: CoroutineDispatcher,
    private val store: LocalSessionSyncStore,
    private val clock: SessionClock,
    private val enforcement: EnforcementPort,
    private val loadTargets: suspend () -> SessionTargetsState,
    private val triggers: SessionSyncTriggers,
) : SessionExchangeObserver {
    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val stateMutex = Mutex()
    private val portMutex = Mutex()
    private val reconciler = SessionReconciler(store)
    private val authoring = SessionSyncAuthoring(store)
    private val mutableView = MutableStateFlow(EnforcementViewState())
    private val mutableStatus = MutableStateFlow<LocalSessionStatus?>(null)
    private var drainJob: Job? = null
    private var pendingWork: PendingWork? = null
    private var actionTag: SessionTag? = null
    private var tickCounter = 0L
    private var unknownStreak = 0
    private var frozenStartSet: FrozenStartSet? = null
    private var enforcedIdentity: SessionTag? = null
    private var lastSettledTag: SessionTag? = null
    private var lastSettledActive: Boolean = false
    private var hasSettled = false
    private var pendingNativeExpiry: SessionTag? = null

    val view: StateFlow<EnforcementViewState> = mutableView.asStateFlow()
    val status: StateFlow<LocalSessionStatus?> = mutableStatus.asStateFlow()

    override suspend fun onExchange(
        writer: SyncWriter,
        workspace: EstablishedWorkspace,
    ): SyncStatus? {
        val now = clock.currentEpochMillis()
        val result = stateMutex.withLock {
            reconciler.reconcile(writer, workspace, now, authoring, ::captureFrozen)
        }
        return when (result) {
            is SessionReconcileResult.Completed -> {
                settle(result.status)
                null
            }

            is SessionReconcileResult.Halted -> {
                result.status
            }
        }
    }

    suspend fun settle(status: LocalSessionStatus) {
        stateMutex.withLock {
            settleLocked(status)
        }
    }

    fun onForeground() {
        scope.launch {
            stateMutex.withLock {
                settleLocked(readLocked())
            }
        }
    }

    suspend fun onTick(nowEpochMillis: Long) {
        if (hasSettled && lastSettledTag == null) {
            return
        }
        val status = stateMutex.withLock {
            readCurrentLocked(nowEpochMillis)
        } ?: return
        val active = status as? LocalSessionStatus.Active
        // SessionTag covers only identity and deadline, so an Active to Ended
        // transition on the same row needs the kind check to settle and clear.
        if (!hasSettled || tagOf(status) != lastSettledTag || (active != null) != lastSettledActive) {
            settle(status)
        }
        if (active != null) {
            tickCounter += 1
            if (tickCounter % STATUS_POLL_TICKS == 0L) {
                pollNow(active.record)
            }
        }
    }

    suspend fun refresh(): LocalSessionResult<LocalSessionStatus> {
        return stateMutex.withLock {
            readAndSettleLocked()
        }
    }

    suspend fun startSession(
        sessionId: SessionId,
        startEpochMillis: Long,
        endEpochMillis: Long,
        frozen: FrozenStartSet,
    ): LocalSessionResult<LocalSessionStatus> {
        val capture = stateMutex.withLock { triggers.captureWorkspace() }
        val workspaceId = (capture as? SessionWorkspaceCapture.Linked)?.workspaceId?.copyOf()
        // The command time validates the session: resampling the clock here would
        // reject a start whose setup (validation, target load) straddled a clock
        // boundary. The end stays fixed, so elapsed command time only shortens
        // the effective session instead of extending its deadline.
        val committed = stateMutex.withLock {
            store.start(sessionId, startEpochMillis, endEpochMillis, startEpochMillis, frozen, workspaceId)
        }
        // A failed commit changes nothing and settles nothing: the caller
        // reports the failure truthfully instead of a stale fresh read.
        if (committed is LocalSessionResult.Failure) {
            return committed
        }
        val active = (committed as? LocalSessionResult.Success)?.value as? LocalSessionStatus.Active
        if (active != null && (workspaceId != null || capture is SessionWorkspaceCapture.Unknown)) {
            triggers.requestSync()
        }
        // Liveness boundary: a session already over at commit time is recorded
        // terminal without ever applying; its deadline never moves. A failed
        // terminal write keeps the committed row and converges on the next tick.
        val overByCommit = active != null && clock.currentEpochMillis() >= active.record.endEpochMillis
        if (overByCommit) {
            stateMutex.withLock { store.markExpired(sessionId) }
        }
        if (active != null && !overByCommit) {
            applyAfterStart(active.record, SessionTag(active.record))
        }
        return stateMutex.withLock {
            readAndSettleLocked()
        }
    }

    suspend fun endEarly(expectedSessionId: SessionId): LocalSessionResult<LocalSessionStatus> {
        val capture = stateMutex.withLock { triggers.captureWorkspace() }
        val workspaceId = (capture as? SessionWorkspaceCapture.Linked)?.workspaceId?.copyOf()
        val committed = stateMutex.withLock {
            val current = store.read(clock.currentEpochMillis())
            val currentActive = (current as? LocalSessionResult.Success)?.value as? LocalSessionStatus.Active
            if (currentActive != null && currentActive.record.sessionId != expectedSessionId) {
                return@withLock current
            }
            store.endEarly(clock.currentEpochMillis(), workspaceId)
        }
        // A failed commit changes nothing and settles nothing: the caller
        // reports the failure truthfully instead of a stale fresh read.
        if (committed is LocalSessionResult.Failure) {
            return committed
        }
        val ended = (committed as? LocalSessionResult.Success)?.value as? LocalSessionStatus.Ended
        if (ended != null && (workspaceId != null || capture is SessionWorkspaceCapture.Unknown)) {
            triggers.requestSync()
        }
        // No synchronous clear here: the settle below owns the transition, so
        // an end clears exactly once instead of twice (once here, once there).
        return stateMutex.withLock {
            readAndSettleLocked()
        }
    }

    fun retry() {
        if (mutableView.value.busy) {
            return
        }
        mutableView.update { view -> view.copy(busy = true) }
        scope.launch {
            try {
                // Retry re-derives the desired state instead of repeating the last
                // attempt: the row may have been replaced while the port was busy.
                val fresh = stateMutex.withLock {
                    readCurrentLocked(clock.currentEpochMillis())
                }
                val active = fresh as? LocalSessionStatus.Active
                if (active != null && pendingNativeExpiry == SessionTag(active.record)) {
                    // A parked native expiry converges through the bank flow,
                    // never through a re-apply of an expired session.
                    reconcile(SessionTag(active.record), active.record, frozenStartSet)
                } else if (active != null) {
                    reapplyCurrent(active.record, SessionTag(active.record), frozenStartSet)
                } else {
                    clearAfterEnd(fresh?.let(::tagOf))
                }
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
                mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
            } finally {
                mutableView.update { view -> view.copy(busy = false) }
            }
        }
    }

    private suspend fun bankObservedExpiry(
        tag: SessionTag,
        record: SessionRecord,
        sessionId: String,
    ) {
        // Durable handoff: the peek never consumes, so a restart before the
        // bank observes the expiry again. The terminal fact is committed
        // before acting on the signal, and the native record is
        // acknowledged only afterwards: a restart between the two re-banks
        // idempotently instead of losing the expiry.
        val banked = stateMutex.withLock { store.markExpired(record.sessionId) }
        when (banked) {
            is LocalSessionResult.Success -> {
                pendingNativeExpiry = null
                acknowledgeExpired(enforcement, sessionId)
                // The extension already cleared at expiry, so this clear runs even
                // when the view never observed an apply: it converges the device to
                // the expired row instead of leaving restrictions behind.
                val cleared = try {
                    portMutex.withLock { enforcement.clear() }
                } catch (expectedCancellation: CancellationException) {
                    throw expectedCancellation
                } catch (_: Exception) {
                    null
                }
                if (cleared == EnforcementOutcome.CLEARED) {
                    enforcedIdentity = null
                    actionTag = null
                    mutableView.update { EnforcementViewState() }
                } else {
                    actionTag = tag
                    mutableView.update { view ->
                        view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                    }
                }
                settle(banked.value)
            }

            is LocalSessionResult.Failure if banked.reason == LocalSessionFailure.SESSION_NOT_ACTIVE -> {
                // The row moved on, but the observed fact still needs a
                // durable home before its native signal may disappear:
                // record it as a retained marker first and acknowledge only
                // afterwards. A failed record keeps the signal for the next
                // pass instead of losing the only copy of the fact.
                pendingNativeExpiry = null
                if (store.retainExpiryMarker(tag.sessionId) is LocalSessionResult.Success) {
                    acknowledgeExpired(enforcement, sessionId)
                }
                settleForTag()
            }

            is LocalSessionResult.Failure -> {
                // A failed durable write is never reported as success and
                // never clears restrictions first: park the observation for the
                // next settle instead of settling Active as success.
                pendingNativeExpiry = tag
                actionTag = tag
                mutableView.update { view ->
                    view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                }
            }
        }
    }

    fun pollNow(record: SessionRecord) {
        if (mutableView.value.busy || mutableView.value.state !is EnforcementState.Active) {
            return
        }
        scope.launch {
            onTickSecond(record, SessionTag(record))
        }
    }

    suspend fun captureFrozen(): FrozenStartSet {
        return loadTargets().toFrozenStartSet()
    }

    suspend fun close() {
        scope.cancel()
    }

    private suspend fun readLocked(): LocalSessionStatus {
        return when (val read = store.read(clock.currentEpochMillis())) {
            is LocalSessionResult.Failure -> mutableStatus.value ?: LocalSessionStatus.Inactive
            is LocalSessionResult.Success -> read.value
        }
    }

    private suspend fun readAndSettleLocked(): LocalSessionResult<LocalSessionStatus> {
        return when (val fresh = store.read(clock.currentEpochMillis())) {
            is LocalSessionResult.Failure -> {
                fresh
            }

            is LocalSessionResult.Success -> {
                settleLocked(fresh.value)
                fresh
            }
        }
    }

    private suspend fun readCurrentLocked(nowEpochMillis: Long): LocalSessionStatus? {
        return when (val read = store.read(nowEpochMillis)) {
            is LocalSessionResult.Failure -> null
            is LocalSessionResult.Success -> read.value
        }
    }

    private fun settleLocked(status: LocalSessionStatus) {
        lastSettledTag = tagOf(status)
        lastSettledActive = status is LocalSessionStatus.Active
        hasSettled = true
        mutableStatus.value = status
        val active = status as? LocalSessionStatus.Active
        if (active != null) {
            frozenStartSet = active.frozenStartSet
        } else {
            frozenStartSet = null
        }
        // Explicit termination condition: work is queued only while the
        // desired state is not yet converged (see isTransitionConverged). A
        // pass that finds its outcome already confirmed or rendered is a
        // no-op instead of a new job, so the drain below always quiesces
        // instead of self-driving — including persistent port failures.
        if (isTransitionConverged(status, enforcedIdentity, mutableView.value.state, actionTag)) {
            return
        }
        val tag = tagOf(status)
        if (active != null && tag != null) {
            pendingWork = if (enforcedIdentity == null) {
                PendingWork.Reconcile(tag, active.record, active.frozenStartSet)
            } else {
                PendingWork.Replace(tag, active.record, active.frozenStartSet)
            }
        } else {
            val ended = status as? LocalSessionStatus.Ended
            if (ended == null || tag == null) {
                return
            }
            pendingWork = PendingWork.Clear(tag)
        }
        ensureDrain()
    }

    private fun ensureDrain() {
        if (drainJob?.isActive == true) {
            return
        }
        drainJob = scope.launch {
            while (true) {
                val work = stateMutex.withLock {
                    val queued = pendingWork
                    pendingWork = null
                    queued
                } ?: break
                executeWork(work)
            }
        }
    }

    private suspend fun executeWork(work: PendingWork) {
        when (work) {
            is PendingWork.Reconcile -> runReconcile(work.tag, work.record, work.frozen)
            is PendingWork.Replace -> runReplace(work.tag, work.record, work.frozen)
            is PendingWork.Clear -> runClear(work.expected)
        }
    }

    private suspend fun runReconcile(
        tag: SessionTag,
        record: SessionRecord,
        frozen: FrozenStartSet?,
    ) {
        try {
            reconcile(tag, record, frozen)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            actionTag = tag
            mutableView.update { view ->
                if (view.state is EnforcementState.Inactive) {
                    view.copy(
                        state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt),
                        enforced = frozen?.toEnforcedSet() ?: view.enforced,
                    )
                } else {
                    view
                }
            }
        }
    }

    private suspend fun runReplace(
        tag: SessionTag,
        record: SessionRecord,
        frozen: FrozenStartSet?,
    ) {
        try {
            portMutex.withLock {
                enforcement.clear()
            }
            // A tag alone cannot tell a replaced session apart: the desired
            // state is the identity plus its deadline, and only an active
            // row with both still matching is re-applied here.
            val current = stateMutex.withLock {
                readCurrentLocked(clock.currentEpochMillis())
            } as? LocalSessionStatus.Active
            if (current != null &&
                current.record.sessionId == record.sessionId &&
                current.record.endEpochMillis == record.endEpochMillis
            ) {
                reapplyCurrent(record, tag, frozen)
            }
            // Drain whatever is current after the in-flight transition:
            // a row that moved on reconciles instead of being lost.
            convergeAfterPort(stateMutex, store, clock, tag, ::settle, ::reconcile)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            actionTag = tag
            mutableView.update { view ->
                view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt))
            }
        }
    }

    private suspend fun runClear(tag: SessionTag) {
        try {
            clearAfterEnd(tag)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            actionTag = tag
            mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        }
    }

    private suspend fun applyAfterStart(
        record: SessionRecord,
        tag: SessionTag,
    ) {
        val entering = mutableView.value.state
        mutableView.update { view -> view.copy(busy = true) }
        try {
            if (entering is EnforcementState.ActionRequired && entering.kind == EnforcementActionKind.CLEAR_FAILED) {
                portMutex.withLock {
                    enforcement.clear()
                }
            }
            val targets = loadTargets()
            val frozen = targets.toFrozenStartSet()
            frozenStartSet = frozen
            // Pre-apply liveness boundary: loading targets suspends, so the
            // session may have ended while waiting. A spent session is
            // recorded terminal without ever applying; its deadline never
            // moves.
            if (!ensureApplicableBeforeApply(stateMutex, store, clock, tag, record, ::settleForTag)) {
                return
            }
            val requested = targets.toEnforcedSet()
            // A schedule for this session must never silently drop a pending
            // foreign native signal: displace and persist it first.
            persistDisplacedExpiry(enforcement, store, record.sessionId.reconciliationId())
            // Decisive check under operation serialization: the waits above
            // may have outlasted the end, so the row is rechecked immediately
            // before the apply instead of trusting the earlier read.
            val report = portMutex.withLock {
                if (!ensureApplicableBeforeApply(stateMutex, store, clock, tag, record, ::settleForTag)) {
                    null
                } else {
                    enforcement.apply(record.toEnforcementRequest(requested, targets))
                }
            } ?: return
            mutableView.update { view -> view.copy(state = report.toActiveState(), enforced = requested) }
            if (report.outcome == EnforcementOutcome.APPLIED) {
                unknownStreak = 0
                enforcedIdentity = tag
                actionTag = null
            } else {
                actionTag = tag
            }
            convergeAfterPort(stateMutex, store, clock, tag, ::settle, ::reconcile)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            actionTag = tag
            mutableView.update { view ->
                view.copy(
                    state = EnforcementState.ActionRequired(
                        EnforcementActionKind.APPLY_FAILED,
                        enforcement.reapplyRequiresPrompt,
                    ),
                    enforced = loadTargets().toEnforcedSet(),
                )
            }
        } finally {
            mutableView.update { view -> view.copy(busy = false) }
        }
    }

    private suspend fun clearAfterEnd(expected: SessionTag?) {
        val entering = mutableView.value.state
        if (entering !is EnforcementState.Active && entering !is EnforcementState.ActionRequired) {
            return
        }
        frozenStartSet = null
        mutableView.update { view -> view.copy(busy = true) }
        try {
            when (portMutex.withLock { enforcement.clear() }) {
                EnforcementOutcome.CLEARED -> {
                    enforcedIdentity = null
                    actionTag = null
                    mutableView.update { EnforcementViewState() }
                }

                EnforcementOutcome.UNAVAILABLE,
                EnforcementOutcome.AUTHORIZATION_REQUIRED -> {
                    if (entering.isApplyFailure()) {
                        enforcedIdentity = null
                        actionTag = null
                        mutableView.update { EnforcementViewState() }
                    } else {
                        actionTag = expected
                        mutableView.update { view ->
                            view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                        }
                    }
                }

                else -> {
                    actionTag = expected
                    mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
                }
            }
            convergeAfterPort(stateMutex, store, clock, expected, ::settle, ::reconcile)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            actionTag = expected
            mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        } finally {
            mutableView.update { view -> view.copy(busy = false) }
        }
    }

    private suspend fun reconcile(
        tag: SessionTag,
        record: SessionRecord,
        frozen: FrozenStartSet?,
    ) {
        val sessionId = record.sessionId.reconciliationId()
        if (pendingNativeExpiry != null && pendingNativeExpiry != tag) {
            // The row moved on; the stale pending observation is moot. A future
            // schedule clears the leftover native record.
            pendingNativeExpiry = null
        }
        val observed = try {
            enforcement.peekSuspendedExpiry(sessionId)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            false
        } || pendingNativeExpiry == tag
        if (observed) {
            bankObservedExpiry(tag, record, sessionId)
        } else if (enforcement.status() == EnforcementOutcome.APPLIED) {
            enforcedIdentity = tag
            actionTag = null
            mutableView.update { view ->
                if (view.state is EnforcementState.Inactive) {
                    view.copy(state = EnforcementState.Active(false), enforced = frozen?.toEnforcedSet() ?: loadTargets().toEnforcedSet())
                } else {
                    view
                }
            }
        } else if (enforcement.reapplyRequiresPrompt) {
            actionTag = tag
            mutableView.update { view ->
                view.copy(
                    state = EnforcementState.ActionRequired(
                        EnforcementActionKind.RESUME_REQUIRED,
                        repeatsSystemPrompt = true,
                    ),
                    enforced = frozen?.toEnforcedSet() ?: loadTargets().toEnforcedSet(),
                )
            }
        } else {
            reapplyCurrent(record, tag, frozen)
        }
    }

    private suspend fun reapplyCurrent(
        record: SessionRecord,
        tag: SessionTag,
        frozen: FrozenStartSet?,
    ) {
        // Same liveness boundary as the command path: a retry or poll may
        // have waited past the end, so a spent session never re-applies.
        if (!ensureApplicableBeforeApply(stateMutex, store, clock, tag, record, ::settleForTag)) {
            return
        }
        val targets = loadTargets()
        val requested = targets.toEnforcedSet()
        // A schedule for this session must never silently drop a pending
        // foreign native signal: displace and persist it first.
        persistDisplacedExpiry(enforcement, store, record.sessionId.reconciliationId())
        portMutex.withLock {
            enforcement.clear()
        }
        // Decisive check under operation serialization: the waits above may
        // have outlasted the end, so the row is rechecked immediately before
        // the apply instead of trusting the earlier read.
        val report = portMutex.withLock {
            if (!ensureApplicableBeforeApply(stateMutex, store, clock, tag, record, ::settleForTag)) {
                null
            } else {
                enforcement.apply(record.toEnforcementRequest(requested, targets))
            }
        } ?: return
        val displayed = frozen?.toEnforcedSet() ?: targets.toEnforcedSet()
        mutableView.update { view -> view.copy(state = report.toActiveState(), enforced = displayed) }
        if (report.outcome == EnforcementOutcome.APPLIED) {
            unknownStreak = 0
            enforcedIdentity = tag
            actionTag = null
        } else {
            actionTag = tag
        }
        convergeAfterPort(stateMutex, store, clock, tag, ::settle, ::reconcile)
    }

    private suspend fun onTickSecond(
        record: SessionRecord,
        tag: SessionTag,
    ) {
        if (mutableView.value.busy || mutableView.value.state !is EnforcementState.Active) {
            return
        }
        val outcome: EnforcementOutcome
        try {
            outcome = enforcement.status()
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
            return
        }
        if (mutableView.value.busy || mutableView.value.state !is EnforcementState.Active) {
            return
        }
        // The status read above suspends: a replacement may have landed meanwhile.
        val current = stateMutex.withLock { tagOf(store.read(clock.currentEpochMillis())) }
        if (current != tag) {
            settleForTag()
            return
        }
        when (outcome) {
            EnforcementOutcome.APPLIED -> {
                unknownStreak = 0
            }

            EnforcementOutcome.CLEARED -> {
                unknownStreak = 0
                handlePollLoss(record, tag)
            }

            else -> {
                unknownStreak += 1
                if (unknownStreak >= CONSECUTIVE_UNKNOWN_LIMIT) {
                    unknownStreak = 0
                    handlePollLoss(record, tag)
                }
            }
        }
    }

    private suspend fun settleForTag() {
        val fresh = stateMutex.withLock {
            readCurrentLocked(clock.currentEpochMillis())
        } ?: return
        settle(fresh)
    }

    private suspend fun handlePollLoss(
        record: SessionRecord,
        tag: SessionTag,
    ) {
        try {
            if (enforcement.reapplyRequiresPrompt) {
                mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
            } else {
                reapplyCurrent(record, tag, frozenStartSet)
            }
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        }
    }
}

internal sealed interface PendingWork {
    data class Reconcile(
        val tag: SessionTag,
        val record: SessionRecord,
        val frozen: FrozenStartSet?,
    ) : PendingWork

    data class Replace(
        val tag: SessionTag,
        val record: SessionRecord,
        val frozen: FrozenStartSet?,
    ) : PendingWork

    data class Clear(
        val expected: SessionTag,
    ) : PendingWork
}

internal fun tagOf(result: LocalSessionResult<LocalSessionStatus>): SessionTag? {
    return (result as? LocalSessionResult.Success)?.let { tagOf(it.value) }
}

internal fun tagOf(status: LocalSessionStatus): SessionTag? {
    return when (status) {
        is LocalSessionStatus.Active -> SessionTag(status.record)
        is LocalSessionStatus.Ended -> SessionTag(status.record)
        is LocalSessionStatus.Inactive -> null
    }
}

internal data class SessionTag(
    val sessionId: SessionId,
    val endEpochMillis: Long,
) {
    constructor(record: SessionRecord) : this(record.sessionId, record.endEpochMillis)

    override fun toString(): String {
        return "SessionTag(redacted)"
    }
}

internal suspend fun loadSessionTargets(
    policyStore: LocalTargetPolicyStore,
    applicationMappings: LocalApplicationMappings,
): SessionTargetsState {
    val policy = when (val result = policyStore.read()) {
        is LocalPolicyResult.Success -> result.value.policy
        is LocalPolicyResult.Failure -> null
    }
    val mappings = try {
        applicationMappings.load()
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (_: Exception) {
        LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE)
    }
    return SessionTargetsState(policy, mappings)
}

internal fun SessionTargetsState.toEnforcedSet(): EnforcedSet {
    val policy = this.policy ?: return EnforcedSet()
    val mappings = (mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings
    return EnforcedSet(
        domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList(),
        applicationCount = mappings?.size,
    )
}

internal fun SessionTargetsState.toFrozenStartSet(): FrozenStartSet {
    val policy = this.policy ?: return FrozenStartSet(persistentListOf(), null)
    val mappings = (mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings
    return FrozenStartSet(
        domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList(),
        applicationCount = mappings?.size,
    )
}

internal fun FrozenStartSet.toEnforcedSet(): EnforcedSet {
    return EnforcedSet(domains = domains, applicationCount = applicationCount)
}

internal fun SessionRecord.toEnforcementRequest(
    frozen: EnforcedSet,
    targets: SessionTargetsState,
): EnforcementRequest {
    val mappingIds = if (frozen.applicationCount != null) {
        val mappings = targets.mappings as? LocalApplicationMappingsLoadResult.Success
        mappings?.snapshot?.mappings?.map { mapping -> mapping.id.canonicalValue }.orEmpty()
    } else {
        emptyList()
    }
    return EnforcementRequest(
        domains = frozen.domains,
        mappingIds = mappingIds,
        sessionId = sessionId.reconciliationId(),
        sessionStartEpochMillis = startEpochMillis,
        sessionEndEpochMillis = endEpochMillis,
    )
}

internal fun EnforcementApplyReport.toActiveState(): EnforcementState {
    return when (outcome) {
        EnforcementOutcome.APPLIED -> EnforcementState.Active(belowPlatformMinimum)
        EnforcementOutcome.NOTHING_TO_ENFORCE -> EnforcementState.Active(false)
        else -> EnforcementState.ActionRequired(EnforcementActionKind.APPLY_FAILED, repeatsSystemPrompt)
    }
}

internal fun EnforcementActionKind.toAction(repeatsSystemPrompt: Boolean): EnforcementState.ActionRequired {
    return EnforcementState.ActionRequired(this, repeatsSystemPrompt)
}

internal fun EnforcementState.isApplyFailure(): Boolean {
    return this is EnforcementState.ActionRequired && kind == EnforcementActionKind.APPLY_FAILED
}

private const val STATUS_POLL_TICKS: Long = 15L
private const val CONSECUTIVE_UNKNOWN_LIMIT: Int = 3
