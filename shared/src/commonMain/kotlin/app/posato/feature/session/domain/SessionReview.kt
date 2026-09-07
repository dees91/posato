package app.posato.feature.session.domain

import app.posato.feature.targets.data.LocalApplicationMappingsAccess
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
    MAPPINGS_NOT_CHOSEN(false),
    ACCESS_REQUIRED(false),
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
                    access = mappings.access,
                )
            }

            is LocalApplicationMappingsLoadResult.Unavailable -> {
                deriveFromCount(
                    policy = policy,
                    selectedMappingCount = null,
                    access = null,
                )
            }
        }
    }

    private fun deriveFromCount(
        policy: TargetPolicy,
        selectedMappingCount: Int?,
        access: LocalApplicationMappingsAccess?,
    ): SessionReview {
        val domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList()
        val effectiveApplications = if (policy.applicationPolicyName != null) selectedMappingCount ?: 0 else 0
        val effectiveCount = domains.size + effectiveApplications
        val actionRequired = when {
            effectiveCount == 0 -> {
                SessionActionRequired.NO_EFFECTIVE_ITEMS
            }

            policy.applicationPolicyName != null && selectedMappingCount == 0 && access == LocalApplicationMappingsAccess.READY -> {
                SessionActionRequired.MAPPINGS_NOT_CHOSEN
            }

            policy.applicationPolicyName != null && selectedMappingCount == 0 && access != null -> {
                SessionActionRequired.ACCESS_REQUIRED
            }

            else -> {
                null
            }
        }

        return SessionReview(
            domains = domains,
            applicationGroupName = policy.applicationPolicyName?.canonicalValue,
            selectedMappingCount = selectedMappingCount,
            actionRequired = actionRequired,
        )
    }
}
