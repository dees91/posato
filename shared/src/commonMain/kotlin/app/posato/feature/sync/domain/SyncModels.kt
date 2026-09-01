package app.posato.feature.sync.domain

import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import kotlin.jvm.JvmInline

internal object SyncFormatLimits {
    const val COMPLETE_BUNDLE_BYTES: Int = 65_536
    const val MAX_TRANSPORT_PROGRESS_BYTES: Int = 65_536
    const val HEADER_BYTES: Int = 108
    const val PLAINTEXT_BYTES: Int = 32_768
    const val IDENTIFIER_BYTES: Int = 16
    const val PUBLIC_KEY_BYTES: Int = 32
    const val SIGNATURE_BYTES: Int = 64
    const val BUNDLE_SALT_BYTES: Int = 32
    const val TRANSPORT_KEY_BYTES: Int = 32
    const val AES_KEY_BYTES: Int = 32
    const val AES_NONCE_BYTES: Int = 12
    const val AES_TAG_BYTES: Int = 16
    const val MAX_SYNCHRONIZED_DOMAINS: Int = 2_048
    const val MAX_UNKNOWN_AUTHOR_BUNDLES: Int = 32
    const val MAX_STAGED_BUNDLES: Int = 128
    const val MAX_PHYSICAL_MILLIS: Long = 4_102_444_800_000L
    const val MAX_LOGICAL_COUNTER: Int = 65_535
    const val MAX_SESSION_DURATION_MILLIS: Long = 86_400_000L
}

internal class SyncIdentifier private constructor(
    private val bytes: ByteArray,
) : Comparable<SyncIdentifier> {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    fun isUuidV4(): Boolean {
        val hasVersionFour = bytes[6].toInt() and 0xF0 == 0x40
        val hasRfcVariant = bytes[8].toInt() and 0xC0 == 0x80

        return hasVersionFour && hasRfcVariant
    }

    fun isZero(): Boolean {
        return bytes.all { value -> value == 0.toByte() }
    }

    override fun compareTo(other: SyncIdentifier): Int {
        for (index in bytes.indices) {
            val comparison = (bytes[index].toInt() and 0xFF).compareTo(other.bytes[index].toInt() and 0xFF)
            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    override fun equals(other: Any?): Boolean {
        return other is SyncIdentifier && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "SyncIdentifier(redacted)"
    }

    companion object {
        fun fromUuidV4Bytes(bytes: ByteArray): SyncIdentifier? {
            val identifier = fromExactBytes(bytes) ?: return null

            return identifier.takeIf(SyncIdentifier::isUuidV4)
        }

        fun fromExactBytes(bytes: ByteArray): SyncIdentifier? {
            return if (bytes.size == SyncFormatLimits.IDENTIFIER_BYTES) {
                SyncIdentifier(bytes.copyOf())
            } else {
                null
            }
        }

        fun zero(): SyncIdentifier {
            return SyncIdentifier(ByteArray(SyncFormatLimits.IDENTIFIER_BYTES))
        }
    }
}

@JvmInline
internal value class BundleId(
    val value: SyncIdentifier,
)

@JvmInline
internal value class WorkspaceId(
    val value: SyncIdentifier,
)

@JvmInline
internal value class TransportEpochId(
    val value: SyncIdentifier,
)

@JvmInline
internal value class KeyEpochId(
    val value: SyncIdentifier,
)

@JvmInline
internal value class AuthorId(
    val value: SyncIdentifier,
)

@JvmInline
internal value class SessionId(
    val value: SyncIdentifier,
)

internal data class SyncContext(
    val workspaceId: WorkspaceId,
    val transportEpochId: TransportEpochId,
    val keyEpochId: KeyEpochId,
)

internal data class HybridLogicalClock(
    val physicalMillis: Long,
    val logicalCounter: Int,
) : Comparable<HybridLogicalClock> {
    init {
        require(physicalMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS)
        require(logicalCounter in 0..SyncFormatLimits.MAX_LOGICAL_COUNTER)
    }

    fun successor(): HybridLogicalClock? {
        return when {
            logicalCounter < SyncFormatLimits.MAX_LOGICAL_COUNTER -> copy(logicalCounter = logicalCounter + 1)
            physicalMillis < SyncFormatLimits.MAX_PHYSICAL_MILLIS -> HybridLogicalClock(physicalMillis + 1, 0)
            else -> null
        }
    }

    override fun compareTo(other: HybridLogicalClock): Int {
        val physicalComparison = physicalMillis.compareTo(other.physicalMillis)

        return if (physicalComparison != 0) physicalComparison else logicalCounter.compareTo(other.logicalCounter)
    }

    override fun toString(): String {
        return "HybridLogicalClock(redacted)"
    }
}

internal class PublicSigningKey private constructor(
    private val bytes: ByteArray,
) {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    override fun equals(other: Any?): Boolean {
        return other is PublicSigningKey && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "PublicSigningKey(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): PublicSigningKey? {
            return if (bytes.size == SyncFormatLimits.PUBLIC_KEY_BYTES) {
                PublicSigningKey(bytes.copyOf())
            } else {
                null
            }
        }
    }
}

internal sealed interface SyncOperationPayload {
    data object AuthorRegister : SyncOperationPayload

    data class DomainPresent(
        val domain: ExactDomain,
    ) : SyncOperationPayload

    data class DomainAbsent(
        val domain: ExactDomain,
    ) : SyncOperationPayload

    data class ApplicationPolicyPresent(
        val name: ApplicationPolicyName,
    ) : SyncOperationPayload

    data object ApplicationPolicyAbsent : SyncOperationPayload

    data class SessionStart(
        val sessionId: SessionId,
        val startEpochMillis: Long,
        val mandatoryEndEpochMillis: Long,
    ) : SyncOperationPayload {
        override fun toString(): String {
            return "SyncOperationPayload.SessionStart(redacted)"
        }
    }

    data class SessionEnd(
        val sessionId: SessionId,
    ) : SyncOperationPayload
}

internal data class SyncOperation(
    val operationId: BundleId,
    val context: SyncContext,
    val authorId: AuthorId,
    val publicSigningKey: PublicSigningKey,
    val authorSequence: Long,
    val clock: HybridLogicalClock,
    val payload: SyncOperationPayload,
) {
    init {
        require(authorSequence in 1..Long.MAX_VALUE)
    }

    override fun toString(): String {
        return "SyncOperation(redacted)"
    }
}

internal class TransportKey private constructor(
    private val bytes: ByteArray,
) {
    fun useBytes(block: (ByteArray) -> ByteArray): ByteArray {
        val copy = bytes.copyOf()
        return try {
            block(copy)
        } finally {
            copy.fill(0)
        }
    }

    fun close() {
        bytes.fill(0)
    }

    override fun toString(): String {
        return "TransportKey(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): TransportKey? {
            return if (bytes.size == SyncFormatLimits.TRANSPORT_KEY_BYTES) {
                TransportKey(bytes.copyOf())
            } else {
                null
            }
        }
    }
}

internal class EncryptedBundle private constructor(
    private val bytes: ByteArray,
) {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    override fun equals(other: Any?): Boolean {
        return other is EncryptedBundle && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "EncryptedBundle(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): EncryptedBundle? {
            return bytes
                .takeIf { value -> value.size <= SyncFormatLimits.COMPLETE_BUNDLE_BYTES }
                ?.copyOf()
                ?.let(::EncryptedBundle)
        }
    }
}

internal data class OperationOrder(
    val clock: HybridLogicalClock,
    val authorId: AuthorId,
    val operationId: BundleId,
) : Comparable<OperationOrder> {
    override fun compareTo(other: OperationOrder): Int {
        val clockComparison = clock.compareTo(other.clock)
        if (clockComparison != 0) {
            return clockComparison
        }
        val authorComparison = authorId.value.compareTo(other.authorId.value)

        return if (authorComparison != 0) authorComparison else operationId.value.compareTo(other.operationId.value)
    }
}

internal fun SyncOperation.order(): OperationOrder {
    return OperationOrder(clock, authorId, operationId)
}
