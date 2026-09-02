package app.posato.feature.sync.domain

import app.posato.feature.sync.data.BundleHeader
import app.posato.feature.sync.data.DecodeBundleResult
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.InspectBundleHeaderResult
import app.posato.feature.sync.data.PreparedStoredBundle
import app.posato.feature.sync.data.RemoteBundleFailure
import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.data.SyncReplicaStore

internal sealed interface RemoteBundleClassification {
    data class Failure(
        val result: RemoteAcceptanceResult.Failure
    ) : RemoteBundleClassification

    data object Duplicate : RemoteBundleClassification

    data class Registration(
        val operation: SyncOperation
    ) : RemoteBundleClassification

    data class UnknownAuthor(
        val operation: SyncOperation
    ) : RemoteBundleClassification

    data class KnownAuthor(
        val operation: SyncOperation
    ) : RemoteBundleClassification
}

internal data class RemoteCommitOutcome(
    val result: RemoteAcceptanceResult,
    val snapshot: SyncReplicaSnapshot? = null,
    val commitUncertain: Boolean = false,
)

internal class RemoteBundleClassifier(
    private val codec: EncryptedBundleCodec,
    private val transportKey: TransportKey,
) {
    fun classify(
        snapshot: SyncReplicaSnapshot,
        bundle: EncryptedBundle
    ): RemoteBundleClassification {
        val inspected = codec.inspectHeader(bundle)
        val header = (inspected as? InspectBundleHeaderResult.Success)?.header
        val inspectionFailure = (inspected as? InspectBundleHeaderResult.Failure)?.reason
        val existing = header?.let { snapshot.acceptedBundles[it.bundleId]?.bundle ?: snapshot.stagedBundles[it.bundleId]?.bundle }
        val decoded = header?.takeIf { it.context == snapshot.context && existing == null }
            ?.let { codec.decode(bundle, snapshot.context, transportKey) }
        val operation = (decoded as? DecodeBundleResult.Success)?.operation
        val decodingFailure = (decoded as? DecodeBundleResult.Failure)?.reason

        return classifyDecoded(snapshot, bundle, header, inspectionFailure, existing, operation, decodingFailure)
    }

    private fun classifyDecoded(
        snapshot: SyncReplicaSnapshot,
        bundle: EncryptedBundle,
        header: BundleHeader?,
        inspectionFailure: RemoteBundleFailure?,
        existing: EncryptedBundle?,
        operation: SyncOperation?,
        decodingFailure: RemoteBundleFailure?,
    ): RemoteBundleClassification = when {
        inspectionFailure != null -> {
            RemoteBundleClassification.Failure(inspectionFailure.toRemoteResult())
        }

        header?.context != snapshot.context -> {
            failure(RemoteAcceptanceFailure.WRONG_CONTEXT)
        }

        existing != null && existing != bundle -> {
            failure(RemoteAcceptanceFailure.REPLAY_CONFLICT)
        }

        existing != null -> {
            RemoteBundleClassification.Duplicate
        }

        decodingFailure != null -> {
            RemoteBundleClassification.Failure(decodingFailure.toRemoteResult())
        }

        operation == null -> {
            failure(RemoteAcceptanceFailure.INVALID_OPERATION)
        }

        hasSequenceConflict(snapshot, operation) -> {
            failure(RemoteAcceptanceFailure.SEQUENCE_CONFLICT)
        }

        operation.payload == SyncOperationPayload.AuthorRegister && operation.authorSequence != 1L -> {
            failure(RemoteAcceptanceFailure.INVALID_OPERATION)
        }

        operation.authorSequence == 1L -> {
            classifyRegistration(operation)
        }

        else -> {
            classifyKnownAuthor(snapshot, operation)
        }
    }

    private fun classifyRegistration(operation: SyncOperation): RemoteBundleClassification =
        if (operation.payload == SyncOperationPayload.AuthorRegister) {
            RemoteBundleClassification.Registration(operation)
        } else {
            failure(RemoteAcceptanceFailure.INVALID_OPERATION)
        }

    private fun classifyKnownAuthor(
        snapshot: SyncReplicaSnapshot,
        operation: SyncOperation
    ): RemoteBundleClassification {
        val registration = snapshot.acceptedBundles.values.map { it.operation }.firstOrNull {
            it.authorId == operation.authorId && it.authorSequence == 1L && it.payload == SyncOperationPayload.AuthorRegister
        }
        return when {
            registration == null -> RemoteBundleClassification.UnknownAuthor(operation)
            registration.publicSigningKey != operation.publicSigningKey -> failure(RemoteAcceptanceFailure.SEQUENCE_CONFLICT)
            operation.payload == SyncOperationPayload.AuthorRegister -> failure(RemoteAcceptanceFailure.SEQUENCE_CONFLICT)
            else -> RemoteBundleClassification.KnownAuthor(operation)
        }
    }

    private fun hasSequenceConflict(
        snapshot: SyncReplicaSnapshot,
        operation: SyncOperation
    ): Boolean = (snapshot.acceptedBundles.values.map { it.operation } + snapshot.stagedBundles.values.map { it.operation }).any {
        it.authorId == operation.authorId && it.authorSequence == operation.authorSequence
    }

    private fun failure(reason: RemoteAcceptanceFailure) = RemoteBundleClassification.Failure(RemoteAcceptanceResult.Failure(reason))
}

internal class RemoteBundleCommitter(
    private val store: SyncReplicaStore,
    private val reconciler: SyncCommitReconciler,
) {
    suspend fun failure(
        snapshot: SyncReplicaSnapshot,
        result: RemoteAcceptanceResult.Failure,
        receipt: RemoteTransportReceipt,
    ): RemoteCommitOutcome {
        val progress = receipt.progress?.takeIf { receipt.exactRefetchAvailable }
            ?: return RemoteCommitOutcome(result)
        val expected = snapshot.expectedAfterProgress(progress)
        val committed = reconciler.commitRemote(snapshot, expected) {
            store.commitTransportProgress(snapshot, progress)
        }

        return committed?.let { RemoteCommitOutcome(result, it) } ?: uncertain()
    }

    suspend fun duplicate(
        snapshot: SyncReplicaSnapshot,
        receipt: RemoteTransportReceipt
    ): RemoteCommitOutcome {
        val progress = receipt.progress ?: return RemoteCommitOutcome(RemoteAcceptanceResult.Duplicate(projection(snapshot)))
        val expected = snapshot.expectedAfterProgress(progress)
        val committed = reconciler.commitRemote(snapshot, expected) {
            store.commitTransportProgress(snapshot, progress)
        }
        return committedOutcome(committed) { RemoteAcceptanceResult.Duplicate(projection(it)) }
    }

    suspend fun unknownAuthor(
        snapshot: SyncReplicaSnapshot,
        bundle: EncryptedBundle,
        operation: SyncOperation,
        receipt: RemoteTransportReceipt,
    ): RemoteCommitOutcome {
        val stagedCount = snapshot.stagedBundles.values.count { it.operation.authorId == operation.authorId }
        val atCapacity = stagedCount >= SyncFormatLimits.MAX_UNKNOWN_AUTHOR_BUNDLES ||
            snapshot.stagedBundles.size >= SyncFormatLimits.MAX_STAGED_BUNDLES
        return if (atCapacity) {
            failure(snapshot, RemoteAcceptanceResult.Failure(RemoteAcceptanceFailure.DEFERRED_CAPACITY), receipt)
        } else {
            stage(snapshot, bundle, operation, receipt)
        }
    }

    suspend fun registration(
        snapshot: SyncReplicaSnapshot,
        bundle: EncryptedBundle,
        operation: SyncOperation,
        receipt: RemoteTransportReceipt,
    ): RemoteCommitOutcome {
        val staged = snapshot.stagedBundles.values.filter { it.operation.authorId == operation.authorId }
        val acceptedStaged = staged.filter {
            it.operation.publicSigningKey == operation.publicSigningKey &&
                it.operation.payload != SyncOperationPayload.AuthorRegister
        }.sortedBy { it.operation.authorSequence }.map { PreparedStoredBundle(it.bundle, it.operation) }
        val prepared = listOf(PreparedStoredBundle(bundle, operation)) + acceptedStaged
        val clock = advanceRemoteClock(snapshot.clockState, prepared.map { it.operation })
        val stagedIds = staged.mapTo(mutableSetOf()) { it.operation.operationId }
        val expected = snapshot.expectedAfterAccepted(prepared, stagedIds, clock, receipt.progress)
        val committed = reconciler.commitRemote(snapshot, expected) {
            store.commitAcceptedRemote(snapshot, prepared, stagedIds, clock, receipt.progress)
        }
        return committedOutcome(committed) { RemoteAcceptanceResult.Accepted(projection(it)) }
    }

    suspend fun knownAuthor(
        snapshot: SyncReplicaSnapshot,
        bundle: PreparedStoredBundle,
        receipt: RemoteTransportReceipt,
    ): RemoteCommitOutcome {
        val clock = advanceRemoteClock(snapshot.clockState, listOf(bundle.operation))
        val bundles = listOf(bundle)
        val expected = snapshot.expectedAfterAccepted(bundles, emptySet(), clock, receipt.progress)
        val committed = reconciler.commitRemote(snapshot, expected) {
            store.commitAcceptedRemote(snapshot, bundles, emptySet(), clock, receipt.progress)
        }
        return committedOutcome(committed) { RemoteAcceptanceResult.Accepted(projection(it)) }
    }

    private suspend fun stage(
        snapshot: SyncReplicaSnapshot,
        bundle: EncryptedBundle,
        operation: SyncOperation,
        receipt: RemoteTransportReceipt,
    ): RemoteCommitOutcome {
        val prepared = PreparedStoredBundle(bundle, operation)
        val expected = snapshot.expectedAfterStaged(prepared, receipt.progress)
        val committed = reconciler.commitRemote(snapshot, expected) {
            store.commitStagedRemote(snapshot, prepared, snapshot.clockState, receipt.progress)
        }
        return committedOutcome(committed) { RemoteAcceptanceResult.Staged(projection(it)) }
    }

    private fun committedOutcome(
        snapshot: SyncReplicaSnapshot?,
        result: (SyncReplicaSnapshot) -> RemoteAcceptanceResult,
    ): RemoteCommitOutcome = snapshot?.let { RemoteCommitOutcome(result(it), it) } ?: uncertain()

    private fun uncertain() = RemoteCommitOutcome(
        RemoteAcceptanceResult.Failure(RemoteAcceptanceFailure.LOCAL_COMMIT_UNCERTAIN),
        commitUncertain = true,
    )

    private fun projection(snapshot: SyncReplicaSnapshot) = SyncReducer.reduce(snapshot.acceptedBundles.values.map { it.operation })
}
