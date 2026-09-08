package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.ZoneDeleteResult

internal class AppleWorkspaceRemoval(
    private val mailbox: MailboxPort,
    private val keys: BootstrapKeyPort,
    private val store: BootstrapStore,
) {
    suspend fun remove(
        check: EstablishedCheck,
        closeWriter: suspend () -> Unit
    ): SyncStatus {
        val workspace = check.workspace ?: return check.status.toSyncStatus()
        val zoneFailure = removeZone(check.status, workspace)
        if (zoneFailure != null) return zoneFailure
        val keyFailure = removeKey(workspace)
        if (keyFailure != null) return keyFailure
        closeWriter()
        return when (store.clearEstablished(workspace)) {
            is BootstrapStoreResult.Failure -> {
                SyncStatus.ACTION_REQUIRED
            }

            is BootstrapStoreResult.Success -> {
                if (check.status == EstablishedStatus.DIFFERENT_ANCHOR) SyncStatus.ACTION_REQUIRED else SyncStatus.LOCAL_ONLY
            }
        }
    }

    private suspend fun removeZone(
        status: EstablishedStatus,
        workspace: EstablishedWorkspace
    ): SyncStatus? {
        return when (status) {
            EstablishedStatus.READY -> when (mailbox.deleteZoneAndVerifyAbsent(workspace.binding)) {
                ZoneDeleteResult.DeletedAndAbsent -> null
                ZoneDeleteResult.AccountChanged -> SyncStatus.ACTION_REQUIRED
                ZoneDeleteResult.Retryable, ZoneDeleteResult.UnknownOutcome -> SyncStatus.RETRYABLE
            }

            EstablishedStatus.ZONE_MISSING, EstablishedStatus.DIFFERENT_ANCHOR -> null

            EstablishedStatus.LOCAL_ONLY, EstablishedStatus.RETRYABLE, EstablishedStatus.ACTION_REQUIRED -> status.toSyncStatus()
        }
    }

    private suspend fun removeKey(workspace: EstablishedWorkspace): SyncStatus? {
        val account = KeyAccount.fromText(BootstrapEncoding.identifierToAccountText(workspace.context.workspaceId.value))
            ?: return SyncStatus.ACTION_REQUIRED
        return when (keys.deleteItemAndVerifyAbsent(workspace.binding, account)) {
            KeyItemDeleteResult.DeletedAndAbsent -> null
            KeyItemDeleteResult.Retryable, KeyItemDeleteResult.UnknownOutcome -> SyncStatus.RETRYABLE
            KeyItemDeleteResult.AccountChanged, KeyItemDeleteResult.IntegrityFailure -> SyncStatus.ACTION_REQUIRED
        }
    }
}
