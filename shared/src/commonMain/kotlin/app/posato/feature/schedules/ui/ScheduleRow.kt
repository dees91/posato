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
import app.posato.core.designsystem.stringResourceForDevice
import app.posato.core.navigation.PosatoNavStack
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.onboarding.MacSetupOverview
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_delete
import app.posato.generated.resources.action_edit
import app.posato.generated.resources.action_keep
import app.posato.generated.resources.action_skip_next
import app.posato.generated.resources.pause_sets_delete_named
import app.posato.generated.resources.schedule_actions_for
import app.posato.generated.resources.schedule_days_hours
import app.posato.generated.resources.schedule_delete_named_body
import app.posato.generated.resources.schedule_delete_other_devices
import app.posato.generated.resources.schedule_edit_named
import app.posato.generated.resources.schedule_ends_next_day
import app.posato.generated.resources.schedule_named
import app.posato.generated.resources.schedule_next
import app.posato.generated.resources.schedule_nothing_computer
import app.posato.generated.resources.schedule_nothing_ipad
import app.posato.generated.resources.schedule_nothing_iphone
import app.posato.generated.resources.schedule_nothing_mac
import app.posato.generated.resources.schedule_nothing_phone
import app.posato.generated.resources.schedule_set_deleted
import app.posato.generated.resources.schedule_set_label
import app.posato.generated.resources.schedule_set_over_limit
import app.posato.generated.resources.schedule_skipped
import app.posato.generated.resources.schedule_sync_limit
import app.posato.generated.resources.schedule_turned_off
import app.posato.generated.resources.schedule_waiting_set
import org.jetbrains.compose.resources.stringResource

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
                PosatoItemMenu(stringResource(Res.string.schedule_actions_for, row.name), scheduleMenu(row, actions))
            }
            if (confirmingDelete) {
                Column(Modifier.padding(bottom = PosatoSpace.Medium), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                    PosatoBody(stringResource(Res.string.schedule_delete_named_body, row.name))
                    PosatoActionRow {
                        PosatoButton(
                            onClick = actions.onDelete,
                            style = PosatoButtonStyle.Destructive,
                            controlId = "action_delete",
                        ) { Text(stringResource(Res.string.action_delete)) }
                        PosatoButton(
                            onClick = { actions.onConfirmDelete(null) },
                            style = PosatoButtonStyle.Quiet,
                            controlId = "action_keep",
                        ) { Text(stringResource(Res.string.action_keep)) }
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
        title = stringResource(Res.string.pause_sets_delete_named, row.name),
        message = stringResource(Res.string.schedule_delete_other_devices),
        onDismiss = { actions.onConfirmDelete(null) },
        actions = listOf(
            PosatoAlertAction(
                stringResource(Res.string.action_keep),
                { actions.onConfirmDelete(null) },
                PosatoAlertRole.Cancel,
                controlId = "action_keep",
            ),
            PosatoAlertAction(
                stringResource(Res.string.action_delete),
                { actions.onDelete() },
                PosatoAlertRole.Destructive,
                controlId = "action_delete",
            ),
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
        modifier.fillMaxWidth().clickable(enabled = !row.refused, onClickLabel = stringResource(Res.string.schedule_edit_named, row.name)) {
            actions.onEdit(row)
        }
            .semantics { if (rowActions.isNotEmpty()) customActions = rowActions }
            .padding(vertical = PosatoSpace.Medium),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge)
            PosatoCaption(row.caption())
            ScheduleRowStatus(row)
        }
        val switchInteraction = remember { MutableInteractionSource() }
        val switchFocused by switchInteraction.collectIsKeyboardFocusedAsState()
        val scheduleName = stringResource(Res.string.schedule_named, row.name)
        Box(
            Modifier.sizeIn(minWidth = PosatoSize.Control, minHeight = PosatoSize.Control).toggleable(
                value = row.enabled,
                enabled = !row.refused,
                role = Role.Switch,
                interactionSource = switchInteraction,
                indication = null,
            ) { actions.onSetEnabled(row, it) }
                .semantics { contentDescription = scheduleName }
                .keyboardFocusRing({ switchFocused }, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            PosatoSwitch(checked = row.enabled, enabled = !row.refused)
        }
    }
}

@Composable
private fun ScheduleRowStatus(row: ScheduleRowModel) {
    row.problem()?.let { problem -> PosatoNotice(tone = PosatoTone.Caution) { Text(problem) } }
    when {
        row.refused -> PosatoNotice(tone = PosatoTone.Caution) {
            Text(stringResource(Res.string.schedule_sync_limit))
        }

        !row.enabled -> PosatoCaption(stringResource(Res.string.schedule_turned_off))

        else -> row.nextLabel()?.let { PosatoCaption(it) }
    }
    row.skippedText()?.let { PosatoCaption(it) }
}

@Composable
private fun ScheduleRowModel.caption(): String {
    val shortDays = listOf(
        shortWeekday(0),
        shortWeekday(1),
        shortWeekday(2),
        shortWeekday(3),
        shortWeekday(4),
        shortWeekday(5),
        shortWeekday(6),
    )
    val endsNext = stringResource(Res.string.schedule_ends_next_day)
    val namedSet = stringResource(Res.string.schedule_set_label, setName.orEmpty())
    val start = startMinuteOfDay
    val end = endMinuteOfDay
    val days = ScheduleDay.entries.filter { weekdayMask and it.bit != 0 }.joinToString(", ") { day -> shortDays[day.ordinal] }
    val hours = if (start == null || end == null) "" else "${minuteLabel(start)} - ${minuteLabel(end)}" + if (rowEndsNextDay) endsNext else ""
    val daysHours = stringResource(Res.string.schedule_days_hours, days, hours)
    if (weekdayMask < 0 || start == null || end == null) {
        return listOfNotNull("$daysLabel · $hoursLabel", setLabel).joinToString(" · ")
    }
    val set = if (setName == null) setLabel else namedSet
    return listOfNotNull(daysHours, set).joinToString(" · ")
}

@Composable
private fun ScheduleRowModel.problem(): String? {
    val waiting = stringResource(Res.string.schedule_waiting_set)
    val deleted = stringResource(Res.string.schedule_set_deleted)
    val overLimit = stringResource(Res.string.schedule_set_over_limit)
    val nothing = stringResourceForDevice(
        Res.string.schedule_nothing_mac,
        Res.string.schedule_nothing_iphone,
        Res.string.schedule_nothing_ipad,
        Res.string.schedule_nothing_computer,
        Res.string.schedule_nothing_phone,
    )
    return when (problemKind) {
        null -> setProblem
        ScheduleProblemKind.WAITING -> waiting
        ScheduleProblemKind.DELETED -> deleted
        ScheduleProblemKind.OVER_LIMIT -> overLimit
        ScheduleProblemKind.NOTHING -> nothing
    }
}

@Composable
internal fun ScheduleRowModel.nextLabel(): String? {
    val formatted = stringResource(Res.string.schedule_next, shortWeekday(nextWeekday ?: 0), nextWhen.orEmpty())
    return if (nextWeekday == null || nextWhen == null) nextRunLabel else formatted
}

@Composable
internal fun ScheduleRowModel.skippedText(): String? {
    val formatted = stringResource(Res.string.schedule_skipped, shortWeekday(skippedWeekday ?: 0), skippedWhen.orEmpty())
    return if (skippedWeekday == null || skippedWhen == null) skippedLabel else formatted
}

@Composable
private fun scheduleSwipeActions(
    row: ScheduleRowModel,
    actions: ScheduleActions,
): List<PosatoSwipeAction> {
    return buildList {
        if (row.enabled && row.canSkip) {
            add(PosatoSwipeAction(stringResource(Res.string.action_skip_next), { actions.onSkipNext(row) }, controlId = "action_skip_next"))
        }
        add(
            PosatoSwipeAction(stringResource(Res.string.action_delete), {
                actions.onConfirmDelete(row.id)
            }, destructive = true, controlId = "action_delete"),
        )
    }
}

@Composable
private fun scheduleMenu(
    row: ScheduleRowModel,
    actions: ScheduleActions,
): List<PosatoMenuItem> {
    return buildList {
        if (!row.refused) {
            add(
                PosatoMenuItem(stringResource(Res.string.action_edit), {
                    actions.onEdit(row)
                }, PosatoMenuSymbol.Edit, controlId = "action_edit"),
            )
        }
        if (row.enabled && row.canSkip) {
            add(
                PosatoMenuItem(stringResource(Res.string.action_skip_next), {
                    actions.onSkipNext(row)
                }, PosatoMenuSymbol.Skip, controlId = "action_skip_next"),
            )
        }
        add(
            PosatoMenuItem(
                stringResource(Res.string.action_delete),
                { actions.onConfirmDelete(row.id) },
                PosatoMenuSymbol.Remove,
                destructive = true,
                controlId = "action_delete",
            ),
        )
    }
}
