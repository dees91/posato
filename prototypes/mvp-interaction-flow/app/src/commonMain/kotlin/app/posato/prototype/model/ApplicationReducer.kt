package app.posato.prototype.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

internal fun openApplications(state: PrototypeState): PrototypeState {
    val activeRepair = state.surface == PrototypeSurface.Active && state.session.active &&
        state.policy.applicationGroup != null && state.localApplications().isEmpty()
    val repairSurface = state.surface == PrototypeSurface.Recovery || state.surface == PrototypeSurface.SessionReview
    if (!canManageItems(state) && !repairSurface && !activeRepair) return state.blocked("Open setup, paused items, or a mapping repair first.")
    if (state.permission != PrototypePermission.Granted || state.workspace.key != WorkspaceKey.Ready) {
        return state.blocked("Permission and the workspace must be ready before choosing local apps.")
    }

    return state.copy(
        surface = PrototypeSurface.AppPicker,
        editor = PrototypeEditor(returnSurface = state.surface, sessionKey = state.editor.sessionKey + 1),
    ).withOutcome("Choose synthetic applications for this ${state.platform.label}. The selection stays local to this device.")
}

internal fun saveApplications(
    state: PrototypeState,
    names: PersistentList<String>
): PrototypeState {
    if (state.surface != PrototypeSurface.AppPicker) return state.blocked("Open the application picker before saving a local mapping.")
    val selected = names.distinct().filter { it in PrototypeFixtures.applications(state.platform) }.toPersistentList()
    if (selected.isEmpty()) {
        val error = "Choose at least one application on this device."
        return state.copy(editor = state.editor.copy(applicationError = error)).withOutcome(error, OutcomeTone.Warning)
    }
    val surface = if (state.editor.returnSurface == PrototypeSurface.Recovery) sessionOverview(state) else state.editor.returnSurface

    return state.copy(
        surface = surface,
        policy = state.policy.copy(applicationGroup = state.policy.applicationGroup ?: PrototypeFixtures.APPLICATION_GROUP),
        editor = PrototypeEditor(sessionKey = state.editor.sessionKey),
    ).withLocalApplications(selected).pending("${selected.joinToString()} mapped locally on this ${state.platform.label}.")
}

internal fun removeApplication(
    state: PrototypeState,
    name: String
): PrototypeState {
    if (!canManageItems(state) ||
        name !in state.localApplications()
    ) {
        return state.blocked("The selected application is not available to remove here.")
    }

    return state.withLocalApplications(state.localApplications().removing(name))
        .pending("$name was removed from this ${state.platform.label}. The shared application policy remains unchanged.")
}
