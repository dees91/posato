package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.PauseSetStatus
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.targets.data.setIdOf
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.StoredPolicyIntent

internal fun seedsFor(
    set: LocalPauseSet,
    projection: SyncProjection,
    pending: List<StoredPolicyIntent>,
): List<StoredPolicyIntent> {
    val pendingForSet = pending.filter { intent -> intent.setIdOf() == set.id }
    // Only a set the workspace does not know is named; the first set is live from the start, so its name never is.
    val put = set.name
        ?.takeIf { projection.pauseSetStatus(set.id) == PauseSetStatus.UNKNOWN }
        ?.takeIf { pendingForSet.none { intent -> intent is StoredPolicyIntent.PutSet } }
        ?.let { name -> StoredPolicyIntent.PutSet(set.id, name) }
    val pendingDomains = pendingForSet.mapNotNullTo(mutableSetOf(), StoredPolicyIntent::domainOrNull)
    val domains = set.domains
        .filter { domain -> domain !in pendingDomains }
        .map { domain -> StoredPolicyIntent.PresentDomain(domain, set.id) }
    return listOfNotNull(put) + domains
}

/**
 * Each set is merged on its own. A set the workspace removed disappears here, one it refused at the cap is
 * kept and marked, one it does not know yet stays as it is, and a live one merges its websites three ways
 * against the last synchronized base, with this device's queued changes applied last.
 */
internal fun mergeSets(
    local: PauseSets,
    base: PolicySyncBase?,
    projection: SyncProjection,
    pending: List<StoredPolicyIntent>,
): List<LocalPauseSet> {
    val ids = (local.sets.map(LocalPauseSet::id) + projection.pauseSets.map { set -> set.setId }).distinct()
    return ids.mapNotNull { setId ->
        val localSet = local.sets.firstOrNull { set -> set.id == setId }
        val pendingForSet = pending.filter { intent -> intent.setIdOf() == setId }
        when (projection.pauseSetStatus(setId)) {
            PauseSetStatus.REMOVED -> null
            PauseSetStatus.LIVE -> mergeLive(setId, localSet, base, projection, pendingForSet)
            PauseSetStatus.REFUSED -> localSet?.copy(domains = applyPending(localSet.domains.toMutableSet(), pendingForSet), refused = true)
            PauseSetStatus.UNKNOWN -> localSet?.copy(domains = applyPending(localSet.domains.toMutableSet(), pendingForSet), refused = false)
        }
    }
}

private fun mergeLive(
    setId: PauseSetId,
    localSet: LocalPauseSet?,
    base: PolicySyncBase?,
    projection: SyncProjection,
    pending: List<StoredPolicyIntent>,
): LocalPauseSet {
    val projected = projection.pauseSetDomains(setId).toSet()
    val localDomains = localSet?.domains.orEmpty().toSet()
    val presents = pending.filterIsInstance<StoredPolicyIntent.PresentDomain>().mapTo(mutableSetOf()) { intent -> intent.domain }
    val removals = pending.filterIsInstance<StoredPolicyIntent.RemoveDomain>().mapTo(mutableSetOf()) { intent -> intent.domain }
    val domains = if (base == null) {
        (projected + localDomains).toMutableSet()
    } else {
        mergeEstablished(localDomains, base.domains[setId].orEmpty().toSet(), projected, presents, removals)
    }
    val pendingName = pending.filterIsInstance<StoredPolicyIntent.PutSet>().lastOrNull()?.name
    val projectedName = projection.pauseSets.firstOrNull { set -> set.setId == setId }?.name
    return LocalPauseSet(setId, pendingName ?: projectedName, applyPending(domains, pending), refused = false)
}

private fun applyPending(
    domains: MutableSet<ExactDomain>,
    pending: List<StoredPolicyIntent>,
): List<ExactDomain> {
    pending.forEach { intent ->
        when (intent) {
            is StoredPolicyIntent.PresentDomain -> domains.add(intent.domain)
            is StoredPolicyIntent.RemoveDomain -> domains.remove(intent.domain)
            else -> Unit
        }
    }
    return domains.sortedBy(ExactDomain::canonicalValue)
}

internal fun chosenDefault(
    pending: List<StoredPolicyIntent>,
    projection: SyncProjection,
): PauseSetId? {
    return pending.filterIsInstance<StoredPolicyIntent.ChooseDefault>().lastOrNull()?.setId ?: projection.defaultPauseSetId
}

private fun mergeEstablished(
    local: Set<ExactDomain>,
    base: Set<ExactDomain>,
    projected: Set<ExactDomain>,
    presents: Set<ExactDomain>,
    removals: Set<ExactDomain>,
): MutableSet<ExactDomain> {
    val gained = projected - base
    val lost = base - projected
    val provisional = (local + gained) - lost
    val intentDomains = presents + removals
    val kept = provisional.filter { domain -> domain in projected || domain in intentDomains }.toMutableSet()
    kept += projected.filter { domain -> domain !in local && domain !in removals }
    return kept
}

private fun StoredPolicyIntent.domainOrNull(): ExactDomain? {
    return when (this) {
        is StoredPolicyIntent.PresentDomain -> domain
        is StoredPolicyIntent.RemoveDomain -> domain
        else -> null
    }
}

internal fun SyncProjection.uniqueDomains(): Set<ExactDomain> {
    return pauseSets.flatMapTo(mutableSetOf()) { set -> set.domains }
}
