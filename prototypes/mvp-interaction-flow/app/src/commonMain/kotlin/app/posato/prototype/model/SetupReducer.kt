package app.posato.prototype.model

import kotlinx.collections.immutable.persistentListOf

internal fun reduceSetup(
    state: PrototypeState,
    action: SetupAction
): PrototypeState {
    return when (action) {
        SetupAction.ShowPrivacy -> showPrivacy(state)
        SetupAction.SyncWithICloud -> discoverWorkspace(state)
        SetupAction.DiscoverEmptyWorkspace -> resolveWorkspace(state, delayed = false)
        SetupAction.DiscoverDelayedKey -> resolveWorkspace(state, delayed = true)
        SetupAction.KeyArrived -> receiveKey(state)
        SetupAction.GrantPermission -> grantPermission(state)
        SetupAction.AddExampleDomain -> addExampleDomain(state)
        SetupAction.MapExampleApplication -> mapExampleApplication(state)
        SetupAction.FinishOnboarding -> finishOnboarding(state)
    }
}

private fun showPrivacy(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.Welcome) return state.blocked("Purpose and privacy start from the welcome screen.")

    return state.copy(surface = PrototypeSurface.Privacy).withOutcome("Purpose and privacy come before service or permission prompts.")
}

private fun discoverWorkspace(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.Privacy) return state.blocked("Read the purpose and privacy boundary first.")

    return state.copy(
        surface = PrototypeSurface.WorkspaceCheck,
        workspace = state.workspace.copy(status = WorkspaceStatus.Checking),
        sync = state.sync.copy(status = PrototypeSyncStatus.Syncing),
    ).withOutcome("Checking for an Apple workspace. Choose the mock result in prototype controls.")
}

private fun resolveWorkspace(
    state: PrototypeState,
    delayed: Boolean
): PrototypeState {
    if (state.surface != PrototypeSurface.WorkspaceCheck) return state.blocked("Start workspace discovery first.")

    return if (delayed) {
        state.copy(
            surface = PrototypeSurface.KeyWait,
            workspace = PrototypeWorkspace(WorkspaceStatus.Found, WorkspaceKey.Waiting, remotePolicyAvailable = true),
            sync = state.sync.copy(status = PrototypeSyncStatus.WaitingForKey),
        ).withOutcome("The existing workspace is unchanged. Waiting for its iCloud Keychain key.")
    } else {
        state.copy(
            surface = PrototypeSurface.Permission,
            workspace = PrototypeWorkspace(WorkspaceStatus.Connected, WorkspaceKey.Ready),
            sync = PrototypeSync(PrototypeSyncStatus.Completed, lastCompletedOnDevice = PrototypeClock.NOW),
        ).withOutcome("A first Apple workspace is ready. Permission remains local to this device.")
    }
}

private fun receiveKey(state: PrototypeState): PrototypeState {
    if (state.workspace.key != WorkspaceKey.Waiting) return state.blocked("The workspace is not waiting for a key.")
    val policy = if (state.workspace.remotePolicyAvailable) {
        state.policy.copy(domains = persistentListOf(PrototypeFixtures.DOMAIN), applicationGroup = PrototypeFixtures.APPLICATION_GROUP)
    } else {
        state.policy
    }

    return state.copy(
        surface = PrototypeSurface.Permission,
        workspace = state.workspace.copy(status = WorkspaceStatus.Connected, key = WorkspaceKey.Ready),
        policy = policy,
        sync = PrototypeSync(PrototypeSyncStatus.Completed, lastCompletedOnDevice = PrototypeClock.NOW),
    ).withLocalApplications(persistentListOf()).withOutcome("The existing key arrived. Shared policy is readable; choose apps locally.")
}

private fun grantPermission(state: PrototypeState): PrototypeState {
    if (state.workspace.key != WorkspaceKey.Ready) return state.blocked("Wait for the workspace key first.")

    return state.copy(surface = PrototypeSurface.Targets, permission = PrototypePermission.Granted)
        .withOutcome("Mock permission is available on this ${state.platform.label}. No system permission was changed.")
}

private fun addExampleDomain(state: PrototypeState): PrototypeState {
    if (state.permission != PrototypePermission.Granted || state.workspace.key != WorkspaceKey.Ready) {
        return state.blocked("Permission and the workspace must be ready before adding a domain.")
    }
    if (PrototypeFixtures.DOMAIN in state.policy.domains) return state.blocked("This domain is already in the shared policy.")

    return state.copy(policy = state.policy.copy(domains = state.policy.domains.adding(PrototypeFixtures.DOMAIN)))
        .pending("The exact domain was saved locally and is pending synchronization.")
}

internal fun mapExampleApplication(state: PrototypeState): PrototypeState {
    if (state.permission != PrototypePermission.Granted || state.workspace.key != WorkspaceKey.Ready) {
        return state.blocked("Permission and the workspace must be ready before choosing apps.")
    }
    if (PrototypeFixtures.APPLICATION in state.localApplications()) return state.blocked("This application is already mapped locally.")
    val surface = if (state.surface == PrototypeSurface.Recovery) sessionOverview(state) else state.surface

    return state.copy(
        surface = surface,
        policy = state.policy.copy(applicationGroup = state.policy.applicationGroup ?: PrototypeFixtures.APPLICATION_GROUP),
    ).withLocalApplications(state.localApplications().adding(PrototypeFixtures.APPLICATION))
        .pending("The application selection stays local to this ${state.platform.label}.")
}

private fun finishOnboarding(state: PrototypeState): PrototypeState {
    if (state.workspace.key != WorkspaceKey.Ready || state.permission != PrototypePermission.Granted) {
        return state.blocked("The workspace and platform permission still need attention.")
    }
    if (state.policy.domains.isEmpty() || state.policy.applicationGroup == null || state.localApplications().isEmpty()) {
        return state.blocked("Add a shared domain and choose a local app before finishing this walkthrough.")
    }

    return state.copy(surface = PrototypeSurface.Home, onboardingComplete = true)
        .withOutcome("Setup is complete on this device. No session is active.", OutcomeTone.Success)
}
