package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.LocalMutationResult
import app.posato.feature.sync.domain.LocalSyncMutation
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val requests = Mutex()
    private var worker: Job? = null
    private val writers = AppleSyncWriter(coordinator, core, ::publish)
    private val exchange = AppleMailboxExchange(mailbox, crypto)
    private val removal = AppleWorkspaceRemoval(mailbox, keys, store)
    private val mutableState = MutableStateFlow(AppleSyncState())
    val state = mutableState.asStateFlow()

    suspend fun syncWithIcloud() {
        scope.async {
            guarded {
                val result = coordinator.bootstrap()
                publish(result.toSyncStatus())
                refreshLinked()
                if (result is BootstrapResult.Ready) syncNow()
            }
        }.await()
    }

    suspend fun onForeground() {
        withContext(backgroundDispatcher) {
            val linked = coordinator.establishedContext() != null
            mutableState.update { it.copy(linked = linked) }
            if (linked) syncNow()
        }
    }

    suspend fun syncNow() {
        requests.withLock {
            if (worker == null) {
                worker = scope.launch {
                    for (ignored in opportunities) {
                        guarded { runExchange() }
                    }
                }
            }
            opportunities.trySend(Unit)
        }
    }

    suspend fun removeWorkspace() {
        scope.async {
            guarded {
                publish(SyncStatus.SYNCING)
                val result = removal.remove(coordinator.checkEstablished(), writers::close)
                refreshLinked()
                publish(result)
            }
        }.await()
    }

    suspend fun recordDomainChanges(
        before: TargetPolicy,
        after: TargetPolicy
    ) {
        val removed = before.domains - after.domains.toSet()
        val added = after.domains - before.domains.toSet()
        if (removed.isEmpty() && added.isEmpty()) return
        scope.async {
            guarded(SyncStatus.ACTION_REQUIRED) {
                if (coordinator.establishedContext() == null) return@guarded
                val active = writers.current?.takeIf { it.isActive }
                if (active == null) {
                    publish(SyncStatus.ACTION_REQUIRED)
                    return@guarded
                }
                val mutations = removed.map { LocalSyncMutation.RemoveDomain(it) } + added.map { LocalSyncMutation.PresentDomain(it) }
                for (mutation in mutations) {
                    if (active.mutate(mutation) is LocalMutationResult.Failure) {
                        publish(SyncStatus.ACTION_REQUIRED)
                        return@guarded
                    }
                }
                publish(SyncStatus.PENDING)
                syncNow()
            }
        }.await()
    }

    suspend fun close() {
        scope.cancel()
        worker?.join()
        AppleBootstrap.flight.withLock { writers.close() }
    }

    private suspend fun runExchange() {
        val check = coordinator.checkEstablished()
        refreshLinked()
        if (check.status != EstablishedStatus.READY) {
            publish(check.status.toSyncStatus())
            return
        }
        publish(SyncStatus.SYNCING)
        val active = writers.open() ?: return
        publish(exchange.exchange(checkNotNull(check.workspace), active))
    }

    private suspend fun refreshLinked() {
        val linked = coordinator.establishedContext() != null
        mutableState.update { it.copy(linked = linked) }
    }

    private fun publish(status: SyncStatus) {
        mutableState.update { it.copy(status = status) }
    }

    private suspend fun guarded(
        failureStatus: SyncStatus = SyncStatus.RETRYABLE,
        action: suspend () -> Unit
    ) {
        AppleBootstrap.flight.withLock {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                publish(failureStatus)
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
        is BootstrapResult.Ready -> SyncStatus.PENDING
        BootstrapResult.WaitingForWorkspaceKey -> SyncStatus.WAITING_FOR_KEY
        BootstrapResult.Retryable -> SyncStatus.RETRYABLE
        BootstrapResult.ActionRequired -> SyncStatus.ACTION_REQUIRED
    }
}
