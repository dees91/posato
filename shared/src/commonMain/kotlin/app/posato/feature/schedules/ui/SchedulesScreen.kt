package app.posato.feature.schedules.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoAlert
import app.posato.core.designsystem.PosatoAlertAction
import app.posato.core.designsystem.PosatoAlertRole
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarInset
import app.posato.core.designsystem.PosatoBarScreen
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoDivider
import app.posato.core.designsystem.PosatoEmptyState
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoItemList
import app.posato.core.designsystem.PosatoItemMenu
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoLead
import app.posato.core.designsystem.PosatoMenuItem
import app.posato.core.designsystem.PosatoMenuSymbol
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoSwipeAction
import app.posato.core.designsystem.PosatoSwipeRow
import app.posato.core.designsystem.PosatoSwitch
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.PosatoTypography
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.navigation.PosatoNavStack
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.onboarding.MacSetupOverview
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_add_schedule
import app.posato.generated.resources.action_allow_schedules_mac
import app.posato.generated.resources.action_allow_screen_time
import app.posato.generated.resources.action_back_to_schedules
import app.posato.generated.resources.action_set_up
import app.posato.generated.resources.action_set_up_this_mac
import app.posato.generated.resources.mac_unified_body
import app.posato.generated.resources.mac_unified_title
import app.posato.generated.resources.nav_schedules
import app.posato.generated.resources.schedule_editor_new
import app.posato.generated.resources.schedule_editor_title
import app.posato.generated.resources.schedule_error_full
import app.posato.generated.resources.schedules_clocks
import app.posato.generated.resources.schedules_empty_body
import app.posato.generated.resources.schedules_empty_title
import app.posato.generated.resources.schedules_eyebrow
import app.posato.generated.resources.schedules_mac_consent_body
import app.posato.generated.resources.schedules_mac_consent_title
import app.posato.generated.resources.schedules_mac_setup_body
import app.posato.generated.resources.schedules_mac_setup_title
import app.posato.generated.resources.schedules_notices_body
import app.posato.generated.resources.schedules_notices_title
import app.posato.generated.resources.schedules_rhythm
import app.posato.generated.resources.schedules_save_failed
import app.posato.generated.resources.schedules_screen_time_later
import app.posato.generated.resources.schedules_sync_note
import org.jetbrains.compose.resources.stringResource

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
    val backEnabled = !(top == SchedulesRoute.Setup && setupPromptOpen) && !(top is SchedulesRoute.Editor && state.saving)
    PosatoNavStack(
        listOfNotNull(SchedulesRoute.List, top),
        onBack = { if (top is SchedulesRoute.Editor) actions.onCloseEditor() else actions.onShowSetup(false) },
        modifier = modifier.fillMaxSize(),
        backEnabled = backEnabled,
    ) { route ->
        val routeContent: @Composable () -> Unit = {
            when (route) {
                is SchedulesRoute.Editor -> rememberLastPresent(editor)?.let { ScheduleEditor(it, state, layout, actions) }
                SchedulesRoute.Setup -> ScheduleSetup(device, layout, actions, macSetupContent)
                SchedulesRoute.List -> ScheduleList(state, device, layout, readiness, actions)
            }
        }
        if (platformUsesCupertinoChrome) {
            SchedulesBarScreen(
                route,
                PosatoBarInset,
                actions,
                addEnabled = !state.atCapacity,
                state.addsFromBar(readiness, device),
                backEnabled,
                routeContent,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset),
                verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
            ) { routeContent() }
        }
    }
}

@Composable
private fun SchedulesBarScreen(
    route: SchedulesRoute,
    inset: Dp,
    actions: ScheduleActions,
    addEnabled: Boolean,
    addsFromBar: Boolean,
    backEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val focus = LocalFocusManager.current
    val padding = PaddingValues(horizontal = inset)
    val latestContent by rememberUpdatedState(content)
    val body = remember { movableContentOf { latestContent() } }
    when (route) {
        SchedulesRoute.List -> PosatoBarScreen(
            title = stringResource(Res.string.nav_schedules),
            largeTitle = true,
            contentPadding = padding,
            trailingContent = {
                if (addsFromBar) {
                    PosatoBarButton(
                        onClick = actions.onAdd,
                        enabled = addEnabled,
                        controlId = "action_add_schedule",
                    ) { Text(stringResource(Res.string.action_add_schedule), style = PosatoTypography.BarAction) }
                }
            },
        ) { body() }

        is SchedulesRoute.Editor -> PosatoBarScreen(
            title = if (route.id == null) stringResource(Res.string.schedule_editor_new) else stringResource(Res.string.schedule_editor_title),
            backLabel = stringResource(Res.string.nav_schedules),
            backEnabled = backEnabled,
            onBack = {
                focus.clearFocus()
                actions.onCloseEditor()
            },
            contentPadding = padding,
        ) { body() }

        SchedulesRoute.Setup -> PosatoBarScreen(
            title = stringResource(Res.string.action_set_up),
            backLabel = stringResource(Res.string.nav_schedules),
            backEnabled = backEnabled,
            onBack = { actions.onShowSetup(false) },
            contentPadding = padding,
        ) { body() }
    }
}

/** Whether this device can take a new schedule now; a Mac first needs its setup. */
private fun ScheduleDeviceReadiness.canAddSchedules(device: PosatoDevice): Boolean {
    return device != PosatoDevice.Mac || mac == MacScheduleReadiness.READY
}

/** Whether the iOS bar offers Add schedule: once a schedule exists, the list's own button gives way to it. */
private fun SchedulesUiState.addsFromBar(
    readiness: ScheduleDeviceReadiness,
    device: PosatoDevice,
): Boolean {
    return schedules.isNotEmpty() && readiness.canAddSchedules(device)
}

@Composable
private fun ScheduleAddAction(
    state: SchedulesUiState,
    device: PosatoDevice,
    readiness: ScheduleDeviceReadiness,
    actions: ScheduleActions,
) {
    if (!readiness.canAddSchedules(device)) return
    // On iOS the bar carries Add schedule; the list keeps the button only as the empty state's first step.
    if (!platformUsesCupertinoChrome || state.schedules.isEmpty()) {
        PosatoButton(
            onClick = actions.onAdd,
            enabled = !state.atCapacity,
            controlId = "action_add_schedule",
        ) { Text(stringResource(Res.string.action_add_schedule)) }
    }
    if (state.atCapacity) {
        PosatoCaption(stringResource(Res.string.schedule_error_full))
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
    if (platformUsesCupertinoChrome) {
        PosatoLead(stringResource(Res.string.schedules_rhythm))
    } else {
        PosatoHeading(stringResource(Res.string.schedules_rhythm), eyebrow = stringResource(Res.string.schedules_eyebrow), layout = layout)
    }
    if (state.showUpdateNote) {
        PosatoNotice { Text(stringResource(Res.string.schedules_sync_note)) }
    }
    ScheduleReadinessCard(device, readiness, actions)
    if (state.changeFailed) {
        PosatoNotice(tone = PosatoTone.Caution) { Text(stringResource(Res.string.schedules_save_failed)) }
    }
    if (state.loaded && state.schedules.isEmpty()) {
        PosatoEmptyState(
            title = stringResource(Res.string.schedules_empty_title),
            description = stringResource(Res.string.schedules_empty_body),
        )
    }
    ScheduleAddAction(state, device, readiness, actions)
    if (state.schedules.isNotEmpty()) {
        PosatoItemList {
            state.schedules.forEach { row -> key(row.id.hex) { ScheduleRow(row, state.confirmingDelete == row.id && state.editor == null, actions) } }
        }
    }
    if (readiness.offerNotices && state.schedules.isNotEmpty()) {
        PosatoButton(
            onClick = actions.onTurnOnNotices,
            style = PosatoButtonStyle.Quiet,
            controlId = "schedules_notices_title",
        ) { Text(stringResource(Res.string.schedules_notices_title)) }
        PosatoCaption(stringResource(Res.string.schedules_notices_body))
    }
    if (state.schedules.isNotEmpty()) {
        PosatoCaption(stringResource(Res.string.schedules_clocks))
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
            PosatoBody(stringResource(Res.string.schedules_mac_setup_title))
            PosatoCaption(stringResource(Res.string.schedules_mac_setup_body))
            PosatoButton(
                onClick = { actions.onShowSetup(true) },
                controlId = "action_set_up_this_mac",
            ) { Text(stringResource(Res.string.action_set_up_this_mac)) }
        }
    } else if (device == PosatoDevice.Mac && readiness.mac == MacScheduleReadiness.CONSENT) {
        PosatoPanel(modifier = Modifier.fillMaxWidth()) {
            PosatoBody(stringResource(Res.string.schedules_mac_consent_title))
            PosatoCaption(stringResource(Res.string.schedules_mac_consent_body))
            PosatoButton(
                onClick = actions.onAllowSchedules,
                controlId = "action_allow_schedules_mac",
            ) { Text(stringResource(Res.string.action_allow_schedules_mac)) }
        }
    } else if (device != PosatoDevice.Mac && !readiness.screenTimeAllowed) {
        PosatoPanel(modifier = Modifier.fillMaxWidth()) {
            PosatoBody(schedulesAllowScreenTime(device))
            PosatoCaption(stringResource(Res.string.schedules_screen_time_later))
            PosatoButton(
                onClick = actions.onAllowScreenTime,
                controlId = "action_allow_screen_time",
            ) { Text(stringResource(Res.string.action_allow_screen_time)) }
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
        PosatoButton(
            onClick = { actions.onShowSetup(false) },
            style = PosatoButtonStyle.Quiet,
            controlId = "action_back_to_schedules",
        ) { Text(stringResource(Res.string.action_back_to_schedules)) }
        PosatoHeading(
            if (device == PosatoDevice.Mac) stringResource(Res.string.mac_unified_title) else schedulesPrepare(device),
            description = stringResource(Res.string.mac_unified_body),
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
