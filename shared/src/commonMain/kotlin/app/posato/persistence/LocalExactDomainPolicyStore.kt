package app.posato.persistence

import app.posato.policy.ExactDomainPolicy

internal enum class LocalPolicyFailure {
    INVALID_REVISION,
    REVISION_CONFLICT,
    REVISION_EXHAUSTED,
    CORRUPTION,
    STORAGE_FAILURE,
    UNSUPPORTED_SCHEMA,
    CLOSED,
}

internal sealed interface LocalPolicyResult<out T> {
    data class Success<T>(
        val value: T,
    ) : LocalPolicyResult<T>

    data class Failure(
        val reason: LocalPolicyFailure,
    ) : LocalPolicyResult<Nothing>
}

internal class LocalExactDomainPolicyState(
    val revision: Long,
    val policy: ExactDomainPolicy,
) {
    init {
        require(revision >= 0)
    }

    override fun equals(other: Any?): Boolean {
        return other is LocalExactDomainPolicyState &&
            revision == other.revision &&
            policy == other.policy
    }

    override fun hashCode(): Int {
        return 31 * revision.hashCode() + policy.hashCode()
    }

    override fun toString(): String {
        return "LocalExactDomainPolicyState(revision=$revision, " +
            "domainCount=${policy.domains.size}, redacted)"
    }
}

internal interface LocalExactDomainPolicyStore {
    suspend fun read(): LocalPolicyResult<LocalExactDomainPolicyState>

    suspend fun replace(
        expectedRevision: Long,
        policy: ExactDomainPolicy,
    ): LocalPolicyResult<LocalExactDomainPolicyState>

    suspend fun close()
}

internal interface LocalExactDomainPolicyStoreFactory {
    suspend fun open(): LocalPolicyResult<LocalExactDomainPolicyStore>
}
