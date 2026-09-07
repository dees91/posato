package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoNotice
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoSyncFooter
import app.posato.prototype.designsystem.PosatoTone
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypePermission
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSyncStatus
import app.posato.prototype.model.RecoveryAction
import app.posato.prototype.model.SyncAction

@Composable
internal fun PrototypeRepairNotice(
    state: PrototypeState,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoNotice(tone = PosatoTone.Caution, announceChanges = true) {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) { state.reviewIssues().forEach { Text(it) } }
        }
        PosatoActionRow {
            if (state.permission == PrototypePermission.Revoked) {
                PosatoButton(onClick = {
                    onAction(RecoveryAction.RepairPermission)
                }, style = PosatoButtonStyle.Secondary) { Text("Restore permission") }
            }
            if (state.policy.applicationGroup != null && state.localApplications().isEmpty() && state.permission == PrototypePermission.Granted) {
                PosatoButton(onClick = { onAction(ItemAction.OpenApplications) }, style = PosatoButtonStyle.Secondary) { Text("Choose apps") }
            }
            if (state.effectiveItemCount() == 0 && state.permission == PrototypePermission.Granted && !state.session.active) {
                PosatoButton(onClick = { onAction(ItemAction.OpenItems) }, style = PosatoButtonStyle.Secondary) { Text("Choose paused items") }
            }
        }
        if (state.permission == PrototypePermission.Revoked) PosatoCaption("Restoring permission changes only this prototype’s mock state.")
    }
}

internal fun prototypeSyncMessage(state: PrototypeState): String {
    return when (state.sync.status) {
        PrototypeSyncStatus.LocalOnly -> "Local only. Connect your workspace during setup."
        PrototypeSyncStatus.Pending -> "Changes saved here · waiting to synchronize."
        PrototypeSyncStatus.Syncing -> "Synchronizing this device. Choose a mock result in prototype controls."
        PrototypeSyncStatus.Completed -> "Last sync completed on this device at ${state.sync.lastCompletedOnDevice ?: "17:45"}."
        PrototypeSyncStatus.Retryable -> "Sync couldn’t complete. Your choices and pending changes are safe."
        PrototypeSyncStatus.WaitingForKey -> "Waiting for the existing workspace key."
        PrototypeSyncStatus.ActionRequired -> "This device needs your attention. Your saved choices remain unchanged."
    }
}

@Composable
internal fun PrototypeSyncStatus(
    state: PrototypeState,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val action = when {
        state.sync.status == PrototypeSyncStatus.Retryable -> SyncAction.Retry
        state.sync.pendingWork && state.sync.status != PrototypeSyncStatus.Syncing -> SyncAction.Start
        else -> null
    }
    PosatoSyncFooter(
        modifier = modifier,
        message = prototypeSyncMessage(state),
        actionContent = action?.let { syncAction ->
            { PosatoButton(onClick = { onAction(syncAction) }, style = PosatoButtonStyle.Secondary) { Text(syncAction.label) } }
        },
    )
}
