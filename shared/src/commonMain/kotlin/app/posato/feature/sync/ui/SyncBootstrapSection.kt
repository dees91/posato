package app.posato.feature.sync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoActivityIndicator
import app.posato.core.designsystem.PosatoAlert
import app.posato.core.designsystem.PosatoAlertAction
import app.posato.core.designsystem.PosatoAlertRole
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncAttentionReason
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_check_again
import app.posato.generated.resources.action_sync_now
import app.posato.generated.resources.action_sync_with_icloud
import app.posato.generated.resources.onboarding_summary_sync_on
import app.posato.generated.resources.session_icloud_attention
import app.posato.generated.resources.session_icloud_completed
import app.posato.generated.resources.session_icloud_linking
import app.posato.generated.resources.session_icloud_local
import app.posato.generated.resources.session_icloud_pending
import app.posato.generated.resources.session_icloud_retryable
import app.posato.generated.resources.session_icloud_running
import app.posato.generated.resources.session_icloud_title
import app.posato.generated.resources.session_icloud_waiting
import app.posato.generated.resources.setup_hide_options
import app.posato.generated.resources.setup_show_options
import app.posato.generated.resources.sync_action_required
import app.posato.generated.resources.sync_action_required_local_capacity
import app.posato.generated.resources.sync_action_required_schedule_capacity
import app.posato.generated.resources.sync_action_required_set_capacity
import app.posato.generated.resources.sync_action_required_shared_capacity
import app.posato.generated.resources.sync_cancel_removal
import app.posato.generated.resources.sync_checking_key
import app.posato.generated.resources.sync_completed
import app.posato.generated.resources.sync_folder_or
import app.posato.generated.resources.sync_folder_remove_confirmation
import app.posato.generated.resources.sync_folder_title
import app.posato.generated.resources.sync_icloud_description
import app.posato.generated.resources.sync_icloud_retryable
import app.posato.generated.resources.sync_icloud_running
import app.posato.generated.resources.sync_icloud_waiting_for_key
import app.posato.generated.resources.sync_pending
import app.posato.generated.resources.sync_remove_confirmation
import app.posato.generated.resources.sync_remove_workspace
import app.posato.generated.resources.sync_removing_note
import app.posato.generated.resources.sync_removing_workspace
import app.posato.generated.resources.sync_retryable
import app.posato.generated.resources.sync_waiting_explanation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SyncBootstrapSection(
    state: SyncBootstrapUiState,
    modifier: Modifier = Modifier,
    showsRemovalProgress: Boolean = workspaceRemovalResumes,
) {
    val snapshot by state.syncState.collectAsState()
    val folder by state.folder.collectAsState()
    var confirmingRemoval by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val running = state.running || snapshot.checkingJoin || snapshot.removing || snapshot.status == SyncStatus.SYNCING
    val removingHere = showsRemovalProgress && snapshot.removing
    val checking = state.checking
    val controls = state.folderControls
    val folderMode = controls.supported && (folder != null || !controls.icloudSupported)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        SyncRow(expanded, folderMode, rowSummary(snapshot, checking, removingHere, state.running)) { expanded = !expanded }
        if (expanded && folderMode) {
            FolderOptions(state, snapshot, folder, running, removingHere) { confirmingRemoval = true }
        } else if (expanded) {
            IcloudAndFolderOptions(state, snapshot, running, removingHere) { confirmingRemoval = true }
        }
    }
    state.offer?.let { offer -> PairingCodeAlert(offer.code, state::closeCode) }
    if (confirmingRemoval) {
        RemoveWorkspaceAlert(folderMode, onDismiss = { confirmingRemoval = false }) {
            confirmingRemoval = false
            state.removeWorkspace()
        }
    }
}

@Composable
private fun SyncRow(
    expanded: Boolean,
    folderMode: Boolean,
    summary: StringResource,
    onToggle: () -> Unit,
) {
    PosatoDisclosureRow(
        onClick = onToggle,
        onClickLabel = stringResource(if (expanded) Res.string.setup_hide_options else Res.string.setup_show_options),
        headlineContent = { Text(stringResource(if (folderMode) Res.string.sync_folder_title else Res.string.session_icloud_title)) },
        supportingContent = { PosatoCaption(stringResource(summary)) },
        leadingContent = { PosatoIcon(if (folderMode) PosatoIcons.Folder else PosatoIcons.Cloud, null) },
        trailingContent = { PosatoIcon(if (expanded) PosatoIcons.ChevronUp else PosatoIcons.ChevronDown, null) },
    )
}

/** iCloud on an Apple host, and below it the choice of a folder while no workspace is linked. */
@Composable
private fun IcloudAndFolderOptions(
    state: SyncBootstrapUiState,
    snapshot: AppleSyncState,
    running: Boolean,
    removingHere: Boolean,
    onRemove: () -> Unit,
) {
    IcloudOptions(snapshot, running, state.checking, removingHere, state::sync, onRemove)
    if (state.folderControls.supported && !snapshot.linked && !snapshot.joinPending) {
        PosatoCaption(stringResource(Res.string.sync_folder_or))
        FolderChooser(state, running)
    }
}

/** The collapsed row's line: the current activity first, then the outcome of the latest attempt. */
private fun rowSummary(
    snapshot: AppleSyncState,
    checking: Boolean,
    removingHere: Boolean,
    running: Boolean,
): StringResource {
    return when {
        checking -> Res.string.sync_checking_key
        removingHere -> Res.string.sync_removing_workspace
        running || snapshot.status == SyncStatus.SYNCING -> Res.string.session_icloud_running
        else -> snapshot.status.summary(snapshot.linked)
    }
}

@Composable
private fun RemoveWorkspaceAlert(
    folderMode: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    PosatoAlert(
        title = stringResource(Res.string.sync_remove_workspace),
        message = stringResource(if (folderMode) Res.string.sync_folder_remove_confirmation else Res.string.sync_remove_confirmation),
        onDismiss = onDismiss,
        actions = listOf(
            PosatoAlertAction(stringResource(Res.string.sync_cancel_removal), { onDismiss() }, PosatoAlertRole.Cancel),
            PosatoAlertAction(stringResource(Res.string.sync_remove_workspace), { onRemove() }, PosatoAlertRole.Destructive),
        ),
    )
}

@Composable
private fun IcloudOptions(
    snapshot: AppleSyncState,
    running: Boolean,
    checking: Boolean,
    removing: Boolean,
    onSync: () -> Unit,
    onRemove: () -> Unit,
) {
    PosatoCaption(
        stringResource(
            when {
                checking -> Res.string.sync_checking_key
                removing -> Res.string.sync_removing_workspace
                else -> snapshot.status.message(snapshot.linked, snapshot.reason)
            },
        ),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (removing) {
        PosatoActivityIndicator()
        PosatoCaption(stringResource(Res.string.sync_removing_note))
    }
    if (snapshot.status == SyncStatus.WAITING_FOR_KEY) {
        PosatoCaption(stringResource(Res.string.sync_waiting_explanation))
    }
    if (!snapshot.linked) {
        PosatoCaption(stringResource(Res.string.session_icloud_linking))
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

internal fun SyncStatus.message(
    linked: Boolean,
    reason: SyncAttentionReason? = null,
): StringResource {
    return when (this) {
        SyncStatus.LOCAL_ONLY -> {
            Res.string.sync_icloud_description
        }

        SyncStatus.PENDING -> {
            Res.string.sync_pending
        }

        SyncStatus.SYNCING -> {
            Res.string.sync_icloud_running
        }

        SyncStatus.COMPLETED -> {
            Res.string.sync_completed
        }

        SyncStatus.RETRYABLE -> {
            if (linked) Res.string.sync_retryable else Res.string.sync_icloud_retryable
        }

        SyncStatus.WAITING_FOR_KEY -> {
            Res.string.sync_icloud_waiting_for_key
        }

        SyncStatus.ACTION_REQUIRED -> {
            when (reason) {
                SyncAttentionReason.LOCAL_CAPACITY -> Res.string.sync_action_required_local_capacity
                SyncAttentionReason.SHARED_CAPACITY -> Res.string.sync_action_required_shared_capacity
                SyncAttentionReason.SCHEDULE_CAPACITY -> Res.string.sync_action_required_schedule_capacity
                SyncAttentionReason.SET_CAPACITY -> Res.string.sync_action_required_set_capacity
                null -> Res.string.sync_action_required
            }
        }
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

@Preview(name = "Design review only: capacity reasons", widthDp = 390, heightDp = 844)
@Composable
private fun CapacityReasonsDesignReviewPreview() {
    PosatoTheme {
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            PosatoCaption("Normal text")
            CapacityReasonSamples()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                PosatoCaption("Large text")
                CapacityReasonSamples()
            }
        }
    }
}

@Composable
private fun CapacityReasonSamples() {
    PosatoCaption(stringResource(SyncStatus.ACTION_REQUIRED.message(true, SyncAttentionReason.LOCAL_CAPACITY)))
    PosatoCaption(stringResource(SyncStatus.ACTION_REQUIRED.message(true, SyncAttentionReason.SHARED_CAPACITY)))
    PosatoCaption(stringResource(Res.string.session_icloud_linking))
}
