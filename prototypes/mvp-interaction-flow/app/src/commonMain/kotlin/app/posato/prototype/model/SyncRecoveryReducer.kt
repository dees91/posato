package app.posato.prototype.model

import kotlinx.collections.immutable.persistentListOf

internal fun reduceSync(
    state: PrototypeState,
    action: SyncAction
): PrototypeState {
    return when (action) {
        SyncAction.Start, SyncAction.Retry -> beginSync(state)
        SyncAction.Succeed -> completeSync(state)
        SyncAction.Fail -> failSync(state)
    }
}

private fun beginSync(state: PrototypeState): PrototypeState {
    if (!state.sync.pendingWork && state.sync.status != PrototypeSyncStatus.Retryable) {
        return state.blocked("There is no pending or retryable local work to synchronize.")
    }

    return state.copy(sync = state.sync.copy(status = PrototypeSyncStatus.Syncing)).withOutcome("Synchronizing pending local work.")
}

private fun completeSync(state: PrototypeState): PrototypeState {
    if (state.sync.status != PrototypeSyncStatus.Syncing) return state.blocked("A local sync attempt must be running first.")

    return state.copy(sync = PrototypeSync(PrototypeSyncStatus.Completed, lastCompletedOnDevice = PrototypeClock.NOW))
        .withOutcome("Last sync completed on this device at 17:45. Delivery to another device is not claimed.", OutcomeTone.Success)
}

private fun failSync(state: PrototypeState): PrototypeState {
    if (state.sync.status != PrototypeSyncStatus.Syncing) return state.blocked("A local sync attempt must be running before it can fail.")

    return state.copy(sync = state.sync.copy(status = PrototypeSyncStatus.Retryable, pendingWork = true))
        .withOutcome("The local attempt could not complete. Valid state and pending work are preserved.", OutcomeTone.Warning)
}

internal fun reduceRecovery(
    state: PrototypeState,
    action: RecoveryAction
): PrototypeState {
    return when (action) {
        RecoveryAction.RevokePermission -> revokePermission(state)
        RecoveryAction.RepairPermission -> repairPermission(state)
        RecoveryAction.RemoveMapping -> removeMapping(state)
        RecoveryAction.RemapApplication -> mapExampleApplication(state)
    }
}

private fun revokePermission(state: PrototypeState): PrototypeState {
    if (state.permission != PrototypePermission.Granted) return state.blocked("Permission is not currently granted on this device.")

    return state.copy(
        surface = PrototypeSurface.Recovery,
        permission = PrototypePermission.Revoked,
        sync = state.sync.copy(status = PrototypeSyncStatus.ActionRequired),
    ).withOutcome("Permission was revoked on this ${state.platform.label}. The previous valid policy remains visible.", OutcomeTone.Warning)
}

private fun repairPermission(state: PrototypeState): PrototypeState {
    if (state.permission != PrototypePermission.Revoked) return state.blocked("Permission does not currently need repair.")
    val surface = if (state.surface == PrototypeSurface.SessionReview) PrototypeSurface.SessionReview else sessionOverview(state)

    return state.copy(
        surface = surface,
        permission = PrototypePermission.Granted,
        sync = state.sync.copy(status = if (state.sync.pendingWork) PrototypeSyncStatus.Pending else PrototypeSyncStatus.Completed),
    ).withOutcome("Mock permission is available again on this ${state.platform.label}.", OutcomeTone.Success)
}

private fun removeMapping(state: PrototypeState): PrototypeState {
    if (state.policy.applicationGroup == null || state.localApplications().isEmpty()) return state.blocked("There is no local app mapping to remove.")

    return state.copy(surface = if (state.session.active) PrototypeSurface.Recovery else state.surface)
        .withLocalApplications(persistentListOf())
        .pending("The shared policy remains, but this ${state.platform.label} needs a new local app mapping.")
}
