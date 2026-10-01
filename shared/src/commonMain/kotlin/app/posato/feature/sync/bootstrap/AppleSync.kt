package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.SessionExchangeObserver
import app.posato.feature.session.data.SessionSyncTriggers
import app.posato.feature.session.data.SessionWorkspaceCapture
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.LocalMutationResult
import app.posato.feature.sync.domain.LocalSyncMutation
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.domain.PolicySyncBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    COMPLETED,
    RETRYABLE,
    WAITING_FOR_KEY,
    ACTION_REQUIRED,
}

internal enum class SyncAttentionReason {
    LOCAL_CAPACITY,
    SHARED_CAPACITY,
    SCHEDULE_CAPACITY,
    SET_CAPACITY,
}

internal data class AppleSyncState(
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
    val linked: Boolean = false,
    val joinPending: Boolean = false,
    val checkingJoin: Boolean = false,
    val reason: SyncAttentionReason? = null,
)

internal class AppleSync(
    private val coordinator: BootstrapCoordinator,
    internal val core: SyncOperationCore,
    mailbox: MailboxPort,
    keys: BootstrapKeyPort,
    store: BootstrapStore,
    private val policySync: LocalPolicySyncStore,
    crypto: SyncCryptoProvider,
    internal val backgroundDispatcher: CoroutineDispatcher,
    private val onWorkspaceRemoved: suspend () -> Unit = {},
    private val scheduleSync: ScheduleSync? = null,
    private val backgroundTime: SyncBackgroundTime = SyncBackgroundTime.None,
    /** Keeps this device's app choices to the surviving sets when a received change removed others. */
    onSetsRemoved: suspend (Set<PauseSetId>) -> Unit = {},
) {
    internal val bootstrap = AppleBootstrap(coordinator, backgroundDispatcher)
    private val reconciler = PolicyReconciler(policySync, onSetsRemoved)
    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val opportunities = Channel<Unit>(Channel.CONFLATED)
    private val writers = AppleSyncWriter(coordinator, core, ::publish)
    private val authoring = AppleSyncAuthoring(store, policySync, ::publish)
    private val exchange = AppleMailboxExchange(mailbox, crypto)
    private val removal = AppleWorkspaceRemoval(mailbox, keys, store)
    private val mutableState = MutableStateFlow(AppleSyncState())
    internal val sessionTriggers: SessionSyncTriggers = object : SessionSyncTriggers {
        override suspend fun captureWorkspace(): SessionWorkspaceCapture {
            return when (val captured = this@AppleSync.captureWorkspace()) {
                is BootstrapStoreResult.Failure -> {
                    SessionWorkspaceCapture.Unknown
                }

                is BootstrapStoreResult.Success -> {
                    val workspace = captured.value
                    if (workspace == null) {
                        SessionWorkspaceCapture.Unlinked
                    } else {
                        SessionWorkspaceCapture.Linked(workspace.context.workspaceId.value.copyBytes())
                    }
                }
            }
        }

        override fun restoreSessions() {
            scope.launch {
                guarded {
                    if (sessionTriggers.captureWorkspace() !is SessionWorkspaceCapture.Linked) return@guarded
                    val writer = writers.open()
                    sessionObserver?.onReplicaSnapshot(writer?.sessionSnapshot)
                }
            }
        }

        override fun requestSync() {
            syncNow()
        }
    }
    internal var sessionObserver: SessionExchangeObserver? = null
    private val joinFlight = Mutex()
    private val worker = scope.launch(start = CoroutineStart.LAZY) {
        ExchangeLoop(opportunities, backgroundTime).run {
            var outcome = SyncStatus.RETRYABLE
            guarded {
                mutableState.refreshLinked(coordinator)
                val writer = writers.open()
                if (writer != null) {
                    sessionObserver?.onReplicaSnapshot(writer.sessionSnapshot)
                    runExchange()
                }
                outcome = mutableState.value.status
            }
            outcome
        }
    }
    val state = mutableState.asStateFlow()

    suspend fun syncWithIcloud() {
        scope.async {
            if (!joinFlight.tryLock()) return@async
            try {
                guarded {
                    if (coordinator.joins.hasPending()) {
                        continueJoin()
                    } else {
                        val result = coordinator.bootstrap()
                        publish(result.toSyncStatus())
                        mutableState.refreshLinked(coordinator)
                        if (result is BootstrapResult.Ready) syncNow()
                    }
                }
            } finally {
                joinFlight.unlock()
            }
        }.await()
    }

    suspend fun onForeground() {
        withContext(backgroundDispatcher) {
            if (!joinFlight.tryLock()) return@withContext
            try {
                AppleBootstrap.flight.withLock {
                    if (coordinator.joins.hasPending()) {
                        continueJoin()
                    } else {
                        mutableState.refreshLinked(coordinator)
                        if (state.value.linked) syncNow()
                    }
                }
            } finally {
                joinFlight.unlock()
            }
        }
    }

    private suspend fun continueJoin() {
        mutableState.update { it.copy(checkingJoin = true) }
        try {
            val result = coordinator.joins.recheck()
            mutableState.refreshLinked(coordinator)
            when (result) {
                JoinCheckResult.UNCHANGED -> {
                    return
                }

                JoinCheckResult.WAITING -> {
                    publish(SyncStatus.WAITING_FOR_KEY)
                }

                JoinCheckResult.READY -> {
                    publish(SyncStatus.SYNCING)
                    syncNow()
                }

                JoinCheckResult.LOCAL_ONLY -> {
                    publish(SyncStatus.LOCAL_ONLY)
                }

                JoinCheckResult.ACTION_REQUIRED -> {
                    publish(SyncStatus.ACTION_REQUIRED)
                }

                JoinCheckResult.RETRYABLE -> {
                    publish(SyncStatus.RETRYABLE)
                }

                JoinCheckResult.STATE_CHANGED -> {
                    if (state.value.linked) syncNow()
                }
            }
        } finally {
            mutableState.update { it.copy(checkingJoin = false) }
        }
    }

    fun syncNow() {
        worker.start()
        opportunities.trySend(Unit)
    }

    suspend fun removeWorkspace() {
        scope.async {
            guarded {
                publish(SyncStatus.SYNCING)
                val result = removal.remove(coordinator.checkEstablished(), writers::close) {
                    sessionObserver?.onReplicaSnapshot(null)
                    onWorkspaceRemoved()
                }
                mutableState.refreshLinked(coordinator)
                mutableState.update { it.copy(reason = null) }
                publish(result)
            }
        }.await()
    }

    suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?> {
        return authoring.captureWorkspace()
    }

    suspend fun close() {
        opportunities.cancel()
        scope.cancel()
        worker.join()
        AppleBootstrap.flight.withLock { writers.close() }
    }

    private suspend fun runExchange() {
        val check = coordinator.checkEstablished()
        if (check.status != EstablishedStatus.READY) {
            publish(check.status.toSyncStatus())
            return
        }
        publish(SyncStatus.SYNCING)
        val active = writers.open()
        if (active == null) {
            return
        }
        // The session phase reconciles converged session intent before policy work.
        // A policy-capacity failure later in this pass must not suppress an accepted
        // session end, which is committed and cleared inside the session phase.
        val workspace = checkNotNull(check.workspace)
        val read = if (enablePauseSetsOrHalt(policySync, workspace, active, ::publish)) readBaseOrHalt(policySync, ::publish) else BaseRead.Halted
        if (read is BaseRead.Ready && exchangeLegsOrHalt(read.base, authoring, exchange, workspace, active, ::publish) {
                sessionObserver?.onReplicaSnapshot(active.sessionSnapshot)
            }
        ) {
            val sessionHalt = sessionObserver?.onExchange(active, workspace)
            if (sessionHalt != null) {
                publish(sessionHalt)
                return
            }
            // Schedules run after sessions and before policies; a halt stops the exchange like the policy phase does.
            val schedules = scheduleSync?.pass(active, workspace) { exchange.publishPending(workspace, active) }
            if (schedules is ScheduleSyncResult.Halted) {
                publish(schedules.status)
                return
            }
            runPolicyPhase(workspace, active, read.base)
            // A refused schedule is reported only when the policy phase has nothing more urgent to say.
            if ((schedules as? ScheduleSyncResult.Done)?.refused == true && mutableState.value.status == SyncStatus.COMPLETED) {
                mutableState.update { it.copy(status = SyncStatus.ACTION_REQUIRED, reason = SyncAttentionReason.SCHEDULE_CAPACITY) }
            }
        }
    }

    private suspend fun runPolicyPhase(
        workspace: EstablishedWorkspace,
        writer: SyncWriter,
        base: PolicySyncBase?,
    ) {
        if (!seedAndDrainOrHalt(base, reconciler, authoring, workspace, writer, ::publish)) {
            return
        }
        // Schedules left a removed set in the schedule phase, so the removal follows their moves.
        if (!authoring.drain(writer, setRemovals = true)) {
            return
        }
        val republished = exchange.publishPending(workspace, writer)
        if (republished != null) {
            publish(republished)
            return
        }
        // What the drain authored is published above, so apply reads a fresh projection and the
        // base never lags a local change that would then look remote.
        mutableState.publishOutcome(reconciler.apply(writer.projection(), base))
    }

    private fun publish(status: SyncStatus) {
        mutableState.update { it.copy(status = status, reason = null) }
    }

    private suspend fun guarded(action: suspend () -> Unit) {
        AppleBootstrap.flight.withLock {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                publish(SyncStatus.RETRYABLE)
            }
        }
    }
}

private sealed interface BaseRead {
    data class Ready(
        val base: PolicySyncBase?,
    ) : BaseRead

    data object Halted : BaseRead
}

private suspend fun readBaseOrHalt(
    policies: LocalPolicySyncStore,
    publish: (SyncStatus) -> Unit,
): BaseRead {
    return when (val read = policies.readBase()) {
        is LocalPolicyResult.Success -> {
            BaseRead.Ready(read.value)
        }

        is LocalPolicyResult.Failure -> {
            publish(read.reason.toSyncStatus())
            BaseRead.Halted
        }
    }
}

private suspend fun exchangeLegsOrHalt(
    base: PolicySyncBase?,
    authoring: AppleSyncAuthoring,
    exchange: AppleMailboxExchange,
    workspace: EstablishedWorkspace,
    writer: SyncWriter,
    publish: (SyncStatus) -> Unit,
    acceptedProgress: suspend () -> Unit,
): Boolean {
    if (base != null && !authoring.drain(writer)) {
        return false
    }
    val published = exchange.publishPending(workspace, writer)
    if (published != null) {
        publish(published)
        return false
    }
    val consumed = exchange.consume(workspace, writer, acceptedProgress)
    if (consumed != SyncStatus.COMPLETED) {
        publish(consumed)
    }
    return consumed == SyncStatus.COMPLETED
}

/** A migrated replica marks the workspace once, so devices still on 1.2 stop receiving now rather than at a later edit. */
private suspend fun enablePauseSetsOrHalt(
    policies: LocalPolicySyncStore,
    workspace: EstablishedWorkspace,
    writer: SyncWriter,
    publish: (SyncStatus) -> Unit,
): Boolean {
    val result = policies.enablePauseSetsOnce(workspace.context.workspaceId.value.copyBytes()) {
        writer.mutate(LocalSyncMutation.EnablePauseSets) is LocalMutationResult.Success
    }
    return when (result) {
        is LocalPolicyResult.Success -> result.value.also { recorded -> if (!recorded) publish(SyncStatus.ACTION_REQUIRED) }
        is LocalPolicyResult.Failure -> false.also { publish(result.reason.toSyncStatus()) }
    }
}

private suspend fun seedAndDrainOrHalt(
    base: PolicySyncBase?,
    reconciler: PolicyReconciler,
    authoring: AppleSyncAuthoring,
    workspace: EstablishedWorkspace,
    writer: SyncWriter,
    publish: (SyncStatus) -> Unit,
): Boolean {
    if (base != null) {
        return true
    }
    val seeded = reconciler.seedLocalExtras(workspace.context.workspaceId.value.copyBytes(), writer.projection())
    if (seeded is LocalPolicyResult.Failure) {
        publish(seeded.reason.toSyncStatus())
        return false
    }
    return authoring.drain(writer)
}

private fun MutableStateFlow<AppleSyncState>.publishOutcome(outcome: ReconcileOutcome) {
    when (outcome) {
        ReconcileOutcome.AppliedClean -> {
            update { it.copy(status = SyncStatus.COMPLETED, reason = null) }
        }

        ReconcileOutcome.RefusedWorkspaceFull -> {
            update { it.copy(status = SyncStatus.ACTION_REQUIRED, reason = SyncAttentionReason.SHARED_CAPACITY) }
        }

        ReconcileOutcome.RefusedLocalCap -> {
            update { it.copy(status = SyncStatus.ACTION_REQUIRED, reason = SyncAttentionReason.LOCAL_CAPACITY) }
        }

        ReconcileOutcome.RefusedSetCapacity -> {
            update { it.copy(status = SyncStatus.ACTION_REQUIRED, reason = SyncAttentionReason.SET_CAPACITY) }
        }

        ReconcileOutcome.Corrupt -> {
            update { it.copy(status = SyncStatus.ACTION_REQUIRED, reason = null) }
        }

        ReconcileOutcome.Conflict, ReconcileOutcome.StorageFailure -> {
            update { it.copy(status = SyncStatus.RETRYABLE, reason = null) }
        }
    }
}

internal fun EstablishedStatus.toSyncStatus(): SyncStatus {
    return when (this) {
        EstablishedStatus.READY -> SyncStatus.COMPLETED

        EstablishedStatus.LOCAL_ONLY -> SyncStatus.LOCAL_ONLY

        EstablishedStatus.RETRYABLE -> SyncStatus.RETRYABLE

        EstablishedStatus.ZONE_MISSING, EstablishedStatus.ANCHOR_MISSING,
        EstablishedStatus.DIFFERENT_ANCHOR,
        EstablishedStatus.ACTION_REQUIRED -> SyncStatus.ACTION_REQUIRED
    }
}

private fun BootstrapResult.toSyncStatus(): SyncStatus {
    return when (this) {
        is BootstrapResult.Ready -> SyncStatus.SYNCING
        BootstrapResult.WaitingForWorkspaceKey -> SyncStatus.WAITING_FOR_KEY
        BootstrapResult.Retryable -> SyncStatus.RETRYABLE
        BootstrapResult.ActionRequired -> SyncStatus.ACTION_REQUIRED
    }
}

private suspend fun MutableStateFlow<AppleSyncState>.refreshLinked(coordinator: BootstrapCoordinator) {
    val linked = coordinator.establishedContext() != null
    val joinPending = coordinator.joins.hasPending()
    update { it.copy(linked = linked, joinPending = joinPending) }
}
