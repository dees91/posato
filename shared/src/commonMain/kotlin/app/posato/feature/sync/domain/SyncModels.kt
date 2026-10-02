package app.posato.feature.sync.domain

import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.sync.data.ImmutableBytes
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
    const val MAX_SYNCHRONIZED_SCHEDULES: Int = ScheduleLimits.MAX_SCHEDULES
    const val MAX_PAUSE_SETS: Int = 10
    val OPTIONAL_KINDS: IntRange = 128..255
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

    /** Kind 2 without a set, which means the first set; kind 14 with one. */
    data class DomainPresent(
        val domain: ExactDomain,
        val setId: PauseSetId? = null,
    ) : SyncOperationPayload

    /** Kind 3 without a set, which means the first set; kind 15 with one. */
    data class DomainAbsent(
        val domain: ExactDomain,
        val setId: PauseSetId? = null,
    ) : SyncOperationPayload

    data class ApplicationPolicyPresent(
        val name: ApplicationPolicyName,
    ) : SyncOperationPayload

    data object ApplicationPolicyAbsent : SyncOperationPayload

    /** Kind 6 without a set, which means the first set; kind 18 with one. */
    data class SessionStart(
        val sessionId: SessionId,
        val startEpochMillis: Long,
        val mandatoryEndEpochMillis: Long,
        val setId: PauseSetId? = null,
    ) : SyncOperationPayload {
        override fun toString(): String {
            return "SyncOperationPayload.SessionStart(redacted)"
        }
    }

    data class SessionEnd(
        val sessionId: SessionId,
    ) : SyncOperationPayload

    /**
     * Kind 8 without a set, which means the first set; kind 17 with one. The whole schedule; the greatest
     * total-order put of either kind per identifier wins unless it is removed.
     */
    data class SchedulePut(
        val scheduleId: ScheduleSyncId,
        val name: String,
        val weekdays: Int,
        val startMinute: Int,
        val endMinute: Int,
        val enabled: Boolean,
        val setId: PauseSetId? = null,
    ) : SyncOperationPayload {
        override fun toString(): String {
            return "SyncOperationPayload.SchedulePut(redacted)"
        }
    }

    /** Kind 9: permanently removes the identifier, whatever the order. */
    data class ScheduleRemove(
        val scheduleId: ScheduleSyncId,
    ) : SyncOperationPayload

    /** Kind 10: a grow-only fact that stops one occurrence. */
    data class ScheduleSkip(
        val occurrence: ScheduleOccurrenceRef,
    ) : SyncOperationPayload {
        override fun toString(): String {
            return "SyncOperationPayload.ScheduleSkip(redacted)"
        }
    }

    /** Kind 11: a grow-only fact that ends one occurrence early. */
    data class ScheduleOccurrenceEnd(
        val occurrence: ScheduleOccurrenceRef,
    ) : SyncOperationPayload {
        override fun toString(): String {
            return "SyncOperationPayload.ScheduleOccurrenceEnd(redacted)"
        }
    }

    /** Kind 12: names a set; the greatest total-order put names it unless the set is removed. */
    data class PauseSetPut(
        val setId: PauseSetId,
        val name: String,
    ) : SyncOperationPayload {
        override fun toString(): String {
            return "SyncOperationPayload.PauseSetPut(redacted)"
        }
    }

    /** Kind 13: permanently removes the set, whatever the order. */
    data class PauseSetRemove(
        val setId: PauseSetId,
    ) : SyncOperationPayload

    /** Kind 16: the greatest total-order choice is the default. */
    data class PauseSetDefault(
        val setId: PauseSetId,
    ) : SyncOperationPayload

    /** Kind 19: marks the workspace as migrated to pause sets. */
    data object PauseSetsEnabled : SyncOperationPayload

    /**
     * Kinds 128-255 are optional extensions: accepted, retained and ignored in projection. Decoding stays
     * total over the range, so any later meaning can live only in projection and every replica ignores
     * an invalid tail the same way.
     */
    class OptionalExtension(
        val kind: Int,
        val tail: ImmutableBytes,
    ) : SyncOperationPayload {
        init {
            require(kind in SyncFormatLimits.OPTIONAL_KINDS)
        }

        override fun equals(other: Any?): Boolean {
            return other is OptionalExtension && other.kind == kind && other.tail == tail
        }

        override fun hashCode(): Int {
            return 31 * kind + tail.hashCode()
        }

        override fun toString(): String {
            return "SyncOperationPayload.OptionalExtension(redacted)"
        }
    }
}

@JvmInline
internal value class ScheduleSyncId(
    val value: SyncIdentifier,
) {
    /** The engine's identifier: the 16 bytes as lowercase hexadecimal. */
    val hex: String
        get() {
            return value.copyBytes().joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }
}

/** A pause set: a UUIDv4, or the all-zero first set that existing history already refers to. */
@JvmInline
internal value class PauseSetId private constructor(
    val value: SyncIdentifier,
) {
    companion object {
        val FIRST: PauseSetId = PauseSetId(SyncIdentifier.zero())

        fun of(value: SyncIdentifier): PauseSetId? {
            return if (value.isZero() || value.isUuidV4()) PauseSetId(value) else null
        }
    }
}

/** One occurrence as it travels in kinds 10 and 11: the schedule and the local date it starts. */
internal data class ScheduleOccurrenceRef(
    val scheduleId: ScheduleSyncId,
    val date: ScheduleDate,
) {
    override fun toString(): String {
        return "ScheduleOccurrenceRef(redacted)"
    }
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
    var isClosed: Boolean = false
        private set

    fun useBytes(block: (ByteArray) -> ByteArray): ByteArray? {
        if (isClosed) return null
        val copy = bytes.copyOf()
        return try {
            block(copy)
        } finally {
            copy.fill(0)
        }
    }

    fun close() {
        isClosed = true
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
