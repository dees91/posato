package app.posato.prototype.ui

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

internal enum class PrototypeItemSection {
    Websites,
    Applications
}

internal fun filterPrototypeWebsites(
    websites: PersistentList<String>,
    query: String
): PersistentList<String> {
    val term = query.trim()
    if (term.isEmpty()) return websites

    return websites.filter { it.contains(term, ignoreCase = true) }.toPersistentList()
}
