package app.posato.feature.sync.domain

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.DurableClockState
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.ImmutableBytes
import app.posato.feature.sync.data.OpaqueTransportProgress
import app.posato.feature.sync.data.PrepareBundleResult
import app.posato.feature.sync.data.PreparedStoredBundle
import app.posato.feature.sync.data.StoredAcceptedBundle
import app.posato.feature.sync.data.StoredStagedBundle
import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.data.SyncReplicaStore
import app.posato.feature.sync.data.SyncStoreFailure
import app.posato.feature.sync.data.SyncStoreResult
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testTransportProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.test.assertIs

internal class FakeSyncReplicaStore(
    initial: SyncReplicaSnapshot,
    private val localCommitMode: LocalCommitMode = LocalCommitMode.SUCCESS,
    private val remoteCommitMode: RemoteCommitMode = RemoteCommitMode.SUCCESS,
    private val expiryCommitMode: LocalCommitMode = LocalCommitMode.SUCCESS,
    private val beforeOpen: suspend () -> Unit = {},
    private val afterLocalCommit: suspend () -> Unit = {},
    private val afterRemoteCommit: suspend () -> Unit = {},
    private val afterExpiryCommit: suspend () -> Unit = {},
    private val remoteReconciliationReadFailure: Exception? = null,
) : SyncReplicaStore {
    private var localCommitAttempts = 0
    private var expiryCommitAttempts = 0
    private var remoteCommitCompleted = false
    var current = initial
        private set

    override suspend fun open(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot> {
        beforeOpen()
        return SyncStoreResult.Success(current)
    }

    override suspend fun read(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot> {
        currentCoroutineContext().ensureActive()
        if (remoteCommitCompleted) remoteReconciliationReadFailure?.let { throw it }
        return SyncStoreResult.Success(current)
    }

    override suspend fun acknowledgePublication(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundleId: BundleId,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        checkpointConflict(expectedCheckpoint)?.let { return it }
        current = current.copy(revision = current.revision + 1, pendingBundles = current.pendingBundles - bundleId)
        return remoteResult()
    }

    override suspend fun commitLocal(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundles: List<PreparedStoredBundle>,
        clockState: DurableClockState,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        checkpointConflict(expectedCheckpoint)?.let { return it }
        localCommitAttempts += 1
        if (localCommitMode == LocalCommitMode.CANCELLED) {
            throw CancellationException("Synthetic local commit cancellation")
        }
        return if (localCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE && localCommitAttempts == 1) {
            SyncStoreResult.Failure(SyncStoreFailure.AMBIGUOUS_RESULT)
        } else {
            val accepted = current.acceptedBundles.toMutableMap()
            val pending = current.pendingBundles.toMutableMap()
            bundles.forEach { prepared ->
                accepted[prepared.operation.operationId] = StoredAcceptedBundle(
                    prepared.bundle,
                    prepared.operationBytes,
                    prepared.operation,
                )
                pending[prepared.operation.operationId] = prepared.bundle
            }
            current = current.copy(
                revision = current.revision + 1,
                clockState = clockState,
                acceptedBundles = accepted,
                pendingBundles = pending,
                transportProgress = if (localCommitMode == LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE) {
                    testTransportProgress(99)
                } else {
                    current.transportProgress
                },
            )
            afterLocalCommit()

            if (localCommitMode == LocalCommitMode.SUCCESS || localCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE) {
                SyncStoreResult.Success(current)
            } else {
                SyncStoreResult.Failure(SyncStoreFailure.AMBIGUOUS_RESULT)
            }
        }
    }

    override suspend fun commitAcceptedRemote(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundles: List<PreparedStoredBundle>,
        stagedBundleIdsToDelete: Set<BundleId>,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        checkpointConflict(expectedCheckpoint)?.let { return it }
        val accepted = current.acceptedBundles.toMutableMap()
        bundles.forEach { prepared ->
            accepted[prepared.operation.operationId] = StoredAcceptedBundle(
                prepared.bundle,
                prepared.operationBytes,
                prepared.operation,
            )
        }
        current = current.copy(
            revision = current.revision + 1,
            clockState = clockState,
            acceptedBundles = accepted,
            stagedBundles = current.stagedBundles - stagedBundleIdsToDelete,
            transportProgress = transportProgress ?: current.transportProgress,
        )

        return remoteResult()
    }

    override suspend fun commitStagedRemote(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundle: PreparedStoredBundle,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        checkpointConflict(expectedCheckpoint)?.let { return it }
        current = current.copy(
            revision = current.revision + 1,
            clockState = clockState,
            stagedBundles = current.stagedBundles + (
                bundle.operation.operationId to StoredStagedBundle(
                    bundle.bundle,
                    bundle.operationBytes,
                    bundle.operation,
                )
            ),
            transportProgress = transportProgress ?: current.transportProgress,
        )

        return remoteResult()
    }

    override suspend fun commitTransportProgress(
        expectedCheckpoint: SyncReplicaSnapshot,
        transportProgress: OpaqueTransportProgress,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        checkpointConflict(expectedCheckpoint)?.let { return it }
        current = current.copy(revision = current.revision + 1, transportProgress = transportProgress)

        return remoteResult()
    }

    override suspend fun markTerminalExpiry(
        expectedCheckpoint: SyncReplicaSnapshot,
        sessionId: SessionId,
    ): SyncStoreResult<SyncReplicaSnapshot> {
        checkpointConflict(expectedCheckpoint)?.let { return it }
        expiryCommitAttempts += 1
        return if (expiryCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE && expiryCommitAttempts == 1) {
            SyncStoreResult.Failure(SyncStoreFailure.AMBIGUOUS_RESULT)
        } else {
            current = current.copy(
                revision = current.revision + 1,
                terminalExpiryFacts = current.terminalExpiryFacts + sessionId,
                transportProgress = if (expiryCommitMode == LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE) {
                    testTransportProgress(99)
                } else {
                    current.transportProgress
                },
            )
            afterExpiryCommit()
            if (expiryCommitMode == LocalCommitMode.SUCCESS || expiryCommitMode == LocalCommitMode.AMBIGUOUS_ABSENT_ONCE) {
                SyncStoreResult.Success(current)
            } else {
                SyncStoreResult.Failure(SyncStoreFailure.AMBIGUOUS_RESULT)
            }
        }
    }

    private fun checkpointConflict(expectedCheckpoint: SyncReplicaSnapshot): SyncStoreResult.Failure? {
        return if (current == expectedCheckpoint) null else SyncStoreResult.Failure(SyncStoreFailure.REVISION_CONFLICT)
    }

    private suspend fun remoteResult(): SyncStoreResult<SyncReplicaSnapshot> {
        remoteCommitCompleted = true
        afterRemoteCommit()
        return when (remoteCommitMode) {
            RemoteCommitMode.SUCCESS -> {
                SyncStoreResult.Success(current)
            }

            RemoteCommitMode.AMBIGUOUS_EXACT -> {
                SyncStoreResult.Failure(SyncStoreFailure.AMBIGUOUS_RESULT)
            }

            RemoteCommitMode.AMBIGUOUS_WITH_EXTRA_STATE -> {
                current = current.copy(
                    terminalExpiryFacts = current.terminalExpiryFacts + SessionId(testIdentifier(96)),
                )
                SyncStoreResult.Failure(SyncStoreFailure.AMBIGUOUS_RESULT)
            }
        }
    }
}

internal enum class LocalCommitMode {
    SUCCESS,
    CANCELLED,
    AMBIGUOUS_EXACT,
    AMBIGUOUS_ABSENT_ONCE,
    AMBIGUOUS_WITH_EXTRA_STATE,
}

internal enum class RemoteCommitMode {
    SUCCESS,
    AMBIGUOUS_EXACT,
    AMBIGUOUS_WITH_EXTRA_STATE,
}

internal fun snapshot(clockState: DurableClockState = DurableClockState(HybridLogicalClock(0, 0), false)): SyncReplicaSnapshot {
    return SyncReplicaSnapshot(
        context = testContext,
        revision = 0,
        clockState = clockState,
        acceptedBundles = emptyMap(),
        stagedBundles = emptyMap(),
        pendingBundles = emptyMap(),
        terminalExpiryFacts = emptySet(),
        transportProgress = null,
    )
}

internal fun transportKey(): TransportKey {
    return checkNotNull(TransportKey.fromBytes(ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES) { 3 }))
}

internal fun remoteBundle(
    provider: FakeSyncCryptoProvider,
    operation: SyncOperation,
): EncryptedBundle {
    val signingKey = checkNotNull(provider.createSigningKey())
    return try {
        val prepared = EncryptedBundleCodec(provider).prepare(
            operation,
            transportKey(),
            signingKey,
            ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { 4 },
        )
        assertIs<PrepareBundleResult.Success>(prepared).bundle
    } finally {
        signingKey.close()
    }
}

internal fun acceptedSnapshot(
    provider: FakeSyncCryptoProvider,
    operations: List<SyncOperation>,
): SyncReplicaSnapshot {
    val lastClock = checkNotNull(operations.maxOfOrNull { operation -> operation.clock })
    val accepted = operations.map { operation -> storedAcceptedBundle(provider, operation) }
        .associateBy { stored -> stored.operation.operationId }
    return snapshot(DurableClockState(lastClock, lastClock.successor() == null)).copy(acceptedBundles = accepted)
}

internal fun storedAcceptedBundle(
    provider: FakeSyncCryptoProvider,
    operation: SyncOperation,
): StoredAcceptedBundle {
    return StoredAcceptedBundle(
        remoteBundle(provider, operation),
        ImmutableBytes(checkNotNull(app.posato.feature.sync.data.SyncOperationCodec.encode(operation))),
        operation,
    )
}
