package app.posato.feature.targets.data

import app.posato.feature.targets.domain.TargetPolicy

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
    suspend fun read(): LocalPolicyResult<LocalTargetPolicyState>

    suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState>
}
