package app.posato.feature.sync.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoActivityIndicator
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTextField
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.folder.LinkSharing
import app.posato.feature.sync.folder.PairingAcceptResult
import app.posato.feature.sync.folder.pairingLink
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_sync_now
import app.posato.generated.resources.sync_folder_add_device
import app.posato.generated.resources.sync_folder_browse
import app.posato.generated.resources.sync_folder_code_done
import app.posato.generated.resources.sync_folder_code_failed
import app.posato.generated.resources.sync_folder_code_message
import app.posato.generated.resources.sync_folder_code_title
import app.posato.generated.resources.sync_folder_copy_link
import app.posato.generated.resources.sync_folder_description
import app.posato.generated.resources.sync_folder_invalid
import app.posato.generated.resources.sync_folder_join_expired
import app.posato.generated.resources.sync_folder_join_invalid
import app.posato.generated.resources.sync_folder_join_not_found
import app.posato.generated.resources.sync_folder_join_refused
import app.posato.generated.resources.sync_folder_join_unavailable
import app.posato.generated.resources.sync_folder_join_wrong_workspace
import app.posato.generated.resources.sync_folder_link_copied
import app.posato.generated.resources.sync_folder_path_label
import app.posato.generated.resources.sync_folder_qr_description
import app.posato.generated.resources.sync_folder_share_link
import app.posato.generated.resources.sync_folder_use
import app.posato.generated.resources.sync_remove_workspace
import app.posato.generated.resources.sync_removing_note
import app.posato.generated.resources.sync_removing_workspace
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FolderOptions(
    state: SyncBootstrapUiState,
    snapshot: AppleSyncState,
    folder: String?,
    running: Boolean,
    removing: Boolean,
    onRemove: () -> Unit,
) {
    folderMessage(snapshot, folder, state.checking, removing)?.let { message ->
        PosatoCaption(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (removing) {
        PosatoActivityIndicator()
        PosatoCaption(stringResource(Res.string.sync_removing_note))
    }
    if (snapshot.linked) {
        LinkedFolder(state, snapshot, folder, running, onRemove)
    } else {
        FolderJoinStepsView(state, folder, running)
    }
}

/**
 * What the expanded folder row says above its controls. While unlinked, the steps speak for themselves, so only an
 * attempt that went wrong adds a sentence; step 2 explains a removed workspace's copy.
 */
private fun folderMessage(
    snapshot: AppleSyncState,
    folder: String?,
    checking: Boolean,
    removing: Boolean,
): StringResource? {
    return when {
        removing -> Res.string.sync_removing_workspace
        snapshot.linked -> snapshot.statusMessage(folderMode = true)
        checking -> null
        folder == null -> Res.string.sync_folder_description
        snapshot.status == SyncStatus.RETRYABLE || snapshot.status == SyncStatus.ACTION_REQUIRED -> snapshot.statusMessage(folderMode = true)
        else -> null
    }
}

/** A linked folder workspace: the folder, one primary action to add a device, and quiet sync and removal. */
@Composable
private fun LinkedFolder(
    state: SyncBootstrapUiState,
    snapshot: AppleSyncState,
    folder: String?,
    running: Boolean,
    onRemove: () -> Unit,
) {
    folder?.let { FolderChip(it, onChange = null) }
    state.offerFailed.takeIf { it }?.let { PosatoCaption(stringResource(Res.string.sync_folder_code_failed)) }
    if (snapshot.status != SyncStatus.ACTION_REQUIRED) {
        PosatoButton(onClick = state::showCode, style = PosatoButtonStyle.Primary, enabled = !running, controlId = "sync_folder_add_device") {
            Text(stringResource(Res.string.sync_folder_add_device))
        }
    }
    PosatoActionRow {
        PosatoButton(onClick = state::sync, style = PosatoButtonStyle.Quiet, enabled = !running, controlId = "action_sync_now") {
            Text(stringResource(Res.string.action_sync_now))
        }
        PosatoButton(onClick = onRemove, style = PosatoButtonStyle.Quiet, enabled = !running, controlId = "sync_remove_workspace") {
            Text(stringResource(Res.string.sync_remove_workspace), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun FolderChooser(
    state: SyncBootstrapUiState,
    running: Boolean,
    primary: Boolean = false,
) {
    val path = rememberTextFieldState()
    val typed = state.folderControls.acceptsTypedPath
    val lead = if (primary) PosatoButtonStyle.Primary else PosatoButtonStyle.Secondary
    if (typed) {
        PosatoTextField(
            state = path,
            label = stringResource(Res.string.sync_folder_path_label),
            enabled = !running,
            errorMessage = if (state.folderRefused) stringResource(Res.string.sync_folder_invalid) else null,
            onSubmit = { state.chooseFolder(path.text.toString()) },
        )
    } else if (state.folderRefused) {
        PosatoCaption(stringResource(Res.string.sync_folder_invalid))
    }
    PosatoActionRow {
        if (typed) {
            PosatoButton(onClick = { state.chooseFolder(path.text.toString()) }, style = lead, enabled = !running, controlId = "sync_folder_use") {
                Text(stringResource(Res.string.sync_folder_use))
            }
        }
        if (state.folderControls.canBrowse) {
            PosatoButton(
                onClick = state::browseFolder,
                style = if (typed) PosatoButtonStyle.Quiet else lead,
                enabled = !running,
                controlId = "sync_folder_browse",
            ) {
                Text(stringResource(Res.string.sync_folder_browse))
            }
        }
    }
}

/**
 * The member's pairing code: a QR code that a phone's camera opens in Posato, the code as text for a computer, and
 * the link to share. The code is the same 128-bit secret in every form (ADR 0010).
 */
@Composable
internal fun PairingCodeDialog(
    code: String,
    sharing: LinkSharing,
    copied: Boolean,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    val qrDescription = stringResource(Res.string.sync_folder_qr_description)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(Res.string.sync_folder_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.sync_folder_code_message))
                Image(
                    painter = rememberQrCodePainter(pairingLink(code)),
                    contentDescription = qrDescription,
                    modifier = Modifier.size(QR_SIZE).background(Color.White).padding(PosatoSpace.Small),
                )
                SelectionContainer {
                    Text(
                        code.chunked(CODE_GROUP_CHARACTERS).joinToString(" "),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                if (copied) {
                    PosatoCaption(
                        stringResource(Res.string.sync_folder_link_copied),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            PosatoActionRow {
                PosatoButton(onClick = onShare, style = PosatoButtonStyle.Secondary) {
                    Text(stringResource(if (sharing.usesShareSheet) Res.string.sync_folder_share_link else Res.string.sync_folder_copy_link))
                }
                PosatoButton(onClick = onDone, style = PosatoButtonStyle.Primary, controlId = "sync_folder_code_done") {
                    Text(stringResource(Res.string.sync_folder_code_done))
                }
            }
        },
    )
}

internal fun PairingAcceptResult.joinMessage(): StringResource? {
    return when (this) {
        PairingAcceptResult.JOINED -> null
        PairingAcceptResult.INVALID_CODE -> Res.string.sync_folder_join_invalid
        PairingAcceptResult.NOT_FOUND -> Res.string.sync_folder_join_not_found
        PairingAcceptResult.EXPIRED -> Res.string.sync_folder_join_expired
        PairingAcceptResult.REFUSED -> Res.string.sync_folder_join_refused
        PairingAcceptResult.WRONG_WORKSPACE -> Res.string.sync_folder_join_wrong_workspace
        PairingAcceptResult.UNAVAILABLE -> Res.string.sync_folder_join_unavailable
    }
}

/** The code is shown in groups of three characters, so it is easier to read out and type. */
private const val CODE_GROUP_CHARACTERS = 3

private val QR_SIZE = 200.dp
