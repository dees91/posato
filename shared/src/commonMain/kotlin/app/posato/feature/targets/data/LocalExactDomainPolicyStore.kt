package app.posato.feature.targets.data

import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal enum class LocalPolicyFailure {
    INVALID_REVISION,
    REVISION_CONFLICT,
    REVISION_EXHAUSTED,
    CORRUPTION,
    STORAGE_FAILURE,
}

internal sealed interface LocalPolicyResult<out T> {
    data class Success<T>(
        val value: T,
    ) : LocalPolicyResult<T>

    data class Failure(
        val reason: LocalPolicyFailure,
    ) : LocalPolicyResult<Nothing>
}

internal class LocalTargetPolicyState(
    val revision: Long,
    val policy: TargetPolicy,
) {
    init {
        require(revision >= 0)
    }

    override fun equals(other: Any?): Boolean {
        return other is LocalTargetPolicyState &&
            revision == other.revision &&
            policy == other.policy
    }

    override fun hashCode(): Int {
        return 31 * revision.hashCode() + policy.hashCode()
    }

    override fun toString(): String {
        return "LocalTargetPolicyState(redacted)"
    }
}

internal interface LocalTargetPolicyStore {
    suspend fun <T> withWriteGate(block: suspend () -> T): T

    val policyChanges: Flow<Unit>
        get() = emptyFlow()

    suspend fun read(): LocalPolicyResult<LocalTargetPolicyState>

    suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
        syncWrite: PolicySyncWrite? = null,
    ): LocalPolicyResult<LocalTargetPolicyState>

    suspend fun wwwCounterpartExpansionCompleted(): Boolean {
        return true
    }

    suspend fun markWwwCounterpartExpansionCompleted(): LocalPolicyResult<Unit> {
        return LocalPolicyResult.Success(Unit)
    }
}

internal interface LocalPolicySyncStore : LocalTargetPolicyStore {
    suspend fun recordIntents(write: PolicySyncWrite): LocalPolicyResult<Unit>

    suspend fun readIntents(): LocalPolicyResult<List<SequencedPolicyIntent>>

    suspend fun deleteIntent(sequence: Long): LocalPolicyResult<Unit>

    suspend fun clearIntents(): LocalPolicyResult<Unit>

    suspend fun readBase(): LocalPolicyResult<PolicySyncBase?>

    suspend fun replaceWithBase(
        expectedRevision: Long,
        policy: TargetPolicy,
        base: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState>
}
