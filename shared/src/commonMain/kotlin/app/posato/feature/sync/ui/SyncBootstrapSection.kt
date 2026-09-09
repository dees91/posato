package app.posato.feature.sync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_sync_now
import app.posato.generated.resources.action_sync_with_icloud
import app.posato.generated.resources.sync_action_required
import app.posato.generated.resources.sync_cancel_removal
import app.posato.generated.resources.sync_completed
import app.posato.generated.resources.sync_icloud_description
import app.posato.generated.resources.sync_icloud_retryable
import app.posato.generated.resources.sync_icloud_running
import app.posato.generated.resources.sync_icloud_waiting_for_key
import app.posato.generated.resources.sync_pending
import app.posato.generated.resources.sync_remove_confirmation
import app.posato.generated.resources.sync_remove_workspace
import app.posato.generated.resources.sync_retryable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SyncBootstrapSection(
    state: SyncBootstrapUiState,
    modifier: Modifier = Modifier
) {
    val snapshot by state.syncState.collectAsState()
    var confirmingRemoval by remember { mutableStateOf(false) }
    val running = state.running || snapshot.status == SyncStatus.SYNCING
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoCaption(stringResource(if (running) Res.string.sync_icloud_running else snapshot.status.message(snapshot.linked)))
        PosatoActionRow {
            PosatoButton(onClick = state::sync, enabled = !running) {
                Text(stringResource(if (snapshot.linked) Res.string.action_sync_now else Res.string.action_sync_with_icloud))
            }
            if (snapshot.linked) {
                PosatoButton(onClick = { confirmingRemoval = true }, style = PosatoButtonStyle.Quiet, enabled = !running) {
                    Text(stringResource(Res.string.sync_remove_workspace), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmingRemoval) {
        AlertDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text(stringResource(Res.string.sync_remove_workspace)) },
            text = { Text(stringResource(Res.string.sync_remove_confirmation)) },
            confirmButton = {
                PosatoButton(onClick = {
                    confirmingRemoval = false
                    state.removeWorkspace()
                }, style = PosatoButtonStyle.Destructive) { Text(stringResource(Res.string.sync_remove_workspace)) }
            },
            dismissButton = {
                PosatoButton(onClick = { confirmingRemoval = false }, style = PosatoButtonStyle.Quiet) {
                    Text(stringResource(Res.string.sync_cancel_removal))
                }
            },
        )
    }
}

private fun SyncStatus.message(linked: Boolean): StringResource {
    return when (this) {
        SyncStatus.LOCAL_ONLY -> Res.string.sync_icloud_description
        SyncStatus.PENDING -> Res.string.sync_pending
        SyncStatus.SYNCING -> Res.string.sync_icloud_running
        SyncStatus.COMPLETED -> Res.string.sync_completed
        SyncStatus.RETRYABLE -> if (linked) Res.string.sync_retryable else Res.string.sync_icloud_retryable
        SyncStatus.WAITING_FOR_KEY -> Res.string.sync_icloud_waiting_for_key
        SyncStatus.ACTION_REQUIRED -> Res.string.sync_action_required
    }
}
