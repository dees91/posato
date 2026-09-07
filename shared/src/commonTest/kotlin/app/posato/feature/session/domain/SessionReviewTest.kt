package app.posato.feature.session.domain

import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionReviewTest {
    @Test
    fun `given retained mappings without group metadata then applications are not effective items`() {
        val review = SessionReviewDerivation.derive(
            policyOf(emptyList()),
            LocalApplicationMappingsLoadResult.Success(
                snapshotOf(mapping("Example", "a")),
                LocalApplicationMappingsAccess.READY,
            ),
        )

        assertEquals(SessionActionRequired.NO_EFFECTIVE_ITEMS, review.actionRequired)
        assertEquals(1, review.selectedMappingCount)
        assertNull(review.applicationGroupName)
    }

    @Test
    fun `given domains and no group when derived then no action is required`() {
        val review = SessionReviewDerivation.derive(
            policyOf(listOf("stable.example")),
            LocalApplicationMappingsLoadResult.Unavailable(),
        )

        assertNull(review.actionRequired)
        assertEquals(listOf("stable.example"), review.domains)
    }

    @Test
    fun `given no domains and no mappings when derived then effective items are missing`() {
        val review = SessionReviewDerivation.derive(
            policyOf(emptyList()),
            LocalApplicationMappingsLoadResult.Unavailable(),
        )

        assertEquals(SessionActionRequired.NO_EFFECTIVE_ITEMS, review.actionRequired)
    }

    @Test
    fun `given a group without chosen applications when derived then mappings are required`() {
        val review = SessionReviewDerivation.derive(
            policyOf(listOf("stable.example"), "Social feeds"),
            LocalApplicationMappingsLoadResult.Success(
                LocalApplicationMappingsSnapshot.empty(),
                LocalApplicationMappingsAccess.READY,
            ),
        )

        assertEquals(SessionActionRequired.MAPPINGS_NOT_CHOSEN, review.actionRequired)
    }

    @Test
    fun `given a group without access when derived then access is required`() {
        val review = SessionReviewDerivation.derive(
            policyOf(listOf("stable.example"), "Social feeds"),
            LocalApplicationMappingsLoadResult.Success(
                LocalApplicationMappingsSnapshot.empty(),
                LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED,
            ),
        )

        assertEquals(SessionActionRequired.ACCESS_REQUIRED, review.actionRequired)
    }

    @Test
    fun `given no effective items with a group when derived then missing items win`() {
        val review = SessionReviewDerivation.derive(
            policyOf(emptyList(), "Social feeds"),
            LocalApplicationMappingsLoadResult.Success(
                LocalApplicationMappingsSnapshot.empty(),
                LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED,
            ),
        )

        assertEquals(SessionActionRequired.NO_EFFECTIVE_ITEMS, review.actionRequired)
    }

    @Test
    fun `given chosen applications when derived then no action is required`() {
        val review = SessionReviewDerivation.derive(
            policyOf(emptyList(), "Social feeds"),
            LocalApplicationMappingsLoadResult.Success(
                snapshotOf(mapping("Example", "a")),
                LocalApplicationMappingsAccess.READY,
            ),
        )

        assertNull(review.actionRequired)
        assertEquals(1, review.selectedMappingCount)
    }

    @Test
    fun `given a mapping load failure when derived then the load failure is reported`() {
        val review = SessionReviewDerivation.derive(
            policyOf(listOf("stable.example")),
            LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE),
        )

        assertEquals(SessionActionRequired.MAPPINGS_LOAD_FAILED, review.actionRequired)
    }

    private companion object {
        fun policyOf(
            domains: List<String>,
            applicationPolicyName: String? = null,
        ): TargetPolicy {
            val result = TargetPolicy.fromStoredValues(domains, applicationPolicyName)

            return (result as TargetPolicyValidationResult.Success).policy
        }

        fun mapping(
            name: String,
            byte: String,
        ): LocalApplicationMapping {
            val id = checkNotNull(LocalApplicationMappingId.restore(byte.repeat(64)))

            return checkNotNull(LocalApplicationMapping.restore(id, name))
        }

        fun snapshotOf(vararg mappings: LocalApplicationMapping): LocalApplicationMappingsSnapshot {
            return checkNotNull(LocalApplicationMappingsSnapshot.restore(mappings.asList()))
        }
    }
}
