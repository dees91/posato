package app.posato.feature.schedules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoEmptyState
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSelectionRow
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.core.navigation.PosatoNavStack
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.onboarding.MacSetupOverview
import app.posato.feature.schedules.domain.ScheduleId

/** What this device still needs before its schedules can run; saving plans never waits for it. */
internal data class ScheduleDeviceReadiness(
    val mac: MacScheduleReadiness = MacScheduleReadiness.READY,
    val screenTimeAllowed: Boolean = true,
    val offerNotices: Boolean = false,
)

internal class ScheduleActions(
    val onAdd: () -> Unit = {},
    val onEdit: (ScheduleRowModel) -> Unit = {},
    val onSetEnabled: (ScheduleRowModel, Boolean) -> Unit = { _, _ -> },
    val onSkipNext: (ScheduleRowModel) -> Unit = {},
    val onConfirmDelete: (ScheduleId?) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onUpdateDraft: (ScheduleDraft) -> Unit = {},
    val onSave: () -> Unit = {},
    val onCloseEditor: () -> Unit = {},
    val onShowSetup: (Boolean) -> Unit = {},
    val onAllowScreenTime: () -> Unit = {},
    val onTurnOnNotices: () -> Unit = {},
    val onAllowSchedules: () -> Unit = {},
)

@Composable
internal fun SchedulesScreen(
    holder: SchedulesHolder,
    device: PosatoDevice,
    layout: PosatoLayout,
    readiness: ScheduleDeviceReadiness,
    modifier: Modifier = Modifier,
    onAllowScreenTime: () -> Unit = {},
    onTurnOnNotices: () -> Unit = {},
    onAllowSchedules: () -> Unit = {},
    macSetupContent: (@Composable () -> Unit)? = null,
    setupPromptOpen: Boolean = false,
) {
    LaunchedEffect(holder) { holder.run() }
    val actions = ScheduleActions(
        onAdd = { holder.openEditor(null) },
        onEdit = holder::openEditor,
        onSetEnabled = holder::setEnabled,
        onSkipNext = holder::skipNext,
        onConfirmDelete = holder::confirmDelete,
        onDelete = holder::delete,
        onUpdateDraft = holder::updateDraft,
        onSave = holder::save,
        onCloseEditor = holder::closeEditor,
        onShowSetup = holder::showSetup,
        onAllowScreenTime = onAllowScreenTime,
        onTurnOnNotices = onTurnOnNotices,
        onAllowSchedules = onAllowSchedules,
    )
    SchedulesScreen(holder.state, device, layout, readiness, actions, modifier, macSetupContent, setupPromptOpen)
}

@Composable
internal fun SchedulesScreen(
    state: SchedulesUiState,
    device: PosatoDevice,
    layout: PosatoLayout,
    readiness: ScheduleDeviceReadiness,
    actions: ScheduleActions,
    modifier: Modifier = Modifier,
    macSetupContent: (@Composable () -> Unit)? = null,
    setupPromptOpen: Boolean = false,
) {
    val inset = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
    val editor = state.editor
    val top = when {
        editor != null -> SchedulesRoute.Editor(editor.id)
        state.showingSetup -> SchedulesRoute.Setup
        else -> null
    }
    PosatoNavStack(
        listOfNotNull(SchedulesRoute.List, top),
        onBack = { if (top is SchedulesRoute.Editor) actions.onCloseEditor() else actions.onShowSetup(false) },
        modifier = modifier.fillMaxSize(),
        backEnabled = !(top == SchedulesRoute.Setup && setupPromptOpen) && !(top is SchedulesRoute.Editor && state.saving),
    ) { route ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            when (route) {
                is SchedulesRoute.Editor -> rememberLastPresent(editor)?.let { ScheduleEditor(it, state, layout, actions) }
                SchedulesRoute.Setup -> ScheduleSetup(device, layout, actions, macSetupContent)
                SchedulesRoute.List -> ScheduleList(state, device, layout, readiness, actions)
            }
        }
    }
}

/** Schedules' screens: the list, with the editor or this Mac's setup above it. */
private sealed interface SchedulesRoute {
    data object List : SchedulesRoute

    data object Setup : SchedulesRoute

    data class Editor(
        val id: ScheduleId?
    ) : SchedulesRoute
}

@Composable
private fun ScheduleList(
    state: SchedulesUiState,
    device: PosatoDevice,
    layout: PosatoLayout,
    readiness: ScheduleDeviceReadiness,
    actions: ScheduleActions,
) {
    PosatoHeading("A rhythm that gives you room.", eyebrow = "SCHEDULES", layout = layout)
    if (state.showUpdateNote) {
        PosatoNotice { Text("Schedules sync with your other devices. Update Posato on each of them to keep them in sync.") }
    }
    ScheduleReadinessCard(device, readiness, actions)
    if (state.changeFailed) {
        PosatoNotice(tone = PosatoTone.Caution) { Text("Couldn't save that change. Try again.") }
    }
    if (state.loaded && state.schedules.isEmpty()) {
        PosatoEmptyState(
            title = "Make time for a regular pause.",
            description = "Choose the days and hours that work for you. Start with one schedule, then add another when you need it.",
        )
    }
    if (device != PosatoDevice.Mac || readiness.mac == MacScheduleReadiness.READY) {
        PosatoButton(onClick = actions.onAdd, enabled = !state.atCapacity) { Text("Add schedule") }
        if (state.atCapacity) {
            PosatoCaption("You have 10 schedules, the most Posato keeps. Delete one to add another.")
        }
    }
    state.schedules.forEach { row -> key(row.id.hex) { ScheduleRow(row, state.confirmingDelete == row.id, actions) } }
    if (readiness.offerNotices && state.schedules.isNotEmpty()) {
        PosatoButton(onClick = actions.onTurnOnNotices, style = PosatoButtonStyle.Quiet) { Text("Turn on pause notices") }
        PosatoCaption("Posato can tell you when a scheduled pause starts and ends. Blocking works either way.")
    }
    if (state.schedules.isNotEmpty()) {
        PosatoCaption("Times follow each device's own clock.")
    }
}

@Composable
private fun ScheduleReadinessCard(
    device: PosatoDevice,
    readiness: ScheduleDeviceReadiness,
    actions: ScheduleActions,
) {
    if (device == PosatoDevice.Mac && readiness.mac == MacScheduleReadiness.SETUP) {
        PosatoPanel(modifier = Modifier.fillMaxWidth()) {
            PosatoBody("One setup for your pauses and schedules.")
            PosatoCaption("Set up this Mac once, then add schedules here.")
            PosatoButton(onClick = { actions.onShowSetup(true) }) { Text("Set up this Mac") }
        }
    } else if (device == PosatoDevice.Mac && readiness.mac == MacScheduleReadiness.CONSENT) {
        PosatoPanel(modifier = Modifier.fillMaxWidth()) {
            PosatoBody("Schedules can't start on this Mac yet.")
            PosatoCaption("Allow them to start on their own, including schedules added on your other devices. No password is needed.")
            PosatoButton(onClick = actions.onAllowSchedules) { Text("Allow schedules to start on this Mac") }
        }
    } else if (device != PosatoDevice.Mac && !readiness.screenTimeAllowed) {
        PosatoPanel(modifier = Modifier.fillMaxWidth()) {
            PosatoBody("Allow Screen Time so schedules can pause apps and websites on this ${device.noun}.")
            PosatoCaption("You can save schedules now; they run here once access is allowed.")
            PosatoButton(onClick = actions.onAllowScreenTime) { Text("Allow Screen Time") }
        }
    }
}

@Composable
private fun ScheduleRow(
    row: ScheduleRowModel,
    confirmingDelete: Boolean,
    actions: ScheduleActions,
) {
    PosatoPanel(modifier = Modifier.fillMaxWidth()) {
        PosatoSelectionRow(
            modifier = Modifier.fillMaxWidth(),
            checked = row.enabled,
            onCheckedChange = { actions.onSetEnabled(row, it) },
            enabled = !row.refused,
            supportingContent = { PosatoCaption(listOfNotNull("${row.daysLabel} · ${row.hoursLabel}", row.setLabel).joinToString(" · ")) },
        ) { Text(row.name) }
        row.setProblem?.let { problem -> PosatoNotice(tone = PosatoTone.Caution) { Text(problem) } }
        when {
            row.refused -> PosatoNotice(tone = PosatoTone.Caution) {
                Text("Couldn't sync: 10 schedules is the most. Delete a schedule on any device and this one syncs by itself.")
            }

            !row.enabled -> PosatoBody("Turned off")

            else -> row.nextRunLabel?.let { PosatoBody(it) }
        }
        row.skippedLabel?.let { PosatoCaption(it) }
        if (confirmingDelete) {
            PosatoBody("Delete ${row.name}? It is deleted on your other devices too.")
            PosatoActionRow {
                PosatoButton(onClick = actions.onDelete, style = PosatoButtonStyle.Secondary) { Text("Delete") }
                PosatoButton(onClick = { actions.onConfirmDelete(null) }, style = PosatoButtonStyle.Quiet) { Text("Keep") }
            }
        } else {
            PosatoActionRow {
                PosatoButton(onClick = { actions.onEdit(row) }, style = PosatoButtonStyle.Secondary, enabled = !row.refused) {
                    Text("Edit ${row.name}")
                }
                if (row.enabled && row.canSkip) {
                    PosatoButton(onClick = { actions.onSkipNext(row) }, style = PosatoButtonStyle.Quiet) { Text("Skip next") }
                }
                PosatoButton(onClick = { actions.onConfirmDelete(row.id) }, style = PosatoButtonStyle.Quiet) { Text("Delete ${row.name}") }
            }
        }
    }
}

@Composable
private fun ScheduleSetup(
    device: PosatoDevice,
    layout: PosatoLayout,
    actions: ScheduleActions,
    macSetupContent: (@Composable () -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoButton(onClick = { actions.onShowSetup(false) }, style = PosatoButtonStyle.Quiet) { Text("Back to schedules") }
        PosatoHeading(
            if (device == PosatoDevice.Mac) "Set up Posato on this Mac." else "Prepare this ${device.noun} for schedules.",
            description = "One setup to block distractions and run your schedules.",
            layout = layout,
        )
        macSetupContent?.invoke() ?: MacSetupOverview()
    }
}

@Preview(name = "Schedules", widthDp = 800, heightDp = 1200)
@Composable
private fun SchedulesScreenPreview(
    @PreviewParameter(SchedulesScreenPreviewDataProvider::class) case: SchedulesPreviewCase,
) {
    PosatoTheme {
        SchedulesScreen(case.state, PosatoDevice.IPhone, PosatoLayout.Compact, ScheduleDeviceReadiness(), ScheduleActions())
    }
}

@Preview(name = "Schedules Mac", widthDp = 1000, heightDp = 1200)
@Composable
private fun SchedulesScreenMacPreview(
    @PreviewParameter(SchedulesScreenPreviewDataProvider::class) case: SchedulesPreviewCase,
) {
    PosatoTheme {
        SchedulesScreen(
            case.state,
            PosatoDevice.Mac,
            PosatoLayout.Expanded,
            ScheduleDeviceReadiness(mac = MacScheduleReadiness.SETUP),
            ScheduleActions(),
        )
    }
}
