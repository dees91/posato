package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.WorkspaceId

internal sealed interface BindingGate {
    data class Use(
        val binding: AccountBinding
    ) : BindingGate

    data class Stop(
        val result: BootstrapResult
    ) : BindingGate
}

internal sealed interface ZoneGate {
    data object Proceed : ZoneGate

    data class Stop(
        val result: BootstrapResult
    ) : ZoneGate
}

internal sealed interface ItemCheck {
    data class Valid(
        val decoded: DecodedKeyItem
    ) : ItemCheck

    data object Missing : ItemCheck

    data object Retryable : ItemCheck

    data object ActionRequired : ItemCheck
}

internal fun mapStoreFailure(reason: BootstrapStoreFailure): BootstrapResult {
    return when (reason) {
        BootstrapStoreFailure.CORRUPTION -> BootstrapResult.ActionRequired
        BootstrapStoreFailure.STORAGE_FAILURE -> BootstrapResult.Retryable
    }
}

internal suspend fun adoptJoinableAnchor(
    store: BootstrapStore,
    anchors: BootstrapAnchorPhase,
    joins: BootstrapJoinPhase,
    binding: AccountBinding,
    anchor: WorkspaceAnchor
): BootstrapResult {
    val refused = refuseIfRemoved(store, anchor.workspaceId)
    if (refused != null) {
        return refused
    }
    val result = anchors.adoptAnchorItem(binding, anchor)
    if (result == BootstrapResult.WaitingForWorkspaceKey) {
        joins.remember(binding, anchor)
    }
    return result
}

internal suspend fun refuseIfRemoved(
    store: BootstrapStore,
    workspaceId: WorkspaceId
): BootstrapResult? {
    return when (val removed = store.containsRemoved(workspaceId)) {
        is BootstrapStoreResult.Failure -> mapStoreFailure(removed.reason)

        is BootstrapStoreResult.Success -> if (removed.value) {
            BootstrapResult.Retryable
        } else {
            null
        }
    }
}
