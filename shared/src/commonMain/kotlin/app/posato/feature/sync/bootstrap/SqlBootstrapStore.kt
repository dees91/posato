package app.posato.feature.sync.bootstrap

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.core.database.Sync_bootstrap_state
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal data class PersistedCandidate(
    val workspaceId: WorkspaceId,
    val transportEpochId: TransportEpochId,
    val keyEpochId: KeyEpochId,
    val binding: AccountBinding
) {
    override fun toString(): String {
        return "PersistedCandidate(redacted)"
    }
}

internal data class EstablishedWorkspace(
    val context: SyncContext,
    val binding: AccountBinding
) {
    override fun toString(): String {
        return "EstablishedWorkspace(redacted)"
    }
}

internal sealed interface BootstrapState {
    data object None : BootstrapState

    data class Candidate(
        val candidate: PersistedCandidate
    ) : BootstrapState

    data class Established(
        val workspace: EstablishedWorkspace
    ) : BootstrapState
}

internal sealed interface BootstrapStoreResult<out T> {
    data class Success<T>(
        val value: T
    ) : BootstrapStoreResult<T>

    data class Failure(
        val reason: BootstrapStoreFailure
    ) : BootstrapStoreResult<Nothing>
}

internal enum class BootstrapStoreFailure {
    CORRUPTION,
    STORAGE_FAILURE
}

internal const val REMOVED_WORKSPACE_LIMIT: Int = 32

internal interface BootstrapStore {
    suspend fun clearEstablished(workspace: EstablishedWorkspace): BootstrapStoreResult<Unit>

    suspend fun containsRemoved(workspaceId: WorkspaceId): BootstrapStoreResult<Boolean>

    suspend fun read(): BootstrapStoreResult<BootstrapState>

    suspend fun persistCandidate(candidate: PersistedCandidate): BootstrapStoreResult<Unit>

    suspend fun commitEstablished(workspace: EstablishedWorkspace): BootstrapStoreResult<Unit>
}

internal class SqlBootstrapStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher
) : BootstrapStore {
    override suspend fun clearEstablished(workspace: EstablishedWorkspace): BootstrapStoreResult<Unit> {
        return databaseCall {
            val row = database.syncBootstrapQueries.selectBootstrapState().awaitAsList().singleOrNull()
                ?: failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
            if (restoreState(row) != BootstrapState.Established(workspace)) {
                failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
            }
            database.syncReplicaQueries.clearPendingBundles()
            database.syncReplicaQueries.clearStagedBundles()
            database.syncReplicaQueries.clearTerminalExpiry()
            database.syncReplicaQueries.clearAcceptedBundles()
            database.syncReplicaQueries.clearReplicaState()
            database.syncLocalPolicyQueries.deleteIntents()
            database.syncSessionQueries.deleteSessionIntents()
            database.syncLocalPolicyQueries.deleteBaseMarker()
            database.syncLocalPolicyQueries.deleteBaseDomains()
            database.syncLocalPolicyQueries.deleteBaseApplication()
            database.syncBootstrapQueries.insertRemovedWorkspace(
                workspace_id = workspace.context.workspaceId.value.copyBytes(),
            )
            database.syncBootstrapQueries.evictRemovedWorkspacesBeyond(
                keep_count = REMOVED_WORKSPACE_LIMIT.toLong(),
            )
            database.syncBootstrapQueries.clearEstablishedWorkspace()
        }
    }

    override suspend fun containsRemoved(workspaceId: WorkspaceId): BootstrapStoreResult<Boolean> {
        return databaseCall {
            database.syncBootstrapQueries.selectRemovedWorkspace(workspaceId.value.copyBytes())
                .awaitAsList()
                .isNotEmpty()
        }
    }

    override suspend fun read(): BootstrapStoreResult<BootstrapState> {
        return databaseCall {
            val rows = database.syncBootstrapQueries.selectBootstrapState().awaitAsList()
            if (rows.size > 1) {
                failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
            }
            val row = rows.singleOrNull() ?: return@databaseCall BootstrapState.None
            restoreState(row)
        }
    }

    override suspend fun persistCandidate(candidate: PersistedCandidate): BootstrapStoreResult<Unit> {
        return databaseCall {
            database.syncBootstrapQueries.persistBootstrapCandidate(
                candidate_workspace_id = candidate.workspaceId.value.copyBytes(),
                candidate_transport_epoch_id = candidate.transportEpochId.value.copyBytes(),
                candidate_key_epoch_id = candidate.keyEpochId.value.copyBytes(),
                binding = candidate.binding.copyBytes(),
            )
        }
    }

    override suspend fun commitEstablished(workspace: EstablishedWorkspace): BootstrapStoreResult<Unit> {
        return databaseCall {
            database.syncBootstrapQueries.commitEstablishedWorkspace(
                binding = workspace.binding.copyBytes(),
                established_workspace_id = workspace.context.workspaceId.value.copyBytes(),
                established_transport_epoch_id = workspace.context.transportEpochId.value.copyBytes(),
                established_key_epoch_id = workspace.context.keyEpochId.value.copyBytes(),
            )
        }
    }

    private fun restoreState(row: Sync_bootstrap_state): BootstrapState {
        if (row.singleton != 1L) {
            failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
        }
        val candidate = restoreCandidate(row)
        val established = restoreEstablished(row)
        if (candidate != null && established != null) {
            failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
        }
        if (candidate != null) {
            return BootstrapState.Candidate(candidate)
        }
        if (established != null) {
            return BootstrapState.Established(established)
        }
        if (row.binding != null) {
            failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
        }
        return BootstrapState.None
    }

    private fun restoreCandidate(row: Sync_bootstrap_state): PersistedCandidate? {
        val identifiers = listOf(
            row.candidate_workspace_id,
            row.candidate_transport_epoch_id,
            row.candidate_key_epoch_id,
        )
        if (identifiers.all { it == null }) {
            return null
        }
        if (identifiers.any { it == null } || row.binding == null) {
            failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
        }
        return PersistedCandidate(
            workspaceId = WorkspaceId(restoreIdentifier(checkNotNull(identifiers[0]))),
            transportEpochId = TransportEpochId(restoreIdentifier(checkNotNull(identifiers[1]))),
            keyEpochId = KeyEpochId(restoreIdentifier(checkNotNull(identifiers[2]))),
            binding = AccountBinding.fromBytes(checkNotNull(row.binding))
                ?: failBootstrapStore(BootstrapStoreFailure.CORRUPTION),
        )
    }

    private fun restoreEstablished(row: Sync_bootstrap_state): EstablishedWorkspace? {
        val identifiers = listOf(
            row.established_workspace_id,
            row.established_transport_epoch_id,
            row.established_key_epoch_id,
        )
        if (identifiers.all { it == null }) {
            return null
        }
        if (identifiers.any { it == null } || row.binding == null) {
            failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
        }
        return EstablishedWorkspace(
            context = SyncContext(
                workspaceId = WorkspaceId(restoreIdentifier(checkNotNull(identifiers[0]))),
                transportEpochId = TransportEpochId(restoreIdentifier(checkNotNull(identifiers[1]))),
                keyEpochId = KeyEpochId(restoreIdentifier(checkNotNull(identifiers[2]))),
            ),
            binding = AccountBinding.fromBytes(checkNotNull(row.binding))
                ?: failBootstrapStore(BootstrapStoreFailure.CORRUPTION),
        )
    }

    private fun restoreIdentifier(bytes: ByteArray): SyncIdentifier {
        return SyncIdentifier.fromUuidV4Bytes(bytes) ?: failBootstrapStore(BootstrapStoreFailure.CORRUPTION)
    }

    private suspend fun <T> databaseCall(block: suspend () -> T): BootstrapStoreResult<T> {
        return withContext(databaseDispatcher) {
            try {
                BootstrapStoreResult.Success(
                    database.transactionWithResult {
                        block()
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: BootstrapStoreException) {
                BootstrapStoreResult.Failure(failure.reason)
            } catch (_: Exception) {
                BootstrapStoreResult.Failure(BootstrapStoreFailure.STORAGE_FAILURE)
            }
        }
    }
}

private class BootstrapStoreException(
    val reason: BootstrapStoreFailure
) : Exception()

private fun failBootstrapStore(reason: BootstrapStoreFailure): Nothing {
    throw BootstrapStoreException(reason)
}
