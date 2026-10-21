package app.posato.feature.sync.ui

import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.generated.resources.Res
import app.posato.generated.resources.sync_folder_action_required
import app.posato.generated.resources.sync_folder_description
import app.posato.generated.resources.sync_folder_removed_copy
import app.posato.generated.resources.sync_folder_retryable
import app.posato.generated.resources.sync_folder_running
import app.posato.generated.resources.sync_folder_waiting_for_key
import app.posato.generated.resources.sync_retryable
import org.jetbrains.compose.resources.StringResource

/**
 * The sentence for the latest sync state, shown in the section and spoken by announcements. In folder mode it never
 * names iCloud; the iCloud sentences stay for iCloud mode.
 */
internal fun AppleSyncState.statusMessage(folderMode: Boolean): StringResource {
    if (!folderMode) return status.message(linked, reason)
    return when {
        removedWorkspaceCopy && !linked -> Res.string.sync_folder_removed_copy
        status == SyncStatus.LOCAL_ONLY -> Res.string.sync_folder_description
        status == SyncStatus.SYNCING -> Res.string.sync_folder_running
        status == SyncStatus.RETRYABLE -> if (linked) Res.string.sync_retryable else Res.string.sync_folder_retryable
        status == SyncStatus.WAITING_FOR_KEY -> Res.string.sync_folder_waiting_for_key
        status == SyncStatus.ACTION_REQUIRED && reason == null -> Res.string.sync_folder_action_required
        else -> status.message(linked, reason)
    }
}

/** Folder mode: a folder is chosen, or the host offers no iCloud. */
internal fun SyncBootstrapUiState.folderMode(folder: String?): Boolean {
    return folderControls.supported && (folder != null || !folderControls.icloudSupported)
}
