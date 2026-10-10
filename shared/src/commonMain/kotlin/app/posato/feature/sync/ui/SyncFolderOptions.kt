package app.posato.feature.sync.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoActivityIndicator
import app.posato.core.designsystem.PosatoAlert
import app.posato.core.designsystem.PosatoAlertAction
import app.posato.core.designsystem.PosatoAlertRole
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoTextField
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.folder.PairingAcceptResult
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_check_again
import app.posato.generated.resources.action_sync_now
import app.posato.generated.resources.action_sync_with_folder
import app.posato.generated.resources.sync_checking_key
import app.posato.generated.resources.sync_folder_add_device
import app.posato.generated.resources.sync_folder_browse
import app.posato.generated.resources.sync_folder_change
import app.posato.generated.resources.sync_folder_chosen
import app.posato.generated.resources.sync_folder_code_done
import app.posato.generated.resources.sync_folder_code_failed
import app.posato.generated.resources.sync_folder_code_label
import app.posato.generated.resources.sync_folder_code_message
import app.posato.generated.resources.sync_folder_code_title
import app.posato.generated.resources.sync_folder_description
import app.posato.generated.resources.sync_folder_invalid
import app.posato.generated.resources.sync_folder_join
import app.posato.generated.resources.sync_folder_join_expired
import app.posato.generated.resources.sync_folder_join_invalid
import app.posato.generated.resources.sync_folder_join_not_found
import app.posato.generated.resources.sync_folder_join_refused
import app.posato.generated.resources.sync_folder_join_unavailable
import app.posato.generated.resources.sync_folder_join_wrong_workspace
import app.posato.generated.resources.sync_folder_linking
import app.posato.generated.resources.sync_folder_path_label
import app.posato.generated.resources.sync_folder_use
import app.posato.generated.resources.sync_folder_waiting_for_key
import app.posato.generated.resources.sync_remove_workspace
import app.posato.generated.resources.sync_removing_note
import app.posato.generated.resources.sync_removing_workspace
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
    PosatoCaption(
        stringResource(
            when {
                state.checking -> Res.string.sync_checking_key
                removing -> Res.string.sync_removing_workspace
                folder == null || (snapshot.status == SyncStatus.LOCAL_ONLY && !snapshot.linked) -> Res.string.sync_folder_description
                snapshot.status == SyncStatus.WAITING_FOR_KEY -> Res.string.sync_folder_waiting_for_key
                else -> snapshot.status.message(snapshot.linked, snapshot.reason)
            },
        ),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (removing) {
        PosatoActivityIndicator()
        PosatoCaption(stringResource(Res.string.sync_removing_note))
    }
    if (folder == null) {
        FolderChooser(state, running)
        return
    }
    PosatoCaption(stringResource(Res.string.sync_folder_chosen, folder))
    if (!snapshot.linked && !snapshot.joinPending) {
        PosatoCaption(stringResource(Res.string.sync_folder_linking))
    }
    if (snapshot.joinPending && !snapshot.linked) {
        JoinWithCode(state, running)
    }
    state.offerFailed.takeIf { it }?.let { PosatoCaption(stringResource(Res.string.sync_folder_code_failed)) }
    PosatoActionRow {
        PosatoButton(onClick = state::sync, style = PosatoButtonStyle.Secondary, enabled = !running) {
            Text(
                stringResource(
                    when {
                        snapshot.joinPending -> Res.string.action_check_again
                        snapshot.linked -> Res.string.action_sync_now
                        else -> Res.string.action_sync_with_folder
                    },
                ),
            )
        }
        if (snapshot.linked) {
            PosatoButton(onClick = state::showCode, style = PosatoButtonStyle.Secondary, enabled = !running) {
                Text(stringResource(Res.string.sync_folder_add_device))
            }
            PosatoButton(onClick = onRemove, style = PosatoButtonStyle.Quiet, enabled = !running) {
                Text(stringResource(Res.string.sync_remove_workspace), color = MaterialTheme.colorScheme.error)
            }
        } else {
            PosatoButton(onClick = state::clearFolder, style = PosatoButtonStyle.Quiet, enabled = !running) {
                Text(stringResource(Res.string.sync_folder_change))
            }
        }
    }
}

@Composable
internal fun FolderChooser(
    state: SyncBootstrapUiState,
    running: Boolean,
) {
    val path = rememberTextFieldState()
    val typed = state.folderControls.acceptsTypedPath
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
            PosatoButton(onClick = { state.chooseFolder(path.text.toString()) }, style = PosatoButtonStyle.Secondary, enabled = !running) {
                Text(stringResource(Res.string.sync_folder_use))
            }
        }
        if (state.folderControls.canBrowse) {
            PosatoButton(
                onClick = state::browseFolder,
                style = if (typed) PosatoButtonStyle.Quiet else PosatoButtonStyle.Secondary,
                enabled = !running,
            ) {
                Text(stringResource(Res.string.sync_folder_browse))
            }
        }
    }
}

@Composable
private fun JoinWithCode(
    state: SyncBootstrapUiState,
    running: Boolean,
) {
    val code = rememberTextFieldState()
    PosatoTextField(
        state = code,
        label = stringResource(Res.string.sync_folder_code_label),
        enabled = !running,
        errorMessage = state.joinResult?.message()?.let { stringResource(it) },
        onSubmit = { state.join(code.text.toString()) },
    )
    PosatoActionRow {
        PosatoButton(onClick = { state.join(code.text.toString()) }, style = PosatoButtonStyle.Secondary, enabled = !running) {
            Text(stringResource(Res.string.sync_folder_join))
        }
    }
}

@Composable
internal fun PairingCodeAlert(
    code: String,
    onDone: () -> Unit,
) {
    PosatoAlert(
        title = stringResource(Res.string.sync_folder_code_title),
        message = stringResource(Res.string.sync_folder_code_message) + "\n\n" + code.chunked(3).joinToString(" "),
        onDismiss = onDone,
        actions = listOf(PosatoAlertAction(stringResource(Res.string.sync_folder_code_done), { onDone() }, PosatoAlertRole.Cancel)),
    )
}

private fun PairingAcceptResult.message(): StringResource? {
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
