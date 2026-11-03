package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.WebsiteWindow

/** Every stored window rule by set; a code outside the fixed choices, or more rows than websites, is corruption. */
internal suspend fun PosatoDatabase.readSetWindowsOrThrow(): Map<PauseSetId, Map<ExactDomain, WebsiteWindow>> {
    val rows = localExactDomainPolicyQueries.selectSetWindows(MAX_STORED_SET_DOMAINS + 1).awaitAsList()
    if (rows.size > MAX_STORED_SET_DOMAINS) {
        fail(LocalPolicyFailure.CORRUPTION)
    }
    return rows.groupBy(
        keySelector = { row -> restoreSetId(row.set_id) },
        valueTransform = { row -> restoreDomain(row.canonical_domain) to checkNotNull(restoreWindow(row.period_minutes, row.length_minutes)) },
    ).mapValues { (_, windows) -> windows.toMap() }
}

/** A stored rule: both codes from the fixed choices, or neither for a queued clearing. */
internal fun restoreWindow(
    period: Long?,
    length: Long?,
): WebsiteWindow? {
    return when {
        period == null && length == null -> null
        period == null || length == null -> fail(LocalPolicyFailure.CORRUPTION)
        else -> WebsiteWindow.of(period.toInt(), length.toInt()) ?: fail(LocalPolicyFailure.CORRUPTION)
    }
}
