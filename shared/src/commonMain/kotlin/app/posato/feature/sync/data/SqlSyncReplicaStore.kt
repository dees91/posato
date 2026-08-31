package app.posato.feature.sync.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SqlSyncReplicaStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : SyncReplicaStore {
    private val snapshotReader = SqlSnapshotReader(database)

    override suspend fun open(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot> {
        return databaseCall {
            database.transactionWithResult {
                val existing = snapshotReader.readStateRows()
                if (existing.isEmpty()) {
                    database.syncReplicaQueries.insertSyncReplicaState(
                        workspace_id = context.workspaceId.value.copyBytes(),
                        transport_epoch_id = context.transportEpochId.value.copyBytes(),
                        key_epoch_id = context.keyEpochId.value.copyBytes(),
                    )
                }
                snapshotReader.readSnapshotOrThrow(context)
            }
        }
    }

    override suspend fun read(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot> {
        return databaseCall {
            database.transactionWithResult {
                snapshotReader.readSnapshotOrThrow(context)
            }
        }
    }

    override suspend fun commitLocal(
        expectedRevision: Long,
        bundles: List<PreparedStoredBundle>,
        clockState: DurableClockState,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        return mutate(expectedRevision) { current ->
            bundles.forEach { bundle ->
                insertAccepted(bundle)
                database.syncReplicaQueries.insertPendingBundle(
                    bundle_id = bundle.operation.operationId.value.copyBytes(),
                    bundle_bytes = bundle.bundle.copyBytes(),
                )
            }
            advanceState(expectedRevision, clockState, current.transportProgress)
        }
    }

    override suspend fun commitAcceptedRemote(
        expectedRevision: Long,
        bundles: List<PreparedStoredBundle>,
        stagedBundleIdsToDelete: Set<BundleId>,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        return mutate(expectedRevision) { current ->
            bundles.forEach { bundle -> insertAccepted(bundle) }
            stagedBundleIdsToDelete.forEach { bundleId ->
                database.syncReplicaQueries.deleteStagedBundle(bundleId.value.copyBytes())
            }
            advanceState(expectedRevision, clockState, transportProgress ?: current.transportProgress)
        }
    }

    override suspend fun commitStagedRemote(
        expectedRevision: Long,
        bundle: PreparedStoredBundle,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        return mutate(expectedRevision) { current ->
            database.syncReplicaQueries.insertStagedBundle(
                bundle_id = bundle.operation.operationId.value.copyBytes(),
                bundle_bytes = bundle.bundle.copyBytes(),
                operation_bytes = bundle.operationBytes.copyBytes(),
                author_id = bundle.operation.authorId.value.copyBytes(),
                author_sequence = bundle.operation.authorSequence,
                public_key = bundle.operation.publicSigningKey.copyBytes(),
            )
            advanceState(expectedRevision, clockState, transportProgress ?: current.transportProgress)
        }
    }

    override suspend fun commitTransportProgress(
        expectedRevision: Long,
        transportProgress: OpaqueTransportProgress,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        return mutate(expectedRevision) { current ->
            advanceState(expectedRevision, current.clockState, transportProgress)
        }
    }

    override suspend fun markTerminalExpiry(
        expectedRevision: Long,
        sessionId: SessionId,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        return mutate(expectedRevision) { current ->
            database.syncReplicaQueries.insertTerminalExpiry(sessionId.value.copyBytes())
            advanceState(expectedRevision, current.clockState, current.transportProgress)
        }
    }

    private suspend fun mutate(
        expectedRevision: Long,
        mutation: suspend (SyncReplicaSnapshot) -> Unit,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        if (expectedRevision == Long.MAX_VALUE) {
            return SyncStoreResult.Failure(SyncStoreFailure.REVISION_EXHAUSTED)
        }

        return databaseCall {
            database.transactionWithResult {
                val context = snapshotReader.readContextOrThrow()
                val current = snapshotReader.readSnapshotOrThrow(context)
                if (current.revision != expectedRevision) {
                    failStore(SyncStoreFailure.REVISION_CONFLICT)
                }
                mutation(current)
                snapshotReader.readSnapshotOrThrow(context)
            }
        }
    }

    private suspend fun insertAccepted(bundle: PreparedStoredBundle) {
        database.syncReplicaQueries.insertAcceptedBundle(
            bundle_id = bundle.operation.operationId.value.copyBytes(),
            bundle_bytes = bundle.bundle.copyBytes(),
            operation_bytes = bundle.operationBytes.copyBytes(),
            author_id = bundle.operation.authorId.value.copyBytes(),
            author_sequence = bundle.operation.authorSequence,
            public_key = bundle.operation.publicSigningKey.copyBytes(),
            hlc_physical = bundle.operation.clock.physicalMillis,
            hlc_logical = bundle.operation.clock.logicalCounter.toLong(),
        )
    }

    private suspend fun advanceState(
        expectedRevision: Long,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ) {
        val changed = database.syncReplicaQueries.advanceSyncReplicaState(
            next_revision = expectedRevision + 1,
            hlc_physical = clockState.last.physicalMillis,
            hlc_logical = clockState.last.logicalCounter.toLong(),
            hlc_exhausted = if (clockState.isExhausted) 1 else 0,
            transport_progress = transportProgress?.copyBytes(),
            expected_revision = expectedRevision,
        )
        if (changed != 1L) {
            failStore(SyncStoreFailure.REVISION_CONFLICT)
        }
    }

    private suspend fun <T> databaseCall(block: suspend () -> T): SyncStoreResult<T> {
        return withContext(databaseDispatcher) {
            try {
                SyncStoreResult.Success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: SyncStoreException) {
                SyncStoreResult.Failure(failure.reason)
            } catch (_: Exception) {
                SyncStoreResult.Failure(SyncStoreFailure.STORAGE_FAILURE)
            }
        }
    }
}

private class SqlSnapshotReader(
    private val database: PosatoDatabase,
) {
    suspend fun readSnapshotOrThrow(expectedContext: SyncContext): SyncReplicaSnapshot {
        val stateRows = readStateRows()
        if (stateRows.size != 1) {
            failStore(SyncStoreFailure.CORRUPTION)
        }
        val state = stateRows.single()
        if (state.context != expectedContext) {
            failStore(SyncStoreFailure.WRONG_CONTEXT)
        }
        val accepted = readAcceptedBundles()
        val staged = readStagedBundles()
        val pending = readPendingBundles(accepted)
        val expiryFacts = readTerminalExpiryFacts()

        return SyncReplicaSnapshot(
            context = state.context,
            revision = state.revision,
            clockState = state.clockState,
            acceptedBundles = accepted,
            stagedBundles = staged,
            pendingBundles = pending,
            terminalExpiryFacts = expiryFacts,
            transportProgress = state.transportProgress,
        )
    }

    suspend fun readStateRows(): List<StateRow> {
        return database.syncReplicaQueries
            .selectSyncReplicaState { workspaceId, transportEpochId, keyEpochId, revision, physical, logical, exhausted, progress ->
                val context = restoreContext(workspaceId, transportEpochId, keyEpochId)
                    ?: failStore(SyncStoreFailure.CORRUPTION)
                if (revision < 0 || logical !in 0..SyncFormatLimits.MAX_LOGICAL_COUNTER.toLong() || exhausted !in 0..1) {
                    failStore(SyncStoreFailure.CORRUPTION)
                }
                StateRow(
                    context = context,
                    revision = revision,
                    clockState = DurableClockState(HybridLogicalClock(physical, logical.toInt()), exhausted == 1L),
                    transportProgress = progress?.let(::OpaqueTransportProgress),
                )
            }
            .awaitAsList()
    }

    suspend fun readContextOrThrow(): SyncContext {
        val states = readStateRows()
        if (states.size != 1) {
            failStore(SyncStoreFailure.CORRUPTION)
        }

        return states.single().context
    }

    private suspend fun readAcceptedBundles(): Map<BundleId, StoredAcceptedBundle> {
        val rows = database.syncReplicaQueries
            .selectAcceptedBundles { bundleId, bundleBytes, operationBytes, authorId, sequence, publicKey, physical, logical ->
                restoreStoredAccepted(bundleId, bundleBytes, operationBytes, authorId, sequence, publicKey, physical, logical)
                    ?: failStore(SyncStoreFailure.CORRUPTION)
            }
            .awaitAsList()

        return rows.associateBy { stored -> stored.operation.operationId }
    }

    private suspend fun readStagedBundles(): Map<BundleId, StoredStagedBundle> {
        val rows = database.syncReplicaQueries
            .selectStagedBundles { bundleId, bundleBytes, operationBytes, authorId, sequence, publicKey ->
                restoreStoredStaged(bundleId, bundleBytes, operationBytes, authorId, sequence, publicKey)
                    ?: failStore(SyncStoreFailure.CORRUPTION)
            }
            .awaitAsList()

        return rows.associateBy { stored -> stored.operation.operationId }
    }

    private suspend fun readPendingBundles(accepted: Map<BundleId, StoredAcceptedBundle>,): Map<BundleId, EncryptedBundle> {
        val rows = database.syncReplicaQueries
            .selectPendingBundles { bundleId, bundleBytes ->
                val id = SyncIdentifier.fromUuidV4Bytes(bundleId)?.let(::BundleId)
                    ?: failStore(SyncStoreFailure.CORRUPTION)
                val acceptedBundle = accepted[id] ?: failStore(SyncStoreFailure.CORRUPTION)
                val bundle = EncryptedBundle(bundleBytes)
                if (bundle != acceptedBundle.bundle) {
                    failStore(SyncStoreFailure.CORRUPTION)
                }
                id to bundle
            }
            .awaitAsList()

        return rows.toMap()
    }

    private suspend fun readTerminalExpiryFacts(): Set<SessionId> {
        return database.syncReplicaQueries
            .selectTerminalExpirySessions()
            .awaitAsList()
            .map { bytes -> SyncIdentifier.fromUuidV4Bytes(bytes)?.let(::SessionId) ?: failStore(SyncStoreFailure.CORRUPTION) }
            .toSet()
    }
}

private data class StateRow(
    val context: SyncContext,
    val revision: Long,
    val clockState: DurableClockState,
    val transportProgress: OpaqueTransportProgress?,
)

private fun restoreContext(
    workspaceId: ByteArray,
    transportEpochId: ByteArray,
    keyEpochId: ByteArray,
): SyncContext? {
    val workspace = SyncIdentifier.fromUuidV4Bytes(workspaceId)?.let(::WorkspaceId)
    val transportEpoch = SyncIdentifier.fromUuidV4Bytes(transportEpochId)?.let(::TransportEpochId)
    val keyEpoch = SyncIdentifier.fromUuidV4Bytes(keyEpochId)?.let(::KeyEpochId)

    return if (workspace != null && transportEpoch != null && keyEpoch != null) {
        SyncContext(workspace, transportEpoch, keyEpoch)
    } else {
        null
    }
}

private fun restoreStoredAccepted(
    bundleId: ByteArray,
    bundleBytes: ByteArray,
    operationBytes: ByteArray,
    authorId: ByteArray,
    sequence: Long,
    publicKey: ByteArray,
    physical: Long,
    logical: Long,
): StoredAcceptedBundle? {
    val operation = restoreStoredOperation(bundleId, operationBytes, authorId, sequence, publicKey, physical, logical) ?: return null

    return StoredAcceptedBundle(EncryptedBundle(bundleBytes), ImmutableBytes(operationBytes), operation)
}

private fun restoreStoredStaged(
    bundleId: ByteArray,
    bundleBytes: ByteArray,
    operationBytes: ByteArray,
    authorId: ByteArray,
    sequence: Long,
    publicKey: ByteArray,
): StoredStagedBundle? {
    val operation = SyncOperationCodec.decode(operationBytes)
    val expectedBundleId = SyncIdentifier.fromUuidV4Bytes(bundleId)?.let(::BundleId)
    val expectedAuthorId = SyncIdentifier.fromUuidV4Bytes(authorId)?.let(::AuthorId)
    val expectedPublicKey = PublicSigningKey.fromBytes(publicKey)
    val identityMatches = operation != null && operation.operationId == expectedBundleId && operation.authorId == expectedAuthorId
    val authorMatches = operation?.publicSigningKey == expectedPublicKey && operation?.authorSequence == sequence

    return operation
        ?.takeIf { identityMatches && authorMatches && sequence > 1 }
        ?.let { StoredStagedBundle(EncryptedBundle(bundleBytes), ImmutableBytes(operationBytes), it) }
}

private fun restoreStoredOperation(
    bundleId: ByteArray,
    operationBytes: ByteArray,
    authorId: ByteArray,
    sequence: Long,
    publicKey: ByteArray,
    physical: Long,
    logical: Long,
): app.posato.feature.sync.domain.SyncOperation? {
    val operation = SyncOperationCodec.decode(operationBytes)
    val expectedBundleId = SyncIdentifier.fromUuidV4Bytes(bundleId)?.let(::BundleId)
    val expectedAuthorId = SyncIdentifier.fromUuidV4Bytes(authorId)?.let(::AuthorId)
    val expectedPublicKey = PublicSigningKey.fromBytes(publicKey)
    val identityMatches = operation != null && operation.operationId == expectedBundleId && operation.authorId == expectedAuthorId
    val authorMatches = operation?.authorSequence == sequence && operation.publicSigningKey == expectedPublicKey
    val clockMatches = operation?.clock?.physicalMillis == physical && operation.clock.logicalCounter.toLong() == logical

    return operation?.takeIf { identityMatches && authorMatches && clockMatches }
}

private class SyncStoreException(
    val reason: SyncStoreFailure,
) : Exception()

private fun failStore(reason: SyncStoreFailure): Nothing {
    throw SyncStoreException(reason)
}
