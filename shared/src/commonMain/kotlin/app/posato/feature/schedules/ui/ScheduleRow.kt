package app.posato.feature.schedules.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import app.posato.core.designsystem.collectIsKeyboardFocusedAsState
import app.posato.core.designsystem.keyboardFocusRing
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.navigation.PosatoNavStack
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.onboarding.MacSetupOverview
import app.posato.feature.schedules.domain.ScheduleId

/**
 * A schedule in the list: tapping the row edits it and the switch turns it on or off. On iOS its other actions
 * slide out from under a left swipe and deleting asks with the system alert; elsewhere they sit in the row's menu.
 */
@Composable
internal fun ScheduleRow(
    row: ScheduleRowModel,
    confirmingDelete: Boolean,
    actions: ScheduleActions,
) {
    Column(Modifier.fillMaxWidth()) {
        if (platformUsesCupertinoChrome) {
            PosatoSwipeRow(scheduleSwipeActions(row, actions)) { rowActions -> ScheduleRowContent(row, actions, rowActions) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScheduleRowContent(row, actions, emptyList(), Modifier.weight(1f))
                PosatoItemMenu("Actions for ${row.name}", scheduleMenu(row, actions))
            }
            if (confirmingDelete) {
                Column(Modifier.padding(bottom = PosatoSpace.Medium), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                    PosatoBody("Delete ${row.name}? It is deleted on your other devices too.")
                    PosatoActionRow {
                        PosatoButton(onClick = actions.onDelete, style = PosatoButtonStyle.Destructive) { Text("Delete") }
                        PosatoButton(onClick = { actions.onConfirmDelete(null) }, style = PosatoButtonStyle.Quiet) { Text("Keep") }
                    }
                }
            }
        }
        PosatoDivider()
    }
    if (confirmingDelete && platformUsesCupertinoChrome) ScheduleDeleteAlert(row, actions)
}

/** The system question before a schedule is deleted, asked from its row or from its editor. */
@Composable
internal fun ScheduleDeleteAlert(
    row: ScheduleRowModel,
    actions: ScheduleActions,
) {
    PosatoAlert(
        title = "Delete ${row.name}?",
        message = "It is deleted on your other devices too.",
        onDismiss = { actions.onConfirmDelete(null) },
        actions = listOf(
            PosatoAlertAction("Keep", { actions.onConfirmDelete(null) }, PosatoAlertRole.Cancel),
            PosatoAlertAction("Delete", { actions.onDelete() }, PosatoAlertRole.Destructive),
        ),
    )
}

@Composable
private fun ScheduleRowContent(
    row: ScheduleRowModel,
    actions: ScheduleActions,
    rowActions: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable(enabled = !row.refused, onClickLabel = "Edit ${row.name}") { actions.onEdit(row) }
            .semantics { if (rowActions.isNotEmpty()) customActions = rowActions }
            .padding(vertical = PosatoSpace.Medium),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge)
            PosatoCaption(listOfNotNull("${row.daysLabel} · ${row.hoursLabel}", row.setLabel).joinToString(" · "))
            ScheduleRowStatus(row)
        }
        val switchInteraction = remember { MutableInteractionSource() }
        val switchFocused by switchInteraction.collectIsKeyboardFocusedAsState()
        Box(
            Modifier.sizeIn(minWidth = PosatoSize.Control, minHeight = PosatoSize.Control).toggleable(
                value = row.enabled,
                enabled = !row.refused,
                role = Role.Switch,
                interactionSource = switchInteraction,
                indication = null,
            ) { actions.onSetEnabled(row, it) }
                .semantics { contentDescription = "${row.name} schedule" }
                .keyboardFocusRing({ switchFocused }, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            PosatoSwitch(checked = row.enabled, enabled = !row.refused)
        }
    }
}

@Composable
private fun ScheduleRowStatus(row: ScheduleRowModel) {
    row.setProblem?.let { problem -> PosatoNotice(tone = PosatoTone.Caution) { Text(problem) } }
    when {
        row.refused -> PosatoNotice(tone = PosatoTone.Caution) {
            Text("Couldn't sync: 10 schedules is the most. Delete a schedule on any device and this one syncs by itself.")
        }

        !row.enabled -> PosatoCaption("Turned off")

        else -> row.nextRunLabel?.let { PosatoCaption(it) }
    }
    row.skippedLabel?.let { PosatoCaption(it) }
}

private fun scheduleSwipeActions(
    row: ScheduleRowModel,
    actions: ScheduleActions,
): List<PosatoSwipeAction> {
    return buildList {
        if (row.enabled && row.canSkip) add(PosatoSwipeAction("Skip next", { actions.onSkipNext(row) }))
        add(PosatoSwipeAction("Delete", { actions.onConfirmDelete(row.id) }, destructive = true))
    }
}

private fun scheduleMenu(
    row: ScheduleRowModel,
    actions: ScheduleActions,
): List<PosatoMenuItem> {
    return buildList {
        if (!row.refused) add(PosatoMenuItem("Edit", { actions.onEdit(row) }, PosatoMenuSymbol.Edit))
        if (row.enabled && row.canSkip) add(PosatoMenuItem("Skip next", { actions.onSkipNext(row) }, PosatoMenuSymbol.Skip))
        add(PosatoMenuItem("Delete", { actions.onConfirmDelete(row.id) }, PosatoMenuSymbol.Remove, destructive = true))
    }
}
