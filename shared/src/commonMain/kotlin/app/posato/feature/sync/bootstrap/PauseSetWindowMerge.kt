package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.PauseSetStatus
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.WebsiteWindow

/**
 * At first link a rule is published only for a website the workspace has never ruled, after the website's own
 * seed, so the workspace's rules win as its names and default do. A set the workspace refused takes none.
 */
internal fun windowSeedsFor(
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

internal fun LocalPauseSet.withPending(
    pending: List<StoredPolicyIntent>,
    refused: Boolean,
): LocalPauseSet {
    val merged = applyPending(domains.toMutableSet(), pending)
    return copy(domains = merged, refused = refused, windows = applyPendingWindows(windows, pending, merged))
}

/** Queued changes in order: a removal clears its website's rule, a rule change sets or clears it. */
internal fun applyPendingWindows(
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
