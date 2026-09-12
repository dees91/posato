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
    private var reconcileJob: Job? = null
    private var transitionJob: Job? = null
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
            when (val read = store.read(nowEpochMillis)) {
                is LocalSessionResult.Failure -> null
                is LocalSessionResult.Success -> read.value
            }
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
            when (val read = store.read(clock.currentEpochMillis())) {
                is LocalSessionResult.Failure -> {
                    read
                }

                is LocalSessionResult.Success -> {
                    settleLocked(read.value)
                    read
                }
            }
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
            when (val fresh = store.read(clock.currentEpochMillis())) {
                is LocalSessionResult.Failure -> {
                    fresh
                }

                is LocalSessionResult.Success -> {
                    settleLocked(fresh.value)
                    fresh
                }
            }
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
            when (val fresh = store.read(clock.currentEpochMillis())) {
                is LocalSessionResult.Failure -> {
                    fresh
                }

                is LocalSessionResult.Success -> {
                    settleLocked(fresh.value)
                    fresh
                }
            }
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
                    when (val read = store.read(clock.currentEpochMillis())) {
                        is LocalSessionResult.Failure -> null
                        is LocalSessionResult.Success -> read.value
                    }
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
                acknowledgeExpired(sessionId)
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
                    mutableView.update { EnforcementViewState() }
                } else {
                    mutableView.update { view ->
                        view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                    }
                }
                settle(banked.value)
            }

            is LocalSessionResult.Failure if banked.reason == LocalSessionFailure.SESSION_NOT_ACTIVE -> {
                // Nothing to bank for this identity; tidy the native record
                // and drain whatever transition is current instead.
                pendingNativeExpiry = null
                acknowledgeExpired(sessionId)
                settleForTag()
            }

            is LocalSessionResult.Failure -> {
                // A failed durable write is never reported as success and
                // never clears restrictions first: park the observation for the
                // next settle instead of settling Active as success.
                pendingNativeExpiry = tag
                mutableView.update { view ->
                    view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                }
            }
        }
    }

    private suspend fun acknowledgeExpired(sessionId: String): Boolean {
        // Best effort: a failed acknowledgement only repeats an idempotent
        // bank on the next observation; the row is already terminal, and a
        // future schedule clears a leftover native record.
        return try {
            enforcement.acknowledgeSuspendedExpiry(sessionId)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            false
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

    private fun settleLocked(status: LocalSessionStatus) {
        lastSettledTag = tagOf(status)
        lastSettledActive = status is LocalSessionStatus.Active
        hasSettled = true
        mutableStatus.value = status
        val active = status as? LocalSessionStatus.Active
        if (active != null) {
            frozenStartSet = active.frozenStartSet
            val tag = SessionTag(active.record)
            if (enforcedIdentity == tag) {
                return
            }
            if (enforcedIdentity == null && mutableView.value.state is EnforcementState.Inactive && !mutableView.value.busy) {
                reconcileActiveSession(tag, active.record, active.frozenStartSet)
            } else if (enforcedIdentity != null) {
                replaceActiveSession(tag, active.record, active.frozenStartSet)
            }
            return
        }
        frozenStartSet = null
        reconcileJob?.cancel()
        reconcileJob = null
        val ended = status as? LocalSessionStatus.Ended
        if (ended != null) {
            if (enforcedIdentity == null && mutableView.value.state is EnforcementState.Inactive && !mutableView.value.busy) {
                return
            }
            clearAfterObservedEnd(SessionTag(ended.record))
        }
    }

    private fun reconcileActiveSession(
        tag: SessionTag,
        record: SessionRecord,
        frozen: FrozenStartSet?,
    ) {
        if (reconcileJob?.isActive == true) {
            return
        }
        reconcileJob = scope.launch {
            try {
                reconcile(tag, record, frozen)
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
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
    }

    private fun replaceActiveSession(
        tag: SessionTag,
        record: SessionRecord,
        frozen: FrozenStartSet?,
    ) {
        if (transitionJob?.isActive == true) {
            return
        }
        transitionJob = scope.launch {
            try {
                portMutex.withLock {
                    enforcement.clear()
                }
                // A tag alone cannot tell a replaced session apart: the desired
                // state is the identity plus its deadline, and only an active
                // row with both still matching is re-applied here.
                val current = stateMutex.withLock {
                    when (val read = store.read(clock.currentEpochMillis())) {
                        is LocalSessionResult.Failure -> null
                        is LocalSessionResult.Success -> read.value as? LocalSessionStatus.Active
                    }
                }
                if (current != null &&
                    current.record.sessionId == record.sessionId &&
                    current.record.endEpochMillis == record.endEpochMillis
                ) {
                    reapplyCurrent(record, tag, frozen)
                }
                // Drain whatever is current after the in-flight transition:
                // a row that moved on reconciles instead of being lost.
                convergeAfterPort(tag)
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
                mutableView.update { view ->
                    view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                }
            }
        }
    }

    private fun clearAfterObservedEnd(tag: SessionTag) {
        if (transitionJob?.isActive == true) {
            return
        }
        transitionJob = scope.launch {
            try {
                clearAfterEnd(tag)
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
                mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
            }
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
            val requested = targets.toEnforcedSet()
            val report = portMutex.withLock {
                enforcement.apply(record.toEnforcementRequest(requested, targets))
            }
            mutableView.update { view -> view.copy(state = report.toActiveState(), enforced = requested) }
            if (report.outcome == EnforcementOutcome.APPLIED) {
                unknownStreak = 0
                enforcedIdentity = tag
            }
            convergeAfterPort(tag)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
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
                    mutableView.update { EnforcementViewState() }
                }

                EnforcementOutcome.UNAVAILABLE,
                EnforcementOutcome.AUTHORIZATION_REQUIRED -> {
                    if (entering.isApplyFailure()) {
                        enforcedIdentity = null
                        mutableView.update { EnforcementViewState() }
                    } else {
                        mutableView.update { view ->
                            view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                        }
                    }
                }

                else -> {
                    mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
                }
            }
            convergeAfterPort(expected)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        } finally {
            mutableView.update { view -> view.copy(busy = false) }
        }
    }

    private suspend fun convergeAfterPort(cleared: SessionTag?) {
        val fresh = stateMutex.withLock {
            when (val read = store.read(clock.currentEpochMillis())) {
                is LocalSessionResult.Failure -> null
                is LocalSessionResult.Success -> read.value
            }
        } ?: return
        val active = fresh as? LocalSessionStatus.Active
        if (active != null && SessionTag(active.record) != cleared) {
            // The clear removed a newer enforcement than intended: reconcile
            // the current desired state directly, so the latest transition
            // drains before this in-flight transition completes.
            reconcile(SessionTag(active.record), active.record, active.frozenStartSet)
        } else if (tagOf(fresh) != cleared) {
            settle(fresh)
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
            mutableView.update { view ->
                if (view.state is EnforcementState.Inactive) {
                    view.copy(state = EnforcementState.Active(false), enforced = frozen?.toEnforcedSet() ?: loadTargets().toEnforcedSet())
                } else {
                    view
                }
            }
        } else if (enforcement.reapplyRequiresPrompt) {
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
        val targets = loadTargets()
        val requested = targets.toEnforcedSet()
        portMutex.withLock {
            enforcement.clear()
        }
        val report = portMutex.withLock {
            enforcement.apply(record.toEnforcementRequest(requested, targets))
        }
        val displayed = frozen?.toEnforcedSet() ?: targets.toEnforcedSet()
        mutableView.update { view -> view.copy(state = report.toActiveState(), enforced = displayed) }
        if (report.outcome == EnforcementOutcome.APPLIED) {
            unknownStreak = 0
            enforcedIdentity = tag
        }
        convergeAfterPort(tag)
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
            when (val read = store.read(clock.currentEpochMillis())) {
                is LocalSessionResult.Failure -> null
                is LocalSessionResult.Success -> read.value
            }
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

private fun tagOf(result: LocalSessionResult<LocalSessionStatus>): SessionTag? {
    return (result as? LocalSessionResult.Success)?.let { tagOf(it.value) }
}

private fun tagOf(status: LocalSessionStatus): SessionTag? {
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
