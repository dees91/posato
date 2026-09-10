package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.data.SyncCryptoProvider
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
) {
    internal val bootstrap = AppleBootstrap(coordinator, backgroundDispatcher)
    private val reconciler = PolicyReconciler(policySync)
    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val opportunities = Channel<Unit>(Channel.CONFLATED)
    private val writers = AppleSyncWriter(coordinator, core, ::publish)
    private val authoring = AppleSyncAuthoring(store, policySync, ::publish)
    private val exchange = AppleMailboxExchange(mailbox, crypto)
    private val removal = AppleWorkspaceRemoval(mailbox, keys, store)
    private val mutableState = MutableStateFlow(AppleSyncState())
    private val joinFlight = Mutex()
    private val worker = scope.launch(start = CoroutineStart.LAZY) {
        for (ignored in opportunities) {
            guarded {
                mutableState.refreshLinked(coordinator)
                val writer = writers.open()
                if (writer != null) runExchange()
            }
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
                val result = removal.remove(coordinator.checkEstablished(), writers::close)
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
        val active = writers.open() ?: return
        val workspace = checkNotNull(check.workspace)
        val base = when (val read = readBaseOrHalt(policySync, ::publish)) {
            is BaseRead.Halted -> return
            is BaseRead.Ready -> read.base
        }
        if (!exchangeLegsOrHalt(base, authoring, exchange, workspace, active, ::publish)) {
            return
        }
        runPolicyPhase(workspace, active, base)
    }

    private suspend fun runPolicyPhase(
        workspace: EstablishedWorkspace,
        writer: SyncWriter,
        base: PolicySyncBase?,
    ) {
        if (!seedAndDrainOrHalt(base, reconciler, authoring, workspace, writer, ::publish)) {
            return
        }
        val group = reconciler.decideGroup(writer, writer.projection())
        if (group is GroupOutcome.Failed) {
            publish(group.status)
            return
        }
        val republished = exchange.publishPending(workspace, writer)
        if (republished != null) {
            publish(republished)
            return
        }
        // A name authored by decideGroup is published above, so apply reads a
        // fresh projection; otherwise the base would lag the authored name and
        // a later local rename would be clobbered as a remote change (D4).
        mutableState.publishOutcome(reconciler.apply(writer.projection(), base))
    }

    private fun publish(status: SyncStatus) {
        mutableState.update { it.copy(status = status) }
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
): Boolean {
    if (base != null && !authoring.drain(writer)) {
        return false
    }
    val published = exchange.publishPending(workspace, writer)
    if (published != null) {
        publish(published)
        return false
    }
    val consumed = exchange.consume(workspace, writer)
    if (consumed != SyncStatus.COMPLETED) {
        publish(consumed)
    }
    return consumed == SyncStatus.COMPLETED
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
    val seeded = reconciler.seedLocalExtras(workspace.context.workspaceId.value.copyBytes())
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

        ReconcileOutcome.AppliedWorkspaceFull -> {
            update { it.copy(status = SyncStatus.ACTION_REQUIRED, reason = SyncAttentionReason.SHARED_CAPACITY) }
        }

        ReconcileOutcome.RefusedLocalCap -> {
            update { it.copy(status = SyncStatus.ACTION_REQUIRED, reason = SyncAttentionReason.LOCAL_CAPACITY) }
        }

        ReconcileOutcome.Corrupt -> {
            update { it.copy(status = SyncStatus.ACTION_REQUIRED) }
        }

        ReconcileOutcome.Conflict, ReconcileOutcome.StorageFailure -> {
            update { it.copy(status = SyncStatus.RETRYABLE) }
        }
    }
}

internal fun EstablishedStatus.toSyncStatus(): SyncStatus {
    return when (this) {
        EstablishedStatus.READY -> SyncStatus.COMPLETED
        EstablishedStatus.LOCAL_ONLY -> SyncStatus.LOCAL_ONLY
        EstablishedStatus.RETRYABLE -> SyncStatus.RETRYABLE
        EstablishedStatus.ZONE_MISSING, EstablishedStatus.DIFFERENT_ANCHOR, EstablishedStatus.ACTION_REQUIRED -> SyncStatus.ACTION_REQUIRED
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
