package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.targets.domain.TargetPolicy
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

internal data class AppleSyncState(
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
    val linked: Boolean = false,
    val joinPending: Boolean = false,
    val checkingJoin: Boolean = false,
)

internal class AppleSync(
    private val coordinator: BootstrapCoordinator,
    internal val core: SyncOperationCore,
    mailbox: MailboxPort,
    keys: BootstrapKeyPort,
    store: BootstrapStore,
    crypto: SyncCryptoProvider,
    internal val backgroundDispatcher: CoroutineDispatcher,
) {
    internal val bootstrap = AppleBootstrap(coordinator, backgroundDispatcher)
    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val opportunities = Channel<Unit>(Channel.CONFLATED)
    private val writers = AppleSyncWriter(coordinator, core, ::publish)
    private val authoring = AppleSyncAuthoring(store, writers, ::publish)
    private val exchange = AppleMailboxExchange(mailbox, crypto)
    private val removal = AppleWorkspaceRemoval(mailbox, keys, store)
    private val mutableState = MutableStateFlow(AppleSyncState())
    private val joinFlight = Mutex()
    private val worker = scope.launch(start = CoroutineStart.LAZY) {
        for (ignored in opportunities) {
            guarded {
                mutableState.refreshLinked(coordinator)
                if (authoring.drain()) runExchange()
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
                publish(result)
            }
        }.await()
    }

    suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?> {
        return authoring.captureWorkspace()
    }

    fun enqueueDomainChanges(
        workspace: BootstrapStoreResult<EstablishedWorkspace?>,
        before: TargetPolicy,
        after: TargetPolicy
    ) {
        if (authoring.enqueue(workspace, before, after)) syncNow()
    }

    suspend fun close() {
        authoring.close()
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
        publish(exchange.exchange(checkNotNull(check.workspace), active))
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
