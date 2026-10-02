package app.posato.feature.targets.domain

import app.posato.feature.sync.domain.PauseSetId

/** A local change waiting to be authored. Each names its pause set; set changes are ordered before their websites. */
internal sealed interface StoredPolicyIntent {
    data class PresentDomain(
        val domain: ExactDomain,
        val setId: PauseSetId = PauseSetId.FIRST,
    ) : StoredPolicyIntent

    data class RemoveDomain(
        val domain: ExactDomain,
        val setId: PauseSetId = PauseSetId.FIRST,
    ) : StoredPolicyIntent

    data class PutSet(
        val setId: PauseSetId,
        val name: String,
    ) : StoredPolicyIntent {
        override fun toString(): String {
            return "StoredPolicyIntent.PutSet(redacted)"
        }
    }

    data class RemoveSet(
        val setId: PauseSetId,
    ) : StoredPolicyIntent

    data class ChooseDefault(
        val setId: PauseSetId,
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

/** Each live set's websites as the workspace last held them, the base of the three-way merge. */
internal data class PolicySyncBase(
    val domains: Map<PauseSetId, List<ExactDomain>>,
)
