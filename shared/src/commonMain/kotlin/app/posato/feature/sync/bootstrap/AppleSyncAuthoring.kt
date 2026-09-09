package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.LocalMutationResult
import app.posato.feature.sync.domain.LocalSyncMutation
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel

internal class AppleSyncAuthoring(
    private val store: BootstrapStore,
    private val writers: AppleSyncWriter,
    private val publish: (SyncStatus) -> Unit,
) {
    private val changes = Channel<QueuedDomainChanges>(Channel.UNLIMITED)

    suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?> {
        return when (val read = store.read()) {
            is BootstrapStoreResult.Failure -> read
            is BootstrapStoreResult.Success -> BootstrapStoreResult.Success((read.value as? BootstrapState.Established)?.workspace)
        }
    }

    fun enqueue(
        workspace: BootstrapStoreResult<EstablishedWorkspace?>,
        before: TargetPolicy,
        after: TargetPolicy
    ): Boolean {
        if (workspace is BootstrapStoreResult.Success && workspace.value == null) return false
        val removed = before.domains - after.domains.toSet()
        val added = after.domains - before.domains.toSet()
        val mutations = removed.map { LocalSyncMutation.RemoveDomain(it) } + added.map { LocalSyncMutation.PresentDomain(it) }
        if (mutations.isEmpty()) return false
        val enqueued = changes.trySend(QueuedDomainChanges(workspace, mutations)).isSuccess
        if (!enqueued) publish(SyncStatus.ACTION_REQUIRED)
        return enqueued
    }

    suspend fun drain(): Boolean {
        while (true) {
            val change = changes.tryReceive().getOrNull() ?: return true
            val authored = try {
                author(change)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                fail()
            }
            if (!authored) return false
        }
    }

    fun close() {
        changes.cancel()
    }

    private suspend fun author(change: QueuedDomainChanges): Boolean {
        val workspace = when (val captured = change.workspace) {
            is BootstrapStoreResult.Failure -> return fail()
            is BootstrapStoreResult.Success -> captured.value ?: return true
        }
        return when (val current = captureWorkspace()) {
            is BootstrapStoreResult.Failure -> fail()
            is BootstrapStoreResult.Success -> current.value != workspace || commit(change.mutations)
        }
    }

    private suspend fun commit(mutations: List<LocalSyncMutation>): Boolean {
        val writer = writers.open() ?: return false
        for (mutation in mutations) {
            if (writer.mutate(mutation) is LocalMutationResult.Failure) return fail()
        }
        publish(SyncStatus.PENDING)
        return true
    }

    private fun fail(): Boolean {
        publish(SyncStatus.ACTION_REQUIRED)
        return false
    }
}

internal data class QueuedDomainChanges(
    val workspace: BootstrapStoreResult<EstablishedWorkspace?>,
    val mutations: List<LocalSyncMutation>,
) {
    override fun toString(): String {
        return "QueuedDomainChanges(redacted)"
    }
}
