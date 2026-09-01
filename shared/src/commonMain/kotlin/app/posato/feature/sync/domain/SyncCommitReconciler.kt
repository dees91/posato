package app.posato.feature.sync.domain

import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.data.SyncReplicaStore
import app.posato.feature.sync.data.SyncStoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class SyncCommitReconciler(
    private val store: SyncReplicaStore,
    private val onCommitCancellation: (SyncReplicaSnapshot?) -> Unit,
) {
    suspend fun commitLocal(
        checkpoint: SyncReplicaSnapshot,
        prepared: PreparedLocalMutation
    ): SyncReplicaSnapshot? {
        val expected = prepared.expectedAfter(checkpoint)
        return try {
            val firstAttempt = store.commitLocal(checkpoint.revision, prepared.bundles, prepared.clockState)
            val firstResult = (firstAttempt as? SyncStoreResult.Success)?.value?.takeIf(expected::equals)
            val observed = firstResult?.let { SyncStoreResult.Success(it) } ?: store.read(checkpoint.context)
            val observedSnapshot = (observed as? SyncStoreResult.Success)?.value
            val retryResult = if (observedSnapshot == checkpoint) {
                retryLocal(checkpoint, prepared, expected)
            } else {
                null
            }
            firstResult ?: observedSnapshot?.takeIf(expected::equals) ?: retryResult
        } catch (cancellation: CancellationException) {
            reconcileCancellation(checkpoint, expected)
            throw cancellation
        }
    }

    suspend fun commitRemote(
        checkpoint: SyncReplicaSnapshot,
        expected: SyncReplicaSnapshot,
        commit: suspend () -> SyncStoreResult<SyncReplicaSnapshot>,
    ): SyncReplicaSnapshot? {
        return try {
            val firstResult = (commit() as? SyncStoreResult.Success)?.value?.takeIf(expected::equals)
            val observed = firstResult?.let { SyncStoreResult.Success(it) } ?: store.read(checkpoint.context)
            val observedSnapshot = (observed as? SyncStoreResult.Success)?.value
            val retryResult = if (observedSnapshot == checkpoint) retryRemote(checkpoint, expected, commit) else null
            firstResult ?: observedSnapshot?.takeIf(expected::equals) ?: retryResult
        } catch (cancellation: CancellationException) {
            reconcileCancellation(checkpoint, expected)
            throw cancellation
        }
    }

    private suspend fun reconcileCancellation(
        checkpoint: SyncReplicaSnapshot,
        expected: SyncReplicaSnapshot,
    ) {
        val reconciled = withContext(NonCancellable) {
            try {
                (store.read(checkpoint.context) as? SyncStoreResult.Success)?.value?.takeIf { observed ->
                    observed == checkpoint || observed == expected
                }
            } catch (_: Exception) {
                null
            }
        }
        onCommitCancellation(reconciled)
    }

    private suspend fun retryLocal(
        checkpoint: SyncReplicaSnapshot,
        prepared: PreparedLocalMutation,
        expected: SyncReplicaSnapshot,
    ): SyncReplicaSnapshot? {
        val retry = store.commitLocal(checkpoint.revision, prepared.bundles, prepared.clockState)
        val retryResult = (retry as? SyncStoreResult.Success)?.value?.takeIf(expected::equals)
        val observation = retryResult?.let { SyncStoreResult.Success(it) } ?: store.read(checkpoint.context)
        return retryResult ?: (observation as? SyncStoreResult.Success)?.value?.takeIf(expected::equals)
    }

    private suspend fun retryRemote(
        checkpoint: SyncReplicaSnapshot,
        expected: SyncReplicaSnapshot,
        commit: suspend () -> SyncStoreResult<SyncReplicaSnapshot>,
    ): SyncReplicaSnapshot? {
        val retryResult = (commit() as? SyncStoreResult.Success)?.value?.takeIf(expected::equals)
        val observation = retryResult?.let { SyncStoreResult.Success(it) } ?: store.read(checkpoint.context)
        return retryResult ?: (observation as? SyncStoreResult.Success)?.value?.takeIf(expected::equals)
    }
}
