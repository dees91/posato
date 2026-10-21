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
        title = "Export pause sets",
        onDismiss = state::close,
        actions = {
            PosatoButton(onClick = state::close, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
            PosatoButton(onClick = state::export, enabled = !dialog.busy) { Text("Export file…") }
        },
    ) {
        Text(dialog.contents, style = MaterialTheme.typography.titleSmall)
        PosatoCaption("Websites, the default set, and schedules. App choices stay on this device.")
        Row(
            modifier = Modifier.toggleable(value = dialog.code != null, enabled = !dialog.busy, role = Role.Switch, onValueChange = state::protect),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            Switch(checked = dialog.code != null, onCheckedChange = null, enabled = !dialog.busy)
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                Text("Protect with a code")
                PosatoCaption("Only someone with the code can open the file. Send the code separately, for example by message.")
            }
        }
        if (dialog.code != null) {
            Text(ExportCode.grouped(dialog.code), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
            PosatoCaption(
                "You need this code on the other device, and Posato does not keep it. The code stops a casual reader; " +
                    "someone with the file and a lot of computing power could still open it.",
            )
        } else {
            PosatoCaption("Without a code, anyone who gets the file can read your pause sets and websites.")
        }
        if (dialog.busy) PosatoActivityIndicator()
        if (dialog.failed) PosatoCaption("The file could not be made. Try again.")
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
        title = "Enter the code",
        onDismiss = state::close,
        actions = {
            PosatoButton(onClick = state::close, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
            PosatoButton(onClick = { state.enterCode(code.text.toString()) }, enabled = !dialog.busy) { Text("Open") }
        },
    ) {
        PosatoCaption("This file is protected. Enter the code shown when it was exported.")
        PosatoTextField(
            state = code,
            label = "Code",
            enabled = !dialog.busy,
            errorMessage = dialog.failure,
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
        title = "Import pause sets",
        onDismiss = state::close,
        actions = {
            PosatoButton(onClick = state::close, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
            PosatoButton(
                onClick = state::import,
                style = if (replace) PosatoButtonStyle.Destructive else PosatoButtonStyle.Primary,
                enabled = planned && !dialog.busy,
            ) { Text(if (replace) "Replace" else "Import") }
        },
    ) {
        Text(contentsOf(dialog.preview.file), style = MaterialTheme.typography.titleSmall)
        Text("Choose how to import", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            ModeCard(
                title = "Add to my pause sets",
                detail = "Keeps everything on this device and adds what the file has.",
                selected = !replace,
                onSelect = { if (replace) state.choose(ImportMode.COMBINE) },
            ) {
                if (!replace) {
                    ModeOption("Combine sets with the same name", "A set here gets the websites it is missing.", mode == ImportMode.COMBINE) {
                        state.choose(ImportMode.COMBINE)
                    }
                    ModeOption(
                        "Keep them separate",
                        "Each set from the file is added; a name already here gets \"(imported)\".",
                        mode == ImportMode.SEPARATE,
                    ) {
                        state.choose(ImportMode.SEPARATE)
                    }
                }
            }
            ModeCard(
                title = "Replace my pause sets",
                detail = "Your sets and schedules on this device are replaced by the file's.",
                selected = replace,
                onSelect = { state.choose(ImportMode.REPLACE) },
            )
        }
        PosatoCaption(previewText(dialog.preview.result), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (dialog.linked && planned) PosatoCaption("These changes also reach your other devices.")
        dialog.notice?.let { notice -> PosatoCaption(notice, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (dialog.busy) PosatoActivityIndicator()
    }
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
        title = "Imported",
        onDismiss = state::close,
        actions = {
            if (dialog.schedules.isNotEmpty() && !dialog.turnedOn) {
                PosatoButton(onClick = state::turnOnSchedules, style = PosatoButtonStyle.Secondary, enabled = !dialog.busy) {
                    Text("Turn on imported schedules")
                }
            }
            PosatoButton(onClick = state::close) { Text("Done") }
        },
    ) {
        val changes = changesOf(summary)
        // Imported schedules have their own line below, so "nothing new" is said only when none came either.
        when {
            changes.isNotEmpty() -> PosatoCaption(changes.joinToString(" "))
            dialog.schedules.isEmpty() -> PosatoCaption("Nothing new was added.")
        }
        if (dialog.schedules.isNotEmpty()) {
            if (dialog.turnedOn) {
                PosatoCaption("Imported schedules are on.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                PosatoCaption("Imported schedules are off: ${summary.schedules.joinToString(", ")}. Turn them on here or later in Schedules.")
                if (dialog.openNow.isNotEmpty()) {
                    PosatoCaption("${dialog.openNow.joinToString(", ")} would start a pause right away.")
                }
            }
        }
        if (dialog.failed) PosatoCaption("The schedules could not be turned on. Try again in Schedules.")
        if (dialog.busy) PosatoActivityIndicator()
    }
}

@Composable
private fun MessageDialog(
    dialog: TransferDialog.Message,
    state: PauseSetTransferUiState,
) {
    TransferAlert(title = dialog.title, onDismiss = state::close, actions = { PosatoButton(onClick = state::close) { Text("OK") } }) {
        Text(dialog.text)
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

private fun changesOf(summary: ImportSummary): List<String> {
    return buildList {
        if (summary.changed.isNotEmpty()) add("Pause sets changed: ${summary.changed.joinToString(", ")}.")
        if (summary.removed.isNotEmpty()) add("Removed: ${summary.removed.joinToString(", ")}.")
        if (summary.removedSchedules.isNotEmpty()) add("Schedules removed: ${summary.removedSchedules.joinToString(", ")}.")
        if (summary.websitesAdded > 0) add(if (summary.websitesAdded == 1) "1 website added." else "${summary.websitesAdded} websites added.")
    }
}
