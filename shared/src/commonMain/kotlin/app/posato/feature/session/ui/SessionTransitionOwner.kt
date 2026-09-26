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
import app.posato.feature.sync.bootstrap.reconcileSessionTime
import app.posato.feature.sync.domain.SessionCandidate
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SessionReplicaSnapshot
import app.posato.feature.sync.domain.SyncReducer
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

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
    private val transitions = Channel<Unit>(Channel.CONFLATED)
    private val idleWakeups = Channel<Unit>(Channel.CONFLATED)
    private val drainJob = scope.launch(start = CoroutineStart.LAZY) {
        for (ignored in transitions) {
            executeWork()
        }
    }
    private var actionTag: SessionTag? = null
    private var tickingJob: Job? = null
    private var replicaSnapshot: SessionReplicaSnapshot? = null
    private var tickCounter = 0L
    private var unknownStreak = 0
    private var frozenStartSet: FrozenStartSet? = null
    private var enforcedIdentity: SessionTag? = null
    private var confirmedClear = false
    private var lastSettledTag: SessionTag? = null
    private var lastSettledActive: Boolean = false
    private var hasSettled = false
    private var pendingNativeExpiry: SessionTag? = null

    val view: StateFlow<EnforcementViewState> = mutableView.asStateFlow()
    val status: StateFlow<LocalSessionStatus?> = mutableStatus.asStateFlow()

    override suspend fun onReplicaSnapshot(snapshot: SessionReplicaSnapshot?) {
        stateMutex.withLock {
            val previous = replicaSnapshot
            if (snapshot != null && previous != null) {
                if (previous.context == snapshot.context && snapshot.revision < previous.revision) return@withLock
            }
            replicaSnapshot = snapshot
        }
        idleWakeups.trySend(Unit)
    }

    suspend fun runWhileHosted(idleRecheckMillis: Long? = null) {
        val running = currentCoroutineContext().job
        check(tickingJob?.isActive != true)
        tickingJob = running
        try {
            triggers.restoreSessions()
            while (currentCoroutineContext().isActive) {
                onTick(clock.currentEpochMillis())
                val idleWait = idleRecheckMillis?.let { recheck -> idleWaitMillis(recheck) }
                if (idleWait == null) {
                    delay(SESSION_TICK_MILLIS)
                } else {
                    withTimeoutOrNull(idleWait) { idleWakeups.receive() }
                }
            }
        } finally {
            if (tickingJob === running) tickingJob = null
        }
    }

    private suspend fun idleWaitMillis(recheckMillis: Long): Long? {
        if (mutableStatus.value is LocalSessionStatus.Active) return null
        val snapshot = stateMutex.withLock { replicaSnapshot } ?: return recheckMillis
        val now = clock.currentEpochMillis()
        return when (val candidate = SyncReducer.describeSession(snapshot.projection, now, snapshot.terminalExpiryFacts)) {
            is SessionCandidate.Current -> null
            is SessionCandidate.Future -> (candidate.start.startEpochMillis - now).coerceIn(0L, recheckMillis)
            else -> recheckMillis
        }
    }

    override suspend fun onExchange(
        writer: SyncWriter,
        workspace: EstablishedWorkspace,
    ): SyncStatus? {
        val now = clock.currentEpochMillis()
        val result = stateMutex.withLock {
            val reconciled = reconciler.reconcile(writer, workspace, now, authoring, ::captureFrozen)
            replicaSnapshot = writer.sessionSnapshot
            reconciled
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
        idleWakeups.trySend(Unit)
    }

    fun onForeground() {
        idleWakeups.trySend(Unit)
        scope.launch {
            onTick(clock.currentEpochMillis())
        }
    }

    suspend fun onTick(nowEpochMillis: Long) {
        val status = stateMutex.withLock { readDesiredLocked(nowEpochMillis) } ?: return
        val active = status as? LocalSessionStatus.Active
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
        val committed = stateMutex.withLock {
            store.start(sessionId, startEpochMillis, endEpochMillis, startEpochMillis, frozen, workspaceId)
        }
        if (committed is LocalSessionResult.Failure) {
            return committed
        }
        val active = (committed as? LocalSessionResult.Success)?.value as? LocalSessionStatus.Active
        if (active != null && (workspaceId != null || capture is SessionWorkspaceCapture.Unknown)) {
            triggers.requestSync()
        }
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
        if (committed is LocalSessionResult.Failure) {
            return committed
        }
        val ended = (committed as? LocalSessionResult.Success)?.value as? LocalSessionStatus.Ended
        if (ended != null && (workspaceId != null || capture is SessionWorkspaceCapture.Unknown)) {
            triggers.requestSync()
        }
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
                val fresh = stateMutex.withLock {
                    readDesiredLocked(clock.currentEpochMillis())
                }
                if (fresh == null) return@launch
                val active = fresh as? LocalSessionStatus.Active
                if (active != null && pendingNativeExpiry == SessionTag(active.record)) {
                    reconcile(SessionTag(active.record), active.record, frozenStartSet)
                } else if (active != null) {
                    reapplyCurrent(active.record, SessionTag(active.record), frozenStartSet)
                } else {
                    clearAfterEnd(tagOf(fresh))
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
        val banked = stateMutex.withLock { store.markExpired(record.sessionId) }
        when (banked) {
            is LocalSessionResult.Success -> {
                pendingNativeExpiry = null
                acknowledgeExpired(enforcement, sessionId)
                clearAfterEnd(tag)
                settle(banked.value)
            }

            is LocalSessionResult.Failure if banked.reason == LocalSessionFailure.SESSION_NOT_ACTIVE -> {
                pendingNativeExpiry = null
                if (store.retainExpiryMarker(tag.sessionId) is LocalSessionResult.Success) {
                    acknowledgeExpired(enforcement, sessionId)
                }
                settleForTag()
            }

            is LocalSessionResult.Failure -> {
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
        tickingJob?.cancel()
        scope.cancel()
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

    private suspend fun readDesiredLocked(nowEpochMillis: Long): LocalSessionStatus? {
        val snapshot = replicaSnapshot
        val capture = if (snapshot != null) triggers.captureWorkspace() else null
        val matchesWorkspace = capture is SessionWorkspaceCapture.Linked &&
            capture.workspaceId.contentEquals(snapshot?.context?.workspaceId?.value?.copyBytes())
        return if (snapshot != null && matchesWorkspace) {
            when (val result = reconcileSessionTime(store, snapshot, nowEpochMillis, ::captureFrozen)) {
                is SessionReconcileResult.Completed -> {
                    result.status
                }

                is SessionReconcileResult.Halted -> {
                    val current = readCurrentLocked(nowEpochMillis)
                    if (current is LocalSessionStatus.Active) {
                        actionTag = SessionTag(current.record)
                        mutableView.update { it.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
                    }
                    current?.takeIf { it is LocalSessionStatus.Ended }
                }
            }
        } else {
            readCurrentLocked(nowEpochMillis)
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
        if (isTransitionConverged(status, enforcedIdentity, mutableView.value.state, actionTag, confirmedClear)) {
            return
        }
        drainJob.start()
        transitions.trySend(Unit)
    }

    private suspend fun executeWork() {
        val current = stateMutex.withLock { readDesiredLocked(clock.currentEpochMillis()) } ?: return
        if (isTransitionConverged(current, enforcedIdentity, mutableView.value.state, actionTag, confirmedClear)) {
            return
        }
        when (current) {
            is LocalSessionStatus.Active -> {
                runReconcile(SessionTag(current.record), current.record, current.frozenStartSet)
            }

            is LocalSessionStatus.Ended -> {
                runClear(SessionTag(current.record))
            }

            LocalSessionStatus.Inactive -> {
                return
            }
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
            val targets = loadTargets()
            val frozen = targets.toFrozenStartSet()
            frozenStartSet = frozen
            if (!ensureApplicableBeforeApply(stateMutex, store, clock, tag, record, ::settleForTag)) {
                return
            }
            val requested = targets.toEnforcedSet()
            portMutex.withLock {
                val needsClear = entering is EnforcementState.ActionRequired && entering.kind == EnforcementActionKind.CLEAR_FAILED
                if (!prepareApply(record, tag, needsClear)) {
                    null
                } else {
                    confirmedClear = false
                    enforcement.apply(record.toEnforcementRequest(requested, targets)).also { recordApply(tag, it, requested) }
                }
            } ?: return
            settleForTag()
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
        mutableView.update { it.copy(busy = true) }
        try {
            portMutex.withLock {
                val current = stateMutex.withLock { readDesiredLocked(clock.currentEpochMillis()) }
                if (current == null || current is LocalSessionStatus.Active || tagOf(current) != expected) return@withLock
                if (confirmedClear && enforcedIdentity == null) return@withLock
                if (mutableView.value.state is EnforcementState.Inactive && enforcement.status() == EnforcementOutcome.CLEARED) {
                    confirmedClear = true
                    return@withLock
                }
                clearForReplacement(expected)
            }
            settleForTag()
        } finally {
            mutableView.update { it.copy(busy = false) }
        }
    }

    private suspend fun reconcile(
        tag: SessionTag,
        record: SessionRecord,
        frozen: FrozenStartSet?,
    ) {
        val sessionId = record.sessionId.reconciliationId()
        if (pendingNativeExpiry != null && pendingNativeExpiry != tag) {
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
            return
        }
        val (outcome, held) = portMutex.withLock { readStatusAndHeld(tag, sessionId) }
        val current = stateMutex.withLock { readDesiredLocked(clock.currentEpochMillis()) }
        if (current !is LocalSessionStatus.Active || SessionTag(current.record) != tag) {
            settleForTag()
            return
        }
        if (outcome == EnforcementOutcome.APPLIED && enforcedIdentity == tag) {
            actionTag = null
            mutableView.update { view -> view.copy(state = EnforcementState.Active(false)) }
        } else if (held) {
            adoptHeld(tag, frozen)
        } else if (enforcement.reapplyRequiresPrompt) {
            actionTag = tag
            mutableView.update { view ->
                view.copy(
                    state = EnforcementActionKind.RESUME_REQUIRED.toAction(true),
                    enforced = frozen?.toEnforcedSet() ?: view.enforced,
                )
            }
        } else {
            reapplyCurrent(record, tag, frozen)
        }
    }

    private suspend fun readStatusAndHeld(
        tag: SessionTag,
        sessionId: String,
    ): Pair<EnforcementOutcome, Boolean> {
        val status = enforcement.status()
        if (status != EnforcementOutcome.APPLIED || enforcedIdentity == tag) {
            return status to false
        }
        return status to enforcement.holdsSession(sessionId)
    }

    private suspend fun adoptHeld(
        tag: SessionTag,
        frozen: FrozenStartSet?,
    ) {
        val displayed = frozen?.toEnforcedSet() ?: loadTargets().toEnforcedSet()
        unknownStreak = 0
        enforcedIdentity = tag
        actionTag = null
        mutableView.update { it.copy(state = EnforcementState.Active(false), enforced = displayed) }
    }

    private suspend fun reapplyCurrent(
        record: SessionRecord,
        tag: SessionTag,
        frozen: FrozenStartSet?,
    ) {
        if (!ensureApplicableBeforeApply(stateMutex, store, clock, tag, record, ::settleForTag)) {
            return
        }
        val targets = loadTargets()
        val requested = targets.toEnforcedSet()
        portMutex.withLock {
            if (!prepareApply(record, tag, clear = true)) {
                null
            } else {
                confirmedClear = false
                enforcement.apply(record.toEnforcementRequest(requested, targets)).also {
                    recordApply(tag, it, frozen?.toEnforcedSet() ?: requested)
                }
            }
        } ?: return
        settleForTag()
    }

    private fun recordApply(
        tag: SessionTag,
        report: EnforcementApplyReport,
        displayed: EnforcedSet
    ) {
        mutableView.update { it.copy(state = report.toActiveState(), enforced = displayed) }
        if (report.outcome == EnforcementOutcome.APPLIED || report.outcome == EnforcementOutcome.NOTHING_TO_ENFORCE) {
            unknownStreak = 0
            enforcedIdentity = tag
            actionTag = null
        } else {
            enforcedIdentity = null
            actionTag = tag
        }
    }

    private suspend fun clearForReplacement(tag: SessionTag?): Boolean {
        if (enforcement.clear() == EnforcementOutcome.CLEARED) {
            confirmedClear = true
            enforcedIdentity = null
            actionTag = null
            mutableView.update { it.copy(state = EnforcementState.Inactive) }
            return true
        }
        actionTag = tag
        mutableView.update { it.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        return false
    }

    private suspend fun persistDisplacement(tag: SessionTag): Boolean {
        if (persistDisplacedExpiry(enforcement, store, tag.sessionId.reconciliationId())) {
            return true
        }
        actionTag = tag
        mutableView.update { it.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        return false
    }

    private suspend fun prepareApply(
        record: SessionRecord,
        tag: SessionTag,
        clear: Boolean,
    ): Boolean {
        if (!ensureDesiredApplicable(record, tag) || !persistDisplacement(tag)) return false
        if (clear && !clearForReplacement(tag)) return false
        return ensureDesiredApplicable(record, tag)
    }

    private suspend fun ensureDesiredApplicable(
        record: SessionRecord,
        tag: SessionTag
    ): Boolean {
        val current = stateMutex.withLock { readDesiredLocked(clock.currentEpochMillis()) }
        if (current !is LocalSessionStatus.Active || SessionTag(current.record) != tag) {
            if (current != null) settle(current)
            return false
        }
        return ensureApplicableBeforeApply(stateMutex, store, clock, tag, record, ::settleForTag)
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
            readDesiredLocked(clock.currentEpochMillis())
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

private const val SESSION_TICK_MILLIS: Long = 1_000L
