package app.posato.feature.sync.domain

import app.posato.feature.sync.data.DurableClockState
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.InspectBundleHeaderResult
import app.posato.feature.sync.data.OpaqueTransportProgress
import app.posato.feature.sync.data.PrepareBundleResult
import app.posato.feature.sync.data.PreparedStoredBundle
import app.posato.feature.sync.data.RemoteBundleFailure
import app.posato.feature.sync.data.StoredAcceptedBundle
import app.posato.feature.sync.data.StoredStagedBundle
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.data.SyncReplicaStore
import app.posato.feature.sync.data.SyncSigningKey
import app.posato.feature.sync.data.SyncStoreFailure
import app.posato.feature.sync.data.SyncStoreResult
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun interface SyncWallClock {
    fun currentEpochMillis(): Long
}

internal sealed interface LocalSyncMutation {
    data class PresentDomain(
        val domain: ExactDomain,
    ) : LocalSyncMutation

    data class RemoveDomain(
        val domain: ExactDomain,
    ) : LocalSyncMutation

    data class PresentApplicationPolicy(
        val name: ApplicationPolicyName,
    ) : LocalSyncMutation

    data object RemoveApplicationPolicy : LocalSyncMutation

    data class StartSession(
        val sessionId: SessionId,
        val startEpochMillis: Long,
        val mandatoryEndEpochMillis: Long,
    ) : LocalSyncMutation {
        override fun toString(): String {
            return "LocalSyncMutation.StartSession(redacted)"
        }
    }

    data class EndSession(
        val sessionId: SessionId,
    ) : LocalSyncMutation
}

internal enum class LocalMutationFailure {
    CLOCK_OUT_OF_RANGE,
    HLC_EXHAUSTED,
    AUTHOR_SEQUENCE_EXHAUSTED,
    INVALID_MUTATION,
    CRYPTOGRAPHY_FAILURE,
    LOCAL_COMMIT_UNCERTAIN,
    STORAGE_FAILURE,
    WRITER_CLOSED,
}

internal sealed interface LocalMutationResult {
    data class Success(
        val pendingBundles: List<EncryptedBundle>,
        val projection: SyncProjection,
    ) : LocalMutationResult

    data class Failure(
        val reason: LocalMutationFailure,
    ) : LocalMutationResult
}

internal data class RemoteTransportReceipt(
    val progress: OpaqueTransportProgress?,
    val exactRefetchAvailable: Boolean,
)

internal enum class RemoteAcceptanceFailure {
    UNSUPPORTED_VERSION,
    MALFORMED,
    OVERSIZED,
    WRONG_CONTEXT,
    UNKNOWN_AUTHOR,
    INVALID_SIGNATURE,
    AUTHENTICATION_FAILED,
    REPLAY_CONFLICT,
    SEQUENCE_CONFLICT,
    INVALID_OPERATION,
    DEFERRED_CAPACITY,
    LOCAL_COMMIT_UNCERTAIN,
    STORAGE_FAILURE,
    WRITER_CLOSED,
}

internal sealed interface RemoteAcceptanceResult {
    data class Accepted(
        val projection: SyncProjection,
    ) : RemoteAcceptanceResult

    data class Staged(
        val projection: SyncProjection,
    ) : RemoteAcceptanceResult

    data class Duplicate(
        val projection: SyncProjection,
    ) : RemoteAcceptanceResult

    data class Failure(
        val reason: RemoteAcceptanceFailure,
    ) : RemoteAcceptanceResult
}

internal sealed interface OpenSyncWriterResult {
    data class Success(
        val writer: SyncWriter,
    ) : OpenSyncWriterResult

    data class Failure(
        val reason: OpenSyncWriterFailure,
    ) : OpenSyncWriterResult
}

internal enum class OpenSyncWriterFailure {
    ALREADY_OPEN,
    WRONG_CONTEXT,
    CORRUPTION,
    STORAGE_FAILURE,
}

internal class SyncOperationCore(
    private val store: SyncReplicaStore,
    private val cryptoProvider: SyncCryptoProvider,
    private val wallClock: SyncWallClock,
) {
    private val mutex = Mutex()
    private var activeWriter: SyncWriter? = null

    suspend fun open(
        context: SyncContext,
        transportKey: app.posato.feature.sync.domain.TransportKey,
    ): OpenSyncWriterResult {
        return mutex.withLock {
            if (activeWriter != null) {
                return@withLock OpenSyncWriterResult.Failure(OpenSyncWriterFailure.ALREADY_OPEN)
            }

            when (val result = store.open(context)) {
                is SyncStoreResult.Success -> {
                    if (!result.value.isAuthenticatedBy(cryptoProvider, transportKey)) {
                        transportKey.close()
                        return@withLock OpenSyncWriterResult.Failure(OpenSyncWriterFailure.CORRUPTION)
                    }
                    val writer = SyncWriter(
                        store = store,
                        cryptoProvider = cryptoProvider,
                        wallClock = wallClock,
                        transportKey = transportKey,
                        initialSnapshot = result.value,
                        onClose = ::release,
                    )
                    activeWriter = writer
                    OpenSyncWriterResult.Success(writer)
                }

                is SyncStoreResult.Failure -> {
                    transportKey.close()
                    OpenSyncWriterResult.Failure(result.reason.toOpenFailure())
                }
            }
        }
    }

    private suspend fun release(writer: SyncWriter) {
        mutex.withLock {
            if (activeWriter === writer) {
                activeWriter = null
            }
        }
    }
}

internal class SyncWriter internal constructor(
    private val store: SyncReplicaStore,
    private val cryptoProvider: SyncCryptoProvider,
    private val wallClock: SyncWallClock,
    private val transportKey: app.posato.feature.sync.domain.TransportKey,
    initialSnapshot: SyncReplicaSnapshot,
    private val onClose: suspend (SyncWriter) -> Unit,
) {
    private val mutex = Mutex()
    private val codec = EncryptedBundleCodec(cryptoProvider)
    private val localMutationPreparer = LocalMutationPreparer(cryptoProvider, codec, transportKey)
    private val commitReconciler = SyncCommitReconciler(store)
    private val remoteClassifier = RemoteBundleClassifier(codec, transportKey)
    private val remoteCommitter = RemoteBundleCommitter(store, commitReconciler)
    private var checkpoint = initialSnapshot
    private var authoringIncarnation: AuthoringIncarnation? = null
    private var state = WriterState.ACTIVE

    suspend fun mutate(mutation: LocalSyncMutation): LocalMutationResult {
        return mutex.withLock {
            if (state != WriterState.ACTIVE) {
                return@withLock LocalMutationResult.Failure(state.toLocalFailure())
            }
            if (!verifyCheckpoint()) {
                return@withLock LocalMutationResult.Failure(LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN)
            }
            val payload = mutation.toPayload()
                ?: return@withLock LocalMutationResult.Failure(LocalMutationFailure.INVALID_MUTATION)
            if (authoringIncarnation?.let { incarnation -> incarnation.nextSequence == null } == true) {
                freeze()
                return@withLock LocalMutationResult.Failure(LocalMutationFailure.AUTHOR_SEQUENCE_EXHAUSTED)
            }
            val wallTime = wallClock.currentEpochMillis()
            if (wallTime !in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS) {
                return@withLock LocalMutationResult.Failure(LocalMutationFailure.CLOCK_OUT_OF_RANGE)
            }
            val isFirstMutation = authoringIncarnation == null
            val reservedClocks = reserveLocalClocks(checkpoint.clockState, wallTime, if (isFirstMutation) 2 else 1)
            if (reservedClocks == null) {
                val exhausted = commitExhaustion()
                return@withLock if (exhausted) {
                    LocalMutationResult.Failure(LocalMutationFailure.HLC_EXHAUSTED)
                } else {
                    LocalMutationResult.Failure(LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN)
                }
            }
            val prepared = localMutationPreparer.prepare(payload, reservedClocks, authoringIncarnation, checkpoint.context)
                ?: return@withLock LocalMutationResult.Failure(freezeForPreparationFailure())
            val committedSnapshot = commitReconciler.commitLocal(checkpoint, prepared)
                ?: return@withLock LocalMutationResult.Failure(LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN).also { freeze() }
            checkpoint = committedSnapshot
            authoringIncarnation = prepared.nextIncarnation

            LocalMutationResult.Success(
                pendingBundles = prepared.bundles.map(PreparedStoredBundle::bundle),
                projection = projection(),
            )
        }
    }

    suspend fun acceptRemote(
        bundleBytes: ByteArray,
        receipt: RemoteTransportReceipt,
    ): RemoteAcceptanceResult {
        return mutex.withLock {
            if (state != WriterState.ACTIVE) {
                return@withLock RemoteAcceptanceResult.Failure(state.toRemoteFailure())
            }
            if (!verifyCheckpoint()) {
                return@withLock RemoteAcceptanceResult.Failure(RemoteAcceptanceFailure.LOCAL_COMMIT_UNCERTAIN)
            }
            val bundle = EncryptedBundle.fromBytes(bundleBytes)
            val outcome = if (bundle == null) {
                remoteCommitter.failure(checkpoint, RemoteBundleFailure.OVERSIZED.toRemoteResult(), receipt)
            } else {
                when (val classified = remoteClassifier.classify(checkpoint, bundle)) {
                    is RemoteBundleClassification.Failure -> {
                        remoteCommitter.failure(checkpoint, classified.result, receipt)
                    }

                    RemoteBundleClassification.Duplicate -> {
                        remoteCommitter.duplicate(checkpoint, receipt)
                    }

                    is RemoteBundleClassification.Registration -> {
                        remoteCommitter.registration(checkpoint, bundle, classified.operation, receipt)
                    }

                    is RemoteBundleClassification.UnknownAuthor -> {
                        remoteCommitter.unknownAuthor(checkpoint, bundle, classified.operation, receipt)
                    }

                    is RemoteBundleClassification.KnownAuthor -> {
                        remoteCommitter.knownAuthor(checkpoint, PreparedStoredBundle(bundle, classified.operation), receipt)
                    }
                }
            }
            outcome.snapshot?.let { checkpoint = it }
            if (outcome.commitUncertain) freeze()
            outcome.result
        }
    }

    fun projection(): SyncProjection {
        return SyncReducer.reduce(checkpoint.acceptedBundles.values.map { stored -> stored.operation })
    }

    fun pendingBundles(): List<EncryptedBundle> {
        return checkpoint.pendingBundles.values.toList()
    }

    fun evaluateSession(evaluationEpochMillis: Long): EffectiveSession {
        return SyncReducer.evaluateSession(projection(), evaluationEpochMillis, checkpoint.terminalExpiryFacts)
    }

    suspend fun markTerminalExpiry(sessionId: SessionId): Boolean {
        return mutex.withLock {
            if (state != WriterState.ACTIVE || !verifyCheckpoint()) {
                return@withLock false
            }
            val projection = projection()
            val applicableOperationIds = projection.audit
                .filterNot { entry -> entry.outcome == SyncAuditOutcome.SEQUENCE_GAP }
                .mapTo(mutableSetOf(), SyncAuditEntry::operationId)
            val retainsSessionStart = checkpoint.acceptedBundles.values.any { stored ->
                stored.operation.operationId in applicableOperationIds &&
                    (stored.operation.payload as? SyncOperationPayload.SessionStart)?.sessionId == sessionId
            }
            if (!retainsSessionStart) {
                return@withLock false
            }
            val expected = checkpoint.copy(
                revision = checkpoint.revision + 1,
                terminalExpiryFacts = checkpoint.terminalExpiryFacts + sessionId,
            )
            val committed = commitReconciler.commitRemote(checkpoint, expected) {
                store.markTerminalExpiry(checkpoint.revision, sessionId)
            }
            if (committed != null) checkpoint = committed else freeze()
            committed != null
        }
    }

    suspend fun close() {
        mutex.withLock {
            if (state != WriterState.CLOSED) {
                state = WriterState.CLOSED
                authoringIncarnation?.signingKey?.close()
                authoringIncarnation = null
                transportKey.close()
            }
        }
        withContext(NonCancellable) {
            onClose(this@SyncWriter)
        }
    }

    private suspend fun commitExhaustion(): Boolean {
        val exhaustedState = DurableClockState(
            HybridLogicalClock(SyncFormatLimits.MAX_PHYSICAL_MILLIS, SyncFormatLimits.MAX_LOGICAL_COUNTER),
            true,
        )
        val expected = checkpoint.copy(revision = checkpoint.revision + 1, clockState = exhaustedState)
        val committed = commitReconciler.commitRemote(checkpoint, expected) {
            store.commitLocal(checkpoint.revision, emptyList(), exhaustedState)
        }
        if (committed != null) checkpoint = committed else freeze()
        return committed != null
    }

    private suspend fun verifyCheckpoint(): Boolean {
        return when (val observed = store.read(checkpoint.context)) {
            is SyncStoreResult.Success -> {
                if (observed.value == checkpoint) {
                    true
                } else {
                    freeze()
                    false
                }
            }

            is SyncStoreResult.Failure -> {
                freeze()
                false
            }
        }
    }

    private fun freezeForPreparationFailure(): LocalMutationFailure {
        freeze()
        return LocalMutationFailure.CRYPTOGRAPHY_FAILURE
    }

    private fun freeze() {
        state = WriterState.FROZEN
        authoringIncarnation?.signingKey?.close()
        authoringIncarnation = null
    }
}

internal data class AuthoringIncarnation(
    val authorId: AuthorId,
    val signingKey: SyncSigningKey,
    val nextSequence: Long?,
)

internal data class PreparedLocalMutation(
    val bundles: List<PreparedStoredBundle>,
    val clockState: DurableClockState,
    val nextIncarnation: AuthoringIncarnation,
) {
    fun expectedAfter(snapshot: SyncReplicaSnapshot): SyncReplicaSnapshot {
        val accepted = snapshot.acceptedBundles.toMutableMap()
        val pending = snapshot.pendingBundles.toMutableMap()
        bundles.forEach { prepared ->
            accepted[prepared.operation.operationId] = StoredAcceptedBundle(
                bundle = prepared.bundle,
                operationBytes = prepared.operationBytes,
                operation = prepared.operation,
            )
            pending[prepared.operation.operationId] = prepared.bundle
        }

        return snapshot.copy(
            revision = snapshot.revision + 1,
            clockState = clockState,
            acceptedBundles = accepted,
            pendingBundles = pending,
        )
    }
}

internal fun SyncReplicaSnapshot.expectedAfterAccepted(
    bundles: List<PreparedStoredBundle>,
    stagedBundleIdsToDelete: Set<BundleId>,
    nextClockState: DurableClockState,
    nextProgress: OpaqueTransportProgress?,
): SyncReplicaSnapshot {
    val accepted = acceptedBundles.toMutableMap()
    bundles.forEach { prepared ->
        accepted[prepared.operation.operationId] = StoredAcceptedBundle(
            prepared.bundle,
            prepared.operationBytes,
            prepared.operation,
        )
    }

    return copy(
        revision = revision + 1,
        clockState = nextClockState,
        acceptedBundles = accepted,
        stagedBundles = stagedBundles - stagedBundleIdsToDelete,
        transportProgress = nextProgress ?: transportProgress,
    )
}

internal fun SyncReplicaSnapshot.expectedAfterStaged(
    bundle: PreparedStoredBundle,
    nextProgress: OpaqueTransportProgress?,
): SyncReplicaSnapshot {
    val staged = stagedBundles + (
        bundle.operation.operationId to StoredStagedBundle(
            bundle.bundle,
            bundle.operationBytes,
            bundle.operation,
        )
    )

    return copy(
        revision = revision + 1,
        stagedBundles = staged,
        transportProgress = nextProgress ?: transportProgress,
    )
}

internal fun SyncReplicaSnapshot.expectedAfterProgress(progress: OpaqueTransportProgress): SyncReplicaSnapshot {
    return copy(revision = revision + 1, transportProgress = progress)
}

private enum class WriterState {
    ACTIVE,
    FROZEN,
    CLOSED,
}

private fun WriterState.toLocalFailure(): LocalMutationFailure {
    return if (this == WriterState.CLOSED) LocalMutationFailure.WRITER_CLOSED else LocalMutationFailure.LOCAL_COMMIT_UNCERTAIN
}

private fun WriterState.toRemoteFailure(): RemoteAcceptanceFailure {
    return if (this == WriterState.CLOSED) RemoteAcceptanceFailure.WRITER_CLOSED else RemoteAcceptanceFailure.LOCAL_COMMIT_UNCERTAIN
}

private fun SyncStoreFailure.toOpenFailure(): OpenSyncWriterFailure {
    return when (this) {
        SyncStoreFailure.WRONG_CONTEXT -> OpenSyncWriterFailure.WRONG_CONTEXT
        SyncStoreFailure.CORRUPTION -> OpenSyncWriterFailure.CORRUPTION
        else -> OpenSyncWriterFailure.STORAGE_FAILURE
    }
}

internal fun RemoteBundleFailure.toRemoteResult(): RemoteAcceptanceResult.Failure {
    val failure = when (this) {
        RemoteBundleFailure.UNSUPPORTED_VERSION -> RemoteAcceptanceFailure.UNSUPPORTED_VERSION
        RemoteBundleFailure.MALFORMED -> RemoteAcceptanceFailure.MALFORMED
        RemoteBundleFailure.OVERSIZED -> RemoteAcceptanceFailure.OVERSIZED
        RemoteBundleFailure.WRONG_CONTEXT -> RemoteAcceptanceFailure.WRONG_CONTEXT
        RemoteBundleFailure.INVALID_SIGNATURE -> RemoteAcceptanceFailure.INVALID_SIGNATURE
        RemoteBundleFailure.AUTHENTICATION_FAILED -> RemoteAcceptanceFailure.AUTHENTICATION_FAILED
        RemoteBundleFailure.INVALID_OPERATION -> RemoteAcceptanceFailure.INVALID_OPERATION
    }

    return RemoteAcceptanceResult.Failure(failure)
}

internal fun nextAuthorSequence(current: Long): Long? {
    return current.takeIf { sequence -> sequence < Long.MAX_VALUE }?.plus(1)
}

internal fun advanceRemoteClock(
    current: DurableClockState,
    operations: List<SyncOperation>,
): DurableClockState {
    if (current.isExhausted) {
        return current
    }
    var state = current
    operations.sortedBy { operation -> operation.clock }.forEach { operation ->
        val greater = maxOf(state.last, operation.clock)
        val successor = greater.successor()
        state = when {
            successor == null -> DurableClockState(greater, true)
            successor.successor() == null -> DurableClockState(successor, true)
            else -> DurableClockState(successor, false)
        }
    }

    return state
}

internal fun LocalSyncMutation.toPayload(): SyncOperationPayload? {
    return when (this) {
        is LocalSyncMutation.PresentDomain -> SyncOperationPayload.DomainPresent(domain)

        is LocalSyncMutation.RemoveDomain -> SyncOperationPayload.DomainAbsent(domain)

        is LocalSyncMutation.PresentApplicationPolicy -> SyncOperationPayload.ApplicationPolicyPresent(name)

        LocalSyncMutation.RemoveApplicationPolicy -> SyncOperationPayload.ApplicationPolicyAbsent

        is LocalSyncMutation.StartSession -> SyncOperationPayload.SessionStart(sessionId, startEpochMillis, mandatoryEndEpochMillis)
            .takeIf { payload ->
                val duration = payload.mandatoryEndEpochMillis - payload.startEpochMillis
                payload.startEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS &&
                    payload.mandatoryEndEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS &&
                    duration in 1..SyncFormatLimits.MAX_SESSION_DURATION_MILLIS
            }

        is LocalSyncMutation.EndSession -> SyncOperationPayload.SessionEnd(sessionId)
    }
}
