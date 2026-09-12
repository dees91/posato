package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.RecordDeleteResult

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
        val recordsFailure = removeRecords(check.status, workspace)
        if (recordsFailure != null) return recordsFailure
        val keyFailure = removeKey(workspace)
        if (keyFailure != null) return keyFailure
        closeWriter()
        return when (store.clearEstablished(workspace)) {
            is BootstrapStoreResult.Failure -> {
                SyncStatus.ACTION_REQUIRED
            }

            is BootstrapStoreResult.Success -> {
                // The workspace is gone locally through every branch, so any
                // resume cursor retained for its removal is stale for the
                // next workspace on this account and must not survive.
                mailbox.clearRemovalResumeState(workspace.binding)
                if (check.status == EstablishedStatus.DIFFERENT_ANCHOR) SyncStatus.ACTION_REQUIRED else SyncStatus.LOCAL_ONLY
            }
        }
    }

    private suspend fun removeRecords(
        status: EstablishedStatus,
        workspace: EstablishedWorkspace
    ): SyncStatus? {
        return when (status) {
            EstablishedStatus.READY -> when (mailbox.deleteWorkspaceRecords(workspace.binding)) {
                RecordDeleteResult.DeletedAndAbsent -> null
                RecordDeleteResult.AccountChanged -> SyncStatus.ACTION_REQUIRED
                RecordDeleteResult.Retryable, RecordDeleteResult.UnknownOutcome -> SyncStatus.RETRYABLE
            }

            // No anchor: a resumed verification or an already-removed anchor
            // still has to drain leftover bundles. The gated sweep is safe
            // here where the full delete is not: every set is re-checked
            // against the anchor before deletion, so a concurrently
            // established workspace aborts the pass instead of being swept.
            EstablishedStatus.ANCHOR_MISSING -> when (mailbox.sweepBundlesIfAnchorMissing(workspace.binding)) {
                BundleSweepResult.Swept -> null

                BundleSweepResult.AccountChanged -> SyncStatus.ACTION_REQUIRED

                BundleSweepResult.AnchorPresent,
                BundleSweepResult.Retryable,
                BundleSweepResult.UnknownOutcome -> SyncStatus.RETRYABLE
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
