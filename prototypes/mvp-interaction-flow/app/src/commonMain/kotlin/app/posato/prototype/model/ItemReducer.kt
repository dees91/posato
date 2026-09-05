package app.posato.prototype.model

internal fun reduceItems(
    state: PrototypeState,
    action: ItemAction
): PrototypeState {
    return when (action) {
        ItemAction.OpenItems -> openItems(state)
        ItemAction.CloseItems -> closeItems(state)
        is ItemAction.OpenDomain -> openDomain(state, action.original)
        is ItemAction.SaveDomain -> saveDomain(state, action.input)
        is ItemAction.RemoveDomain -> removeDomain(state, action.domain)
        ItemAction.OpenApplications -> openApplications(state)
        is ItemAction.SaveApplications -> saveApplications(state, action.names)
        is ItemAction.RemoveApplication -> removeApplication(state, action.name)
        ItemAction.Cancel -> cancelEditor(state)
    }
}

private fun openItems(state: PrototypeState): PrototypeState {
    if (!state.onboardingComplete || state.session.active) return state.blocked("Manage items after setup and while no session is active.")

    return state.copy(surface = PrototypeSurface.Items).withOutcome("Choose exact domains and device-local applications.")
}

private fun closeItems(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.Items) return state.blocked("Paused-item management is not open.")

    return state.copy(surface = PrototypeSurface.Home).withOutcome("Your session overview is open.")
}

private fun cancelEditor(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.DomainEditor && state.surface != PrototypeSurface.AppPicker) {
        return state.blocked("There is no configuration editor to close.")
    }

    return state.copy(surface = state.editor.returnSurface, editor = PrototypeEditor(sessionKey = state.editor.sessionKey))
        .withOutcome("The previous valid configuration remains unchanged.")
}

internal fun canManageItems(state: PrototypeState): Boolean {
    return state.surface == PrototypeSurface.Targets || state.surface == PrototypeSurface.Items
}
