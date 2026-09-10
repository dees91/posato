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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_check_again
import app.posato.generated.resources.action_sync_now
import app.posato.generated.resources.action_sync_with_icloud
import app.posato.generated.resources.onboarding_summary_sync_on
import app.posato.generated.resources.session_icloud_attention
import app.posato.generated.resources.session_icloud_completed
import app.posato.generated.resources.session_icloud_local
import app.posato.generated.resources.session_icloud_pending
import app.posato.generated.resources.session_icloud_retryable
import app.posato.generated.resources.session_icloud_running
import app.posato.generated.resources.session_icloud_title
import app.posato.generated.resources.session_icloud_waiting
import app.posato.generated.resources.setup_hide_options
import app.posato.generated.resources.setup_show_options
import app.posato.generated.resources.sync_action_required
import app.posato.generated.resources.sync_cancel_removal
import app.posato.generated.resources.sync_checking_key
import app.posato.generated.resources.sync_completed
import app.posato.generated.resources.sync_icloud_description
import app.posato.generated.resources.sync_icloud_retryable
import app.posato.generated.resources.sync_icloud_running
import app.posato.generated.resources.sync_icloud_waiting_for_key
import app.posato.generated.resources.sync_pending
import app.posato.generated.resources.sync_remove_confirmation
import app.posato.generated.resources.sync_remove_workspace
import app.posato.generated.resources.sync_retryable
import app.posato.generated.resources.sync_waiting_explanation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SyncBootstrapSection(
    state: SyncBootstrapUiState,
    modifier: Modifier = Modifier
) {
    val snapshot by state.syncState.collectAsState()
    var confirmingRemoval by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val running = state.running || snapshot.checkingJoin || snapshot.status == SyncStatus.SYNCING
    val checking = state.checking
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoDisclosureRow(
            onClick = { expanded = !expanded },
            onClickLabel = stringResource(if (expanded) Res.string.setup_hide_options else Res.string.setup_show_options),
            headlineContent = { Text(stringResource(Res.string.session_icloud_title)) },
            supportingContent = {
                PosatoCaption(
                    stringResource(
                        when {
                            checking -> Res.string.sync_checking_key
                            state.running || snapshot.status == SyncStatus.SYNCING -> Res.string.session_icloud_running
                            else -> snapshot.status.summary(snapshot.linked)
                        },
                    ),
                )
            },
            leadingContent = { PosatoIcon(PosatoIcons.Cloud, null) },
            trailingContent = { PosatoIcon(if (expanded) PosatoIcons.ChevronUp else PosatoIcons.ChevronDown, null) },
        )
        if (expanded) {
            IcloudOptions(snapshot, running, checking, state::sync) { confirmingRemoval = true }
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

@Composable
private fun IcloudOptions(
    snapshot: AppleSyncState,
    running: Boolean,
    checking: Boolean,
    onSync: () -> Unit,
    onRemove: () -> Unit,
) {
    PosatoCaption(
        stringResource(if (checking) Res.string.sync_checking_key else snapshot.status.message(snapshot.linked)),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (snapshot.status == SyncStatus.WAITING_FOR_KEY) {
        PosatoCaption(stringResource(Res.string.sync_waiting_explanation))
    }
    PosatoActionRow {
        PosatoButton(onClick = onSync, style = PosatoButtonStyle.Secondary, enabled = !running) {
            Text(
                stringResource(
                    when {
                        snapshot.joinPending -> Res.string.action_check_again
                        snapshot.linked -> Res.string.action_sync_now
                        else -> Res.string.action_sync_with_icloud
                    },
                ),
            )
        }
        if (snapshot.linked) {
            PosatoButton(onClick = onRemove, style = PosatoButtonStyle.Quiet, enabled = !running) {
                Text(stringResource(Res.string.sync_remove_workspace), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

internal fun SyncStatus.message(linked: Boolean): StringResource {
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

internal fun SyncStatus.summary(linked: Boolean): StringResource {
    return when (this) {
        SyncStatus.LOCAL_ONLY -> if (linked) Res.string.onboarding_summary_sync_on else Res.string.session_icloud_local
        SyncStatus.PENDING -> Res.string.session_icloud_pending
        SyncStatus.SYNCING -> Res.string.session_icloud_running
        SyncStatus.COMPLETED -> Res.string.session_icloud_completed
        SyncStatus.RETRYABLE -> Res.string.session_icloud_retryable
        SyncStatus.WAITING_FOR_KEY -> Res.string.session_icloud_waiting
        SyncStatus.ACTION_REQUIRED -> Res.string.session_icloud_attention
    }
}
