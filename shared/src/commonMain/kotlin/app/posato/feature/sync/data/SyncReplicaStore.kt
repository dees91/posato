package app.posato.feature.sync.data

import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperation

internal class OpaqueTransportProgress private constructor(
    private val bytes: ByteArray,
) {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    override fun equals(other: Any?): Boolean {
        return other is OpaqueTransportProgress && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "OpaqueTransportProgress(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): OpaqueTransportProgress? {
            return bytes
                .takeIf { value -> value.size <= SyncFormatLimits.MAX_TRANSPORT_PROGRESS_BYTES }
                ?.copyOf()
                ?.let(::OpaqueTransportProgress)
        }
    }
}

internal data class DurableClockState(
    val last: HybridLogicalClock,
    val isExhausted: Boolean,
)

internal data class StoredAcceptedBundle(
    val bundle: EncryptedBundle,
    val operationBytes: ImmutableBytes,
    val operation: SyncOperation,
)

internal data class StoredStagedBundle(
    val bundle: EncryptedBundle,
    val operationBytes: ImmutableBytes,
    val operation: SyncOperation,
)

internal data class SyncReplicaSnapshot(
    val context: SyncContext,
    val revision: Long,
    val clockState: DurableClockState,
    val acceptedBundles: Map<BundleId, StoredAcceptedBundle>,
    val stagedBundles: Map<BundleId, StoredStagedBundle>,
    val pendingBundles: Map<BundleId, EncryptedBundle>,
    val terminalExpiryFacts: Set<SessionId>,
    val transportProgress: OpaqueTransportProgress?,
) {
    override fun toString(): String {
        return "SyncReplicaSnapshot(redacted)"
    }
}

internal class ImmutableBytes(
    bytes: ByteArray,
) {
    private val bytes = bytes.copyOf()

    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    fun contentEquals(other: ByteArray): Boolean {
        return bytes.contentEquals(other)
    }

    override fun equals(other: Any?): Boolean {
        return other is ImmutableBytes && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "ImmutableBytes(redacted)"
    }
}

internal data class PreparedStoredBundle(
    val bundle: EncryptedBundle,
    val operation: SyncOperation,
) {
    val operationBytes = checkNotNull(SyncOperationCodec.encode(operation)).useAndClear(::ImmutableBytes)
}

internal sealed interface SyncStoreResult<out T> {
    data class Success<T>(
        val value: T,
    ) : SyncStoreResult<T>

    data class Failure(
        val reason: SyncStoreFailure,
    ) : SyncStoreResult<Nothing>
}

internal enum class SyncStoreFailure {
    WRONG_CONTEXT,
    REVISION_CONFLICT,
    REVISION_EXHAUSTED,
    CORRUPTION,
    STORAGE_FAILURE,
    AMBIGUOUS_RESULT,
}

internal interface SyncReplicaStore {
    suspend fun open(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot>

    suspend fun read(context: SyncContext): SyncStoreResult<SyncReplicaSnapshot>

    suspend fun acknowledgePublication(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundleId: BundleId,
    ): SyncStoreResult<SyncReplicaSnapshot>

    suspend fun commitLocal(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundles: List<PreparedStoredBundle>,
        clockState: DurableClockState,
    ): SyncStoreResult<SyncReplicaSnapshot>

    suspend fun commitAcceptedRemote(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundles: List<PreparedStoredBundle>,
        stagedBundleIdsToDelete: Set<BundleId>,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot>

    suspend fun commitStagedRemote(
        expectedCheckpoint: SyncReplicaSnapshot,
        bundle: PreparedStoredBundle,
        clockState: DurableClockState,
        transportProgress: OpaqueTransportProgress?,
    ): SyncStoreResult<SyncReplicaSnapshot>

    suspend fun commitTransportProgress(
        expectedCheckpoint: SyncReplicaSnapshot,
        transportProgress: OpaqueTransportProgress,
    ): SyncStoreResult<SyncReplicaSnapshot>

    suspend fun markTerminalExpiry(
        expectedCheckpoint: SyncReplicaSnapshot,
        sessionId: SessionId,
    ): SyncStoreResult<SyncReplicaSnapshot>
}
