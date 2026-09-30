package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.StoredPolicyIntent

internal suspend fun PosatoDatabase.writeSetRows(sets: PauseSets) {
    localExactDomainPolicyQueries.deleteSets()
    localExactDomainPolicyQueries.deleteAllSetDomains()
    sets.sets.forEach { set ->
        localExactDomainPolicyQueries.insertSet(set.id.value.copyBytes(), set.name, if (set.refused) 1L else 0L)
        set.domains.forEach { domain ->
            localExactDomainPolicyQueries.insertSetDomain(set.id.value.copyBytes(), domain.canonicalValue)
        }
    }
    localExactDomainPolicyQueries.deleteDefault()
    sets.defaultSetId?.let { setId -> localExactDomainPolicyQueries.insertDefault(setId.value.copyBytes()) }
}

internal suspend fun PosatoDatabase.readSetsOrThrow(): PauseSets {
    val setRows = localExactDomainPolicyQueries.selectSets(MAX_STORED_SETS + 1).awaitAsList()
    val domainRows = localExactDomainPolicyQueries.selectSetDomains(MAX_STORED_SET_DOMAINS + 1).awaitAsList()
    val defaults = localExactDomainPolicyQueries.selectDefault().awaitAsList()
    if (setRows.size > MAX_STORED_SETS || domainRows.size > MAX_STORED_SET_DOMAINS || defaults.size > 1) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    val domainsBySet = domainRows.groupBy(
        keySelector = { row -> restoreSetId(row.set_id) },
        valueTransform = { row -> restoreDomain(row.canonical_domain) },
    )
    val sets = setRows.map { row ->
        LocalPauseSet(restoreSetId(row.set_id), row.name, domainsBySet[restoreSetId(row.set_id)].orEmpty(), row.refused == 1L)
    }
    if (domainsBySet.keys.any { setId -> sets.none { set -> set.id == setId } }) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    return PauseSets.of(sets, defaults.singleOrNull()?.let(::restoreSetId)) ?: fail(LocalPolicyFailure.CORRUPTION)
}

internal fun restoreSetId(bytes: ByteArray): PauseSetId {
    return SyncIdentifier.fromExactBytes(bytes)?.let(PauseSetId::of) ?: fail(LocalPolicyFailure.CORRUPTION)
}

/** Ten live sets plus sets refused at the cap; well past what devices can create, so more rows mean corruption. */
private const val MAX_STORED_SETS: Long = 64
private const val MAX_STORED_SET_DOMAINS: Long = MAX_STORED_SETS * ExactDomainPolicyLimits.MAX_DOMAIN_COUNT

internal suspend fun PosatoDatabase.readApplicationPolicyNameOrThrow(): ApplicationPolicyName? {
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
    return applicationPolicyName?.let(::restorePolicyName)
}

internal fun StoredPolicyIntent.setIdOf(): PauseSetId {
    return when (this) {
        is StoredPolicyIntent.PresentDomain -> setId
        is StoredPolicyIntent.RemoveDomain -> setId
        is StoredPolicyIntent.PutSet -> setId
        is StoredPolicyIntent.RemoveSet -> setId
        is StoredPolicyIntent.ChooseDefault -> setId
    }
}
