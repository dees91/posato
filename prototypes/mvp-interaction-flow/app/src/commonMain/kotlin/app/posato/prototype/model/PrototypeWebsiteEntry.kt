package app.posato.prototype.model

import kotlinx.collections.immutable.toPersistentList

data class PrototypeWebsiteEntry(
    val revision: Int = 0,
    val submitted: String = "",
    val remainder: String = "",
    val addedCount: Int = 0,
    val duplicateCount: Int = 0,
    val invalidCount: Int = 0,
    val message: String = ""
)

internal fun addDomains(
    state: PrototypeState,
    input: String
): PrototypeState {
    if (!canManageItems(state)) return state.blocked("Manage websites while no session is active.")
    val entries = input.split('\n', '\r', ',').map(String::trim).filter(String::isNotEmpty)
    val domains = state.policy.domains.toMutableSet()
    val invalid = mutableListOf<String>()
    var duplicates = 0
    for (entry in entries) {
        when (val parsed = parsePrototypeDomain(entry)) {
            is PrototypeDomainResult.Valid -> if (!domains.add(parsed.domain)) duplicates += 1
            is PrototypeDomainResult.Invalid -> invalid.add(entry)
        }
    }
    val added = domains.size - state.policy.domains.size
    val result = PrototypeWebsiteEntry(
        revision = state.websiteEntry.revision + 1,
        submitted = input,
        remainder = invalid.joinToString("\n"),
        addedCount = added,
        duplicateCount = duplicates,
        invalidCount = invalid.size,
        message = websiteEntryMessage(added, duplicates, invalid.size, entries.isEmpty()),
    )
    val next = state.copy(websiteEntry = result)
    return if (added > 0) {
        next.copy(policy = state.policy.copy(domains = domains.toPersistentList())).pending(result.message)
    } else {
        next.withOutcome(result.message, if (invalid.isNotEmpty() || entries.isEmpty()) OutcomeTone.Warning else OutcomeTone.Info)
    }
}

private fun websiteEntryMessage(
    added: Int,
    duplicates: Int,
    invalid: Int,
    empty: Boolean
): String {
    if (empty) return "Enter a website domain or URL."
    val summary = buildList {
        if (added > 0) add("$added added")
        if (duplicates > 0) add("$duplicates already on your list")
        if (invalid > 0) add("$invalid to fix")
    }.joinToString(" · ")
    return if (invalid > 0) "$summary. Check the remaining domains or website URLs." else "$summary. Ready for the next website."
}
