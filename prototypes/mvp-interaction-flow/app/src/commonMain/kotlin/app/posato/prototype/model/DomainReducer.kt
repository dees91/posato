package app.posato.prototype.model

internal fun openDomain(
    state: PrototypeState,
    original: String?
): PrototypeState {
    if (!canManageItems(state)) return state.blocked("Open setup or paused-item management before editing a website.")
    if (original != null && original !in state.policy.domains) return state.blocked("The selected domain is no longer available.")

    return state.copy(
        surface = PrototypeSurface.DomainEditor,
        editor = PrototypeEditor(returnSurface = state.surface, originalDomain = original, sessionKey = state.editor.sessionKey + 1),
    ).withOutcome("Enter an exact domain or a website URL. Only the domain will be kept.")
}

internal fun saveDomain(
    state: PrototypeState,
    input: String
): PrototypeState {
    if (state.surface != PrototypeSurface.DomainEditor) return state.blocked("Open the website editor before saving.")

    return when (val result = parsePrototypeDomain(input)) {
        is PrototypeDomainResult.Invalid -> domainFailure(state, result.message)
        is PrototypeDomainResult.Valid -> replaceDomain(state, result.domain)
    }
}

private fun domainFailure(
    state: PrototypeState,
    message: String
): PrototypeState {
    return state.copy(editor = state.editor.copy(domainError = message)).withOutcome(message, OutcomeTone.Warning)
}

private fun replaceDomain(
    state: PrototypeState,
    domain: String
): PrototypeState {
    val original = state.editor.originalDomain
    if (domain in state.policy.domains && domain != original) return domainFailure(state, "This domain is already in your selection.")
    val closed = state.copy(surface = state.editor.returnSurface, editor = PrototypeEditor(sessionKey = state.editor.sessionKey))
    if (domain == original) return closed.withOutcome("The previous valid domain remains unchanged.")
    val domains = if (original == null) {
        state.policy.domains.adding(domain)
    } else {
        state.policy.domains.replacingAt(state.policy.domains.indexOf(original), domain)
    }

    return closed.copy(policy = state.policy.copy(domains = domains)).pending("$domain was saved locally. Only the exact domain is kept.")
}

internal fun removeDomain(
    state: PrototypeState,
    domain: String
): PrototypeState {
    if (!canManageItems(state) || domain !in state.policy.domains) return state.blocked("The selected domain is not available to remove here.")

    return state.copy(policy = state.policy.copy(domains = state.policy.domains.removing(domain)))
        .pending("$domain was removed locally and the change is pending synchronization.")
}
