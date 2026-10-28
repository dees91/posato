package app.posato.feature.transfer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoActivityIndicator
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTextField
import app.posato.feature.transfer.data.ExportCode
import app.posato.feature.transfer.domain.ImportMode
import app.posato.feature.transfer.domain.ImportPlanResult
import app.posato.feature.transfer.domain.ImportSummary
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_export_file
import app.posato.generated.resources.action_import_word
import app.posato.generated.resources.action_open
import app.posato.generated.resources.action_replace
import app.posato.generated.resources.action_turn_on_imported_schedules
import app.posato.generated.resources.presence_first_close_ok
import app.posato.generated.resources.sync_folder_code_done
import app.posato.generated.resources.transfer_add_to_sets
import app.posato.generated.resources.transfer_add_to_sets_body
import app.posato.generated.resources.transfer_choose_how
import app.posato.generated.resources.transfer_code_body
import app.posato.generated.resources.transfer_code_label
import app.posato.generated.resources.transfer_code_limit
import app.posato.generated.resources.transfer_combine
import app.posato.generated.resources.transfer_combine_body
import app.posato.generated.resources.transfer_enter_code
import app.posato.generated.resources.transfer_export_scope
import app.posato.generated.resources.transfer_export_title
import app.posato.generated.resources.transfer_file_failed
import app.posato.generated.resources.transfer_import_title
import app.posato.generated.resources.transfer_imported
import app.posato.generated.resources.transfer_keep_separate
import app.posato.generated.resources.transfer_keep_separate_body
import app.posato.generated.resources.transfer_no_code
import app.posato.generated.resources.transfer_nothing_new
import app.posato.generated.resources.transfer_protect
import app.posato.generated.resources.transfer_protect_body
import app.posato.generated.resources.transfer_reaches_devices
import app.posato.generated.resources.transfer_replace_sets
import app.posato.generated.resources.transfer_replace_sets_body
import app.posato.generated.resources.transfer_schedules_failed
import app.posato.generated.resources.transfer_schedules_off
import app.posato.generated.resources.transfer_schedules_on
import app.posato.generated.resources.transfer_would_start
import org.jetbrains.compose.resources.stringResource

/** Shows the current export or import step, if any. */
@Composable
internal fun PauseSetTransferDialogs(state: PauseSetTransferUiState) {
    when (val dialog = state.dialog) {
        is TransferDialog.Export -> ExportDialog(dialog, state)
        is TransferDialog.Code -> CodeDialog(dialog, state)
        is TransferDialog.Review -> ReviewDialog(dialog, state)
        is TransferDialog.Result -> ResultDialog(dialog, state)
        is TransferDialog.Message -> MessageDialog(dialog, state)
        null -> Unit
    }
}

@Composable
private fun ExportDialog(
    dialog: TransferDialog.Export,
    state: PauseSetTransferUiState,
) {
    TransferAlert(
        title = stringResource(Res.string.transfer_export_title),
        onDismiss = state::close,
        actions = {
            PosatoButton(
                onClick = state::close,
                style = PosatoButtonStyle.Quiet,
                controlId = "action_cancel",
            ) { Text(stringResource(Res.string.action_cancel)) }
            PosatoButton(
                onClick = state::export,
                enabled = !dialog.busy,
                controlId = "action_export_file",
            ) { Text(stringResource(Res.string.action_export_file)) }
        },
    ) {
        Text(contentsOf(dialog.file), style = MaterialTheme.typography.titleSmall)
        PosatoCaption(stringResource(Res.string.transfer_export_scope))
        Row(
            modifier = Modifier.toggleable(value = dialog.code != null, enabled = !dialog.busy, role = Role.Switch, onValueChange = state::protect),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            Switch(checked = dialog.code != null, onCheckedChange = null, enabled = !dialog.busy)
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                Text(stringResource(Res.string.transfer_protect))
                PosatoCaption(stringResource(Res.string.transfer_protect_body))
            }
        }
        if (dialog.code != null) {
            Text(ExportCode.grouped(dialog.code), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
            PosatoCaption(stringResource(Res.string.transfer_code_limit))
        } else {
            PosatoCaption(stringResource(Res.string.transfer_no_code))
        }
        if (dialog.busy) PosatoActivityIndicator()
        if (dialog.failed) PosatoCaption(stringResource(Res.string.transfer_file_failed))
    }
}

@Composable
private fun CodeDialog(
    dialog: TransferDialog.Code,
    state: PauseSetTransferUiState,
) {
    // Not saved across process death: the code never goes into saved state.
    val code = remember { TextFieldState() }
    TransferAlert(
        title = stringResource(Res.string.transfer_enter_code),
        onDismiss = state::close,
        actions = {
            PosatoButton(
                onClick = state::close,
                style = PosatoButtonStyle.Quiet,
                controlId = "action_cancel",
            ) { Text(stringResource(Res.string.action_cancel)) }
            PosatoButton(
                onClick = { state.enterCode(code.text.toString()) },
                enabled = !dialog.busy,
                controlId = "action_open",
            ) { Text(stringResource(Res.string.action_open)) }
        },
    ) {
        PosatoCaption(stringResource(Res.string.transfer_code_body))
        PosatoTextField(
            state = code,
            label = stringResource(Res.string.transfer_code_label),
            enabled = !dialog.busy,
            errorMessage = dialog.failure?.let { stringResource(it) },
            onSubmit = { state.enterCode(code.text.toString()) },
        )
        if (dialog.busy) PosatoActivityIndicator()
    }
}

@Composable
private fun ReviewDialog(
    dialog: TransferDialog.Review,
    state: PauseSetTransferUiState,
) {
    val mode = dialog.preview.mode
    val replace = mode == ImportMode.REPLACE
    val planned = dialog.preview.result is ImportPlanResult.Planned
    TransferAlert(
        title = stringResource(Res.string.transfer_import_title),
        onDismiss = state::close,
        actions = { ReviewActions(state, replace, planned, dialog.busy) },
    ) {
        Text(contentsOf(dialog.preview.file), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.transfer_choose_how), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            ModeCard(
                title = stringResource(Res.string.transfer_add_to_sets),
                detail = stringResource(Res.string.transfer_add_to_sets_body),
                selected = !replace,
                onSelect = { if (replace) state.choose(ImportMode.COMBINE) },
            ) {
                if (!replace) {
                    ModeOption(
                        stringResource(Res.string.transfer_combine),
                        stringResource(Res.string.transfer_combine_body),
                        mode == ImportMode.COMBINE,
                    ) {
                        state.choose(ImportMode.COMBINE)
                    }
                    ModeOption(
                        stringResource(Res.string.transfer_keep_separate),
                        stringResource(Res.string.transfer_keep_separate_body),
                        mode == ImportMode.SEPARATE,
                    ) {
                        state.choose(ImportMode.SEPARATE)
                    }
                }
            }
            ModeCard(
                title = stringResource(Res.string.transfer_replace_sets),
                detail = stringResource(Res.string.transfer_replace_sets_body),
                selected = replace,
                onSelect = { state.choose(ImportMode.REPLACE) },
            )
        }
        PosatoCaption(previewText(dialog.preview.result), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (dialog.linked && planned) PosatoCaption(stringResource(Res.string.transfer_reaches_devices))
        val notice = dialog.notice?.let { stringResource(it) }
        notice?.let { text -> PosatoCaption(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (dialog.busy) PosatoActivityIndicator()
    }
}

@Composable
private fun ReviewActions(
    state: PauseSetTransferUiState,
    replace: Boolean,
    planned: Boolean,
    busy: Boolean,
) {
    PosatoButton(
        onClick = state::close,
        style = PosatoButtonStyle.Quiet,
        controlId = "action_cancel",
    ) { Text(stringResource(Res.string.action_cancel)) }
    PosatoButton(
        onClick = state::import,
        style = if (replace) PosatoButtonStyle.Destructive else PosatoButtonStyle.Primary,
        enabled = planned && !busy,
        controlId = "action_import_word",
    ) { Text(if (replace) stringResource(Res.string.action_replace) else stringResource(Res.string.action_import_word)) }
}

/** A choice that reads as one: a radio, a title, and what it does, with a stronger border when chosen. */
@Composable
private fun ModeCard(
    title: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
    options: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Column(Modifier.padding(PosatoSpace.Medium), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    PosatoCaption(detail)
                }
            }
            options()
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected,
            onClick = onSelect,
            role = Role.RadioButton,
        ).padding(start = PosatoSpace.Large),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(title)
            PosatoCaption(detail)
        }
    }
}

@Composable
private fun ResultDialog(
    dialog: TransferDialog.Result,
    state: PauseSetTransferUiState,
) {
    val summary = dialog.summary
    TransferAlert(
        title = stringResource(Res.string.transfer_imported),
        onDismiss = state::close,
        actions = {
            if (dialog.schedules.isNotEmpty() && !dialog.turnedOn) {
                PosatoButton(
                    onClick = state::turnOnSchedules,
                    style = PosatoButtonStyle.Secondary,
                    enabled = !dialog.busy,
                    controlId = "action_turn_on_imported_schedules",
                ) {
                    Text(stringResource(Res.string.action_turn_on_imported_schedules))
                }
            }
            PosatoButton(onClick = state::close, controlId = "sync_folder_code_done") { Text(stringResource(Res.string.sync_folder_code_done)) }
        },
    ) {
        val schedulesOff = stringResource(Res.string.transfer_schedules_off, summary.schedules.joinToString(", "))
        val wouldStart = stringResource(Res.string.transfer_would_start, dialog.openNow.joinToString(", "))
        val changes = changesOf(summary)
        // Imported schedules have their own line below, so "nothing new" is said only when none came either.
        when {
            changes.isNotEmpty() -> PosatoCaption(changes.joinToString(" "))
            dialog.schedules.isEmpty() -> PosatoCaption(stringResource(Res.string.transfer_nothing_new))
        }
        if (dialog.schedules.isNotEmpty()) {
            if (dialog.turnedOn) {
                PosatoCaption(stringResource(Res.string.transfer_schedules_on), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                PosatoCaption(schedulesOff)
                if (dialog.openNow.isNotEmpty()) {
                    PosatoCaption(wouldStart)
                }
            }
        }
        if (dialog.failed) PosatoCaption(stringResource(Res.string.transfer_schedules_failed))
        if (dialog.busy) PosatoActivityIndicator()
    }
}

@Composable
private fun MessageDialog(
    dialog: TransferDialog.Message,
    state: PauseSetTransferUiState,
) {
    TransferAlert(title = stringResource(dialog.title), onDismiss = state::close, actions = {
        PosatoButton(onClick = state::close, controlId = "presence_first_close_ok") { Text(stringResource(Res.string.presence_first_close_ok)) }
    }) {
        Text(stringResource(dialog.text, dialog.argument.orEmpty()))
    }
}

@Composable
private fun TransferAlert(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                content()
            }
        },
        confirmButton = { PosatoActionRow { actions() } },
    )
}
