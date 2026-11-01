package app.posato.feature.sync.data

import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.targets.domain.WebsiteWindow

/** Kind 22: set, kind 2 domain, then a rule from the fixed choices or the none pair 0 and 0; any other pair is refused. */
internal fun CanonicalReader.readSetDomainWindow(): SyncOperationPayload.SetDomainWindow? {
    val setId = readPauseSetId()
    val domain = readCanonicalDomain()
    val pair = readWindowPair()
    return if (setId != null && domain != null && pair != null) SyncOperationPayload.SetDomainWindow(setId, domain, pair.window) else null
}

/** A decoded pair: [window] is null for the none pair. */
private class WindowPair(
    val window: WebsiteWindow?,
)

private fun CanonicalReader.readWindowPair(): WindowPair? {
    val period = readByte() ?: return null
    val length = readByte() ?: return null
    return if (period == 0 && length == 0) WindowPair(null) else WebsiteWindow.of(period, length)?.let(::WindowPair)
}
