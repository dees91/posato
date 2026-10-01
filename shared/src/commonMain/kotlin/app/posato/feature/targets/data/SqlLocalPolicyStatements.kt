package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
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

/** Replaces the first set's websites and the local group name; the other sets must still fit the unique limit. */
internal suspend fun PosatoDatabase.writePolicyRows(policy: TargetPolicy) {
    val current = readStateOrThrow().sets
    val sets = current.withDomains(PauseSetId.FIRST, policy.domains) ?: fail(LocalPolicyFailure.CAPACITY)
    writeSetRows(sets)
    writeApplicationPolicyName(policy.applicationPolicyName)
}

private suspend fun PosatoDatabase.writeApplicationPolicyName(name: ApplicationPolicyName?) {
    localExactDomainPolicyQueries.deleteApplicationPolicy()
    name?.let { value -> localExactDomainPolicyQueries.insertApplicationPolicy(value.canonicalValue) }
}

internal suspend fun PosatoDatabase.writeBaseRows(base: Map<PauseSetId, List<ExactDomain>>) {
    syncLocalPolicyQueries.deleteBaseMarker()
    syncLocalPolicyQueries.deleteBaseDomains()
    syncLocalPolicyQueries.deleteBaseApplication()
    syncLocalPolicyQueries.insertBaseMarker()
    base.forEach { (setId, domains) ->
        domains.forEach { domain -> syncLocalPolicyQueries.insertBaseDomain(setId.value.copyBytes(), domain.canonicalValue) }
    }
}

/** Records [write]'s intents; a set removal first drops that set's queued changes, which it makes moot. */
internal suspend fun PosatoDatabase.insertIntents(write: PolicySyncWrite) {
    write.intents.forEach { intent ->
        if (intent is StoredPolicyIntent.RemoveSet) {
            syncLocalPolicyQueries.deleteIntentsOfSet(intent.setId.value.copyBytes())
        }
        val (kind, domain, name) = when (intent) {
            is StoredPolicyIntent.PresentDomain -> Triple(INTENT_DOMAIN_PRESENT, intent.domain.canonicalValue, null)
            is StoredPolicyIntent.RemoveDomain -> Triple(INTENT_DOMAIN_ABSENT, intent.domain.canonicalValue, null)
            is StoredPolicyIntent.PutSet -> Triple(INTENT_SET_PUT, null, intent.name)
            is StoredPolicyIntent.RemoveSet -> Triple(INTENT_SET_REMOVE, null, null)
            is StoredPolicyIntent.ChooseDefault -> Triple(INTENT_SET_DEFAULT, null, null)
        }
        syncLocalPolicyQueries.insertIntent(write.workspaceId, kind, intent.setIdOf().value.copyBytes(), domain, name)
    }
}

internal suspend fun PosatoDatabase.readIntentsOrThrow(): List<SequencedPolicyIntent> {
    return syncLocalPolicyQueries.selectIntents().awaitAsList().map { row ->
        val setId = restoreSetId(row.set_id)
        val intent = when (row.kind) {
            INTENT_DOMAIN_PRESENT -> StoredPolicyIntent.PresentDomain(restoreDomain(row.canonical_domain), setId)

            INTENT_DOMAIN_ABSENT -> StoredPolicyIntent.RemoveDomain(restoreDomain(row.canonical_domain), setId)

            INTENT_SET_PUT -> StoredPolicyIntent.PutSet(
                setId,
                row.set_name?.takeIf(ScheduleWireRules::isValidName) ?: fail(LocalPolicyFailure.CORRUPTION),
            )

            INTENT_SET_REMOVE -> StoredPolicyIntent.RemoveSet(setId)

            INTENT_SET_DEFAULT -> StoredPolicyIntent.ChooseDefault(setId)

            else -> fail(LocalPolicyFailure.CORRUPTION)
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
    val domains = syncLocalPolicyQueries.selectBaseDomains().awaitAsList().groupBy(
        keySelector = { row -> restoreSetId(row.set_id) },
        valueTransform = { row -> restoreDomain(row.canonical_domain) },
    )
    if (domains.values.flatten().toSet().size > SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    return PolicySyncBase(domains)
}

internal suspend fun PosatoDatabase.readStateOrThrow(): LocalTargetPolicyState {
    val revisions = localExactDomainPolicyQueries
        .selectRevision()
        .awaitAsList()
    if (revisions.size != 1 || revisions.single() < 0) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    return LocalTargetPolicyState(revisions.single(), readSetsOrThrow(), readApplicationPolicyNameOrThrow())
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
private const val INTENT_SET_PUT = "set_put"
private const val INTENT_SET_REMOVE = "set_remove"
private const val INTENT_SET_DEFAULT = "set_default"
