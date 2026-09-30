package app.posato.feature.targets.data

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal enum class LocalPolicyFailure {
    INVALID_REVISION,
    REVISION_CONFLICT,
    REVISION_EXHAUSTED,
    CORRUPTION,
    STORAGE_FAILURE,
    CAPACITY,
}

internal sealed interface LocalPolicyResult<out T> {
    data class Success<T>(
        val value: T,
    ) : LocalPolicyResult<T>

    data class Failure(
        val reason: LocalPolicyFailure,
    ) : LocalPolicyResult<Nothing>
}

/**
 * The stored pause sets and the local application group name. [policy] is the first set's websites with that
 * name, the view every screen edits until sets can be chosen.
 */
internal class LocalTargetPolicyState(
    val revision: Long,
    val sets: PauseSets,
    val applicationPolicyName: ApplicationPolicyName?,
) {
    constructor(revision: Long, policy: TargetPolicy) : this(
        revision,
        PauseSets.firstSetOnly(policy.domains),
        policy.applicationPolicyName,
    )

    val policy: TargetPolicy = checkNotNull(
        (
            TargetPolicy.fromStoredValues(
                sets.domainsOf(PauseSetId.FIRST).map(ExactDomain::canonicalValue),
                applicationPolicyName?.canonicalValue,
            ) as? TargetPolicyValidationResult.Success
        )?.policy,
    )

    init {
        require(revision >= 0)
    }

    /** The websites the other sets hold, which count once toward the one limit shared by every set. */
    fun domainsOutside(setId: PauseSetId): Set<String> {
        return sets.sets.filter { set -> set.id != setId }.flatMapTo(mutableSetOf()) { set -> set.domains.map(ExactDomain::canonicalValue) }
    }

    override fun equals(other: Any?): Boolean {
        return other is LocalTargetPolicyState &&
            revision == other.revision &&
            sets == other.sets &&
            applicationPolicyName == other.applicationPolicyName
    }

    override fun hashCode(): Int {
        return 31 * (31 * revision.hashCode() + sets.hashCode()) + applicationPolicyName.hashCode()
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
}

internal interface LocalPolicySyncStore : LocalTargetPolicyStore {
    suspend fun replaceSets(
        expectedRevision: Long,
        sets: PauseSets,
        syncWrite: PolicySyncWrite? = null,
    ): LocalPolicyResult<LocalTargetPolicyState>

    suspend fun recordIntents(write: PolicySyncWrite): LocalPolicyResult<Unit>

    suspend fun readIntents(): LocalPolicyResult<List<SequencedPolicyIntent>>

    suspend fun deleteIntent(sequence: Long): LocalPolicyResult<Unit>

    suspend fun clearIntents(): LocalPolicyResult<Unit>

    suspend fun readBase(): LocalPolicyResult<PolicySyncBase?>

    suspend fun replaceWithBase(
        expectedRevision: Long,
        sets: PauseSets,
        base: Map<PauseSetId, List<ExactDomain>>,
    ): LocalPolicyResult<LocalTargetPolicyState>
}
