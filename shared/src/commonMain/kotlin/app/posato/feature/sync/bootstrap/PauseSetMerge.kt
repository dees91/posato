package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.PauseSetStatus
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.targets.data.setIdOf
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.WebsiteWindow

internal fun seedsFor(
    set: LocalPauseSet,
    projection: SyncProjection,
    pending: List<StoredPolicyIntent>,
): List<StoredPolicyIntent> {
    val pendingForSet = pending.filter { intent -> intent.setIdOf() == set.id }
    // A set the workspace does not know is named; the first set, live from the start, only while nobody named it.
    val unnamed = when (projection.pauseSetStatus(set.id)) {
        PauseSetStatus.UNKNOWN -> true
        PauseSetStatus.LIVE -> set.id == PauseSetId.FIRST && projection.pauseSets.none { live -> live.setId == set.id && live.name != null }
        PauseSetStatus.REMOVED, PauseSetStatus.REFUSED -> false
    }
    val put = set.name
        ?.takeIf { unnamed }
        ?.takeIf { pendingForSet.none { intent -> intent is StoredPolicyIntent.PutSet } }
        ?.let { name -> StoredPolicyIntent.PutSet(set.id, name) }
    val pendingDomains = pendingForSet.mapNotNullTo(mutableSetOf(), StoredPolicyIntent::domainOrNull)
    val domains = set.domains
        .filter { domain -> domain !in pendingDomains }
        .map { domain -> StoredPolicyIntent.PresentDomain(domain, set.id) }
    return listOfNotNull(put) + domains + windowSeedsFor(set, projection, pendingForSet)
}

/**
 * At first link a rule is published only for a website the workspace has never ruled, after the website's own
 * seed, so the workspace's rules win as its names and default do. A set the workspace refused takes none.
 */
private fun windowSeedsFor(
    set: LocalPauseSet,
    projection: SyncProjection,
    pending: List<StoredPolicyIntent>,
): List<StoredPolicyIntent> {
    val status = projection.pauseSetStatus(set.id)
    if (status != PauseSetStatus.LIVE && status != PauseSetStatus.UNKNOWN) {
        return emptyList()
    }
    val ruled = projection.pauseSets.firstOrNull { live -> live.setId == set.id }?.ruledDomains.orEmpty()
    val queued = pending.filterIsInstance<StoredPolicyIntent.SetWindow>().mapTo(mutableSetOf()) { intent -> intent.domain }
    return set.domains.mapNotNull { domain ->
        set.windows[domain]
            ?.takeIf { domain !in ruled && domain !in queued }
            ?.let { window -> StoredPolicyIntent.SetWindow(domain, set.id, window) }
    }
}

/**
 * Each set is merged on its own. A set the workspace removed, or this device removed and has not authored
 * yet, disappears here; one the workspace refused at the cap is kept and marked, one it does not know yet
 * stays as it is, and a live one merges its websites three ways against the last synchronized base, with
 * this device's queued changes applied last.
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
        val status = projection.pauseSetStatus(setId)
        when {
            pendingForSet.any { intent -> intent is StoredPolicyIntent.RemoveSet } -> null
            status == PauseSetStatus.REMOVED -> null
            status == PauseSetStatus.LIVE -> mergeLive(setId, localSet, base, projection, pendingForSet)
            status == PauseSetStatus.REFUSED -> localSet?.withPending(pendingForSet, refused = true)
            else -> localSet?.withPending(pendingForSet, refused = false)
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
    val live = projection.pauseSets.firstOrNull { set -> set.setId == setId }
    val merged = applyPending(domains, pending)
    // The workspace's rules win; before the first merge a local rule for a website it never ruled waits for its seed.
    val unpublished = if (base == null) localSet?.windows.orEmpty().filterKeys { domain -> domain !in live?.ruledDomains.orEmpty() } else emptyMap()
    val windows = applyPendingWindows(live?.windows.orEmpty() + unpublished, pending, merged)
    return LocalPauseSet(setId, pendingName ?: live?.name, merged, refused = false, windows = windows)
}

private fun LocalPauseSet.withPending(
    pending: List<StoredPolicyIntent>,
    refused: Boolean,
): LocalPauseSet {
    val merged = applyPending(domains.toMutableSet(), pending)
    return copy(domains = merged, refused = refused, windows = applyPendingWindows(windows, pending, merged))
}

/** Queued changes in order: a removal clears its website's rule, a rule change sets or clears it. */
private fun applyPendingWindows(
    start: Map<ExactDomain, WebsiteWindow>,
    pending: List<StoredPolicyIntent>,
    domains: List<ExactDomain>,
): Map<ExactDomain, WebsiteWindow> {
    val windows = start.toMutableMap()
    pending.forEach { intent ->
        when (intent) {
            is StoredPolicyIntent.RemoveDomain -> windows.remove(intent.domain)
            is StoredPolicyIntent.SetWindow -> if (intent.window == null) windows.remove(intent.domain) else windows[intent.domain] = intent.window
            else -> Unit
        }
    }
    return windows.filterKeys { domain -> domain in domains }
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

/**
 * The default this device publishes at first link: its own resolved default, only while the workspace never
 * chose one, when it differs from the workspace's, survives the seed, and its set would not be refused at the cap.
 */
internal fun seededDefault(
    local: PauseSets,
    projection: SyncProjection,
    pending: List<StoredPolicyIntent>,
    seeds: List<StoredPolicyIntent>,
): StoredPolicyIntent.ChooseDefault? {
    val chosen = local.resolvedDefault() ?: return null
    // Queued puts drain before the seeds, so they take the free slots first.
    val newSets = (pending + seeds).filterIsInstance<StoredPolicyIntent.PutSet>()
        .filter { put -> projection.pauseSetStatus(put.setId) == PauseSetStatus.UNKNOWN }
        .map(StoredPolicyIntent.PutSet::setId)
        .distinct()
    val fits = when (projection.pauseSetStatus(chosen)) {
        PauseSetStatus.LIVE -> true
        PauseSetStatus.UNKNOWN -> chosen in newSets && projection.pauseSets.size + newSets.indexOf(chosen) < SyncFormatLimits.MAX_PAUSE_SETS
        PauseSetStatus.REMOVED, PauseSetStatus.REFUSED -> false
    }
    return StoredPolicyIntent.ChooseDefault(chosen).takeIf {
        !projection.defaultChosen &&
            pending.none { intent -> intent is StoredPolicyIntent.ChooseDefault } &&
            chosen != projection.defaultPauseSetId &&
            fits
    }
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
