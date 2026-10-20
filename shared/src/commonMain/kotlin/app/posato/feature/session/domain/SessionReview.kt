package app.posato.feature.session.domain

import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

internal enum class SessionActionRequired(
    val blocksStart: Boolean,
) {
    NO_EFFECTIVE_ITEMS(true),
    MAPPINGS_LOAD_FAILED(true),
}

internal data class SessionReview(
    val domains: PersistentList<String> = persistentListOf(),
    val applicationGroupName: String? = null,
    val selectedMappingCount: Int? = null,
    val actionRequired: SessionActionRequired? = null,
) {
    override fun toString(): String {
        return "SessionReview(redacted)"
    }
}

internal object SessionReviewDerivation {
    fun derive(
        policy: TargetPolicy,
        mappings: LocalApplicationMappingsLoadResult,
    ): SessionReview {
        return when (mappings) {
            is LocalApplicationMappingsLoadResult.Failure -> {
                SessionReview(
                    domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList(),
                    applicationGroupName = policy.applicationPolicyName?.canonicalValue,
                    actionRequired = SessionActionRequired.MAPPINGS_LOAD_FAILED,
                )
            }

            is LocalApplicationMappingsLoadResult.Success -> {
                deriveFromCount(
                    policy = policy,
                    selectedMappingCount = mappings.snapshot.mappings.size,
                )
            }

            is LocalApplicationMappingsLoadResult.Unavailable -> {
                deriveFromCount(
                    policy = policy,
                    selectedMappingCount = null,
                )
            }
        }
    }

    private fun deriveFromCount(
        policy: TargetPolicy,
        selectedMappingCount: Int?,
    ): SessionReview {
        val domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList()
        val effectiveApplications = selectedMappingCount ?: 0
        val effectiveCount = domains.size + effectiveApplications
        // A set of websites only is complete: no app choice or app access is asked for.
        val actionRequired = if (effectiveCount == 0) SessionActionRequired.NO_EFFECTIVE_ITEMS else null

        return SessionReview(
            domains = domains,
            applicationGroupName = policy.applicationPolicyName?.canonicalValue,
            selectedMappingCount = selectedMappingCount,
            actionRequired = actionRequired,
        )
    }
}
