package app.posato.feature.targets.domain

internal sealed interface StoredPolicyIntent {
    data class PresentDomain(
        val domain: ExactDomain,
    ) : StoredPolicyIntent

    data class RemoveDomain(
        val domain: ExactDomain,
    ) : StoredPolicyIntent

    data class PresentApplicationPolicy(
        val name: ApplicationPolicyName,
    ) : StoredPolicyIntent
}

internal data class PolicySyncWrite(
    val workspaceId: ByteArray,
    val intents: List<StoredPolicyIntent>,
) {
    override fun equals(other: Any?): Boolean {
        return other is PolicySyncWrite &&
            workspaceId.contentEquals(other.workspaceId) &&
            intents == other.intents
    }

    override fun hashCode(): Int {
        return 31 * workspaceId.contentHashCode() + intents.hashCode()
    }

    override fun toString(): String {
        return "PolicySyncWrite(redacted)"
    }
}

internal data class SequencedPolicyIntent(
    val sequence: Long,
    val workspaceId: ByteArray,
    val intent: StoredPolicyIntent,
) {
    override fun equals(other: Any?): Boolean {
        return other is SequencedPolicyIntent &&
            sequence == other.sequence &&
            workspaceId.contentEquals(other.workspaceId) &&
            intent == other.intent
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + workspaceId.contentHashCode()
        result = 31 * result + intent.hashCode()
        return result
    }

    override fun toString(): String {
        return "SequencedPolicyIntent(redacted)"
    }
}

internal data class PolicySyncBase(
    val policy: TargetPolicy,
)
