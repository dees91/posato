package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult

internal suspend fun PosatoDatabase.advanceRevisionOrThrow(expectedRevision: Long) {
    val changed = localExactDomainPolicyQueries.advanceRevision(
        next_revision = expectedRevision + 1,
        expected_revision = expectedRevision,
    )
    readStateOrThrow()
    if (changed != 1L) {
        fail(LocalPolicyFailure.REVISION_CONFLICT)
    }
}

internal suspend fun PosatoDatabase.writePolicyRows(policy: TargetPolicy) {
    localExactDomainPolicyQueries.deleteDomains()
    policy.domains.forEach { domain ->
        localExactDomainPolicyQueries.insertDomain(domain.canonicalValue)
    }
    localExactDomainPolicyQueries.deleteApplicationPolicy()
    policy.applicationPolicyName?.let { name ->
        localExactDomainPolicyQueries.insertApplicationPolicy(name.canonicalValue)
    }
}

internal suspend fun PosatoDatabase.writeBaseRows(base: TargetPolicy) {
    syncLocalPolicyQueries.deleteBaseMarker()
    syncLocalPolicyQueries.deleteBaseDomains()
    syncLocalPolicyQueries.deleteBaseApplication()
    syncLocalPolicyQueries.insertBaseMarker()
    base.domains.forEach { domain ->
        syncLocalPolicyQueries.insertBaseDomain(domain.canonicalValue)
    }
    base.applicationPolicyName?.let { name ->
        syncLocalPolicyQueries.insertBaseApplication(name.canonicalValue)
    }
}

internal suspend fun PosatoDatabase.insertIntents(write: PolicySyncWrite) {
    write.intents.forEach { intent ->
        when (intent) {
            is StoredPolicyIntent.PresentDomain -> {
                syncLocalPolicyQueries.insertIntent(
                    workspaceId = write.workspaceId,
                    kind = INTENT_DOMAIN_PRESENT,
                    canonicalDomain = intent.domain.canonicalValue,
                    canonicalName = null,
                )
            }

            is StoredPolicyIntent.RemoveDomain -> {
                syncLocalPolicyQueries.insertIntent(
                    workspaceId = write.workspaceId,
                    kind = INTENT_DOMAIN_ABSENT,
                    canonicalDomain = intent.domain.canonicalValue,
                    canonicalName = null,
                )
            }

            is StoredPolicyIntent.PresentApplicationPolicy -> {
                syncLocalPolicyQueries.insertIntent(
                    workspaceId = write.workspaceId,
                    kind = INTENT_APPLICATION_PRESENT,
                    canonicalDomain = null,
                    canonicalName = intent.name.canonicalValue,
                )
            }
        }
    }
}

internal suspend fun PosatoDatabase.readIntentsOrThrow(): List<SequencedPolicyIntent> {
    return syncLocalPolicyQueries.selectIntents().awaitAsList().map { row ->
        val intent = when (row.kind) {
            INTENT_DOMAIN_PRESENT -> {
                StoredPolicyIntent.PresentDomain(restoreDomain(row.canonical_domain))
            }

            INTENT_DOMAIN_ABSENT -> {
                StoredPolicyIntent.RemoveDomain(restoreDomain(row.canonical_domain))
            }

            INTENT_APPLICATION_PRESENT -> {
                StoredPolicyIntent.PresentApplicationPolicy(restorePolicyName(row.canonical_name))
            }

            else -> {
                fail(LocalPolicyFailure.CORRUPTION)
            }
        }
        SequencedPolicyIntent(row.sequence, row.workspace_id, intent)
    }
}

internal suspend fun PosatoDatabase.readBaseOrThrow(): PolicySyncBase? {
    val marker = syncLocalPolicyQueries.selectBaseMarker().awaitAsList()
    if (marker.isEmpty()) {
        return null
    }
    if (marker.size != 1) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    if (syncReplicaQueries.selectSyncReplicaState().awaitAsList().isEmpty()) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    val domains = syncLocalPolicyQueries.selectBaseDomains().awaitAsList()
    val names = syncLocalPolicyQueries.selectBaseApplicationName().awaitAsList()
    if (names.size > 1) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    return when (
        val validation = TargetPolicy.fromStoredValues(domains, names.singleOrNull())
    ) {
        is TargetPolicyValidationResult.Success -> {
            PolicySyncBase(validation.policy)
        }

        is TargetPolicyValidationResult.Failure -> {
            fail(LocalPolicyFailure.CORRUPTION)
        }
    }
}

internal suspend fun PosatoDatabase.readStateOrThrow(): LocalTargetPolicyState {
    val revisions = localExactDomainPolicyQueries
        .selectRevision()
        .awaitAsList()
    if (revisions.size != 1 || revisions.single() < 0) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    val canonicalDomains = localExactDomainPolicyQueries
        .selectDomains(ExactDomainPolicyLimits.MAX_DOMAIN_COUNT.toLong() + 1)
        .awaitAsList()
    if (canonicalDomains.size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    val applicationPolicyNameBytes = localExactDomainPolicyQueries
        .selectApplicationPolicyNameBytes()
        .awaitAsList()
    if (applicationPolicyNameBytes.size > 1) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    val applicationPolicyName = try {
        applicationPolicyNameBytes.singleOrNull()?.decodeToString(throwOnInvalidSequence = true)
    } catch (_: Exception) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    val policy = when (
        val validation = TargetPolicy.fromStoredValues(
            canonicalDomains = canonicalDomains,
            applicationPolicyName = applicationPolicyName,
        )
    ) {
        is TargetPolicyValidationResult.Success -> {
            validation.policy
        }

        is TargetPolicyValidationResult.Failure -> {
            fail(LocalPolicyFailure.CORRUPTION)
        }
    }

    return LocalTargetPolicyState(revisions.single(), policy)
}

internal fun restoreDomain(canonicalDomain: String?): ExactDomain {
    return canonicalDomain?.let(ExactDomain::restore) ?: fail(LocalPolicyFailure.CORRUPTION)
}

internal fun restorePolicyName(canonicalName: String?): ApplicationPolicyName {
    return canonicalName?.let(ApplicationPolicyName::restore) ?: fail(LocalPolicyFailure.CORRUPTION)
}

internal class LocalPolicyStoreException(
    val reason: LocalPolicyFailure,
) : Exception()

internal fun fail(reason: LocalPolicyFailure): Nothing {
    throw LocalPolicyStoreException(reason)
}

private const val INTENT_DOMAIN_PRESENT = "domain_present"
private const val INTENT_DOMAIN_ABSENT = "domain_absent"
private const val INTENT_APPLICATION_PRESENT = "application_present"
