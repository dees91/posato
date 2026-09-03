package app.posato.feature.sync.bootstrap

internal class BootstrapKeyReadPhase(
    private val items: BootstrapItemCheck,
    private val store: BootstrapStore
) {
    suspend fun readEstablishedKey(binding: AccountBinding): WorkspaceKeyRead {
        val established = currentEstablished(binding)
        if (established == null) {
            return WorkspaceKeyRead.ActionRequired
        }
        val account = items.accountFor(established.context.workspaceId)
            ?: return WorkspaceKeyRead.ActionRequired
        return when (val check = items.check(binding, account)) {
            is ItemCheck.Valid -> validatedKey(established, check.decoded)
            is ItemCheck.Missing -> WorkspaceKeyRead.WaitingForWorkspaceKey
            is ItemCheck.Retryable -> WorkspaceKeyRead.Retryable
            is ItemCheck.ActionRequired -> WorkspaceKeyRead.ActionRequired
        }
    }

    private fun validatedKey(
        established: EstablishedWorkspace,
        decoded: DecodedKeyItem
    ): WorkspaceKeyRead {
        val matches = items.matchesAnchor(decoded, anchorOf(established))
        val key = if (matches) WorkspaceKeyValue.fromBytes(decoded.workspaceKey) else null
        decoded.clear()
        if (key == null) {
            return WorkspaceKeyRead.ActionRequired
        }
        return WorkspaceKeyRead.Ready(established.context, key)
    }

    private fun anchorOf(established: EstablishedWorkspace): WorkspaceAnchor {
        return WorkspaceAnchor(
            established.context.workspaceId,
            established.context.transportEpochId,
            established.context.keyEpochId,
        )
    }

    private suspend fun currentEstablished(binding: AccountBinding): EstablishedWorkspace? {
        return when (val state = store.read()) {
            is BootstrapStoreResult.Failure -> null

            is BootstrapStoreResult.Success -> when (val value = state.value) {
                is BootstrapState.Established -> if (value.workspace.binding == binding) value.workspace else null
                is BootstrapState.None -> null
                is BootstrapState.Candidate -> null
            }
        }
    }
}
