package app.posato.feature.schedules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoChoiceGroup
import app.posato.core.designsystem.PosatoDayRow
import app.posato.core.designsystem.PosatoDayToggle
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoDivider
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoLead
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoNumberWheel
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoSwitchRow
import app.posato.core.designsystem.PosatoTextButton
import app.posato.core.designsystem.PosatoTextField
import app.posato.core.designsystem.PosatoTimeRow
import app.posato.core.designsystem.PosatoToggleButton
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.feature.targets.ui.PauseSetPickerRow
import app.posato.feature.targets.ui.PauseSetRow
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_back_to_schedules
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_delete_schedule
import app.posato.generated.resources.action_save_schedule
import app.posato.generated.resources.action_skip_next
import app.posato.generated.resources.schedule_choose_set
import app.posato.generated.resources.schedule_editor_eyebrow
import app.posato.generated.resources.schedule_editor_lead
import app.posato.generated.resources.schedule_editor_title
import app.posato.generated.resources.schedule_ends
import app.posato.generated.resources.schedule_ends_at
import app.posato.generated.resources.schedule_ends_at_next
import app.posato.generated.resources.schedule_error_day
import app.posato.generated.resources.schedule_error_full
import app.posato.generated.resources.schedule_error_name
import app.posato.generated.resources.schedule_error_same
import app.posato.generated.resources.schedule_error_save
import app.posato.generated.resources.schedule_error_short
import app.posato.generated.resources.schedule_name_label
import app.posato.generated.resources.schedule_next_day
import app.posato.generated.resources.schedule_off_keeps
import app.posato.generated.resources.schedule_on
import app.posato.generated.resources.schedule_repeat_on
import app.posato.generated.resources.schedule_starts
import app.posato.generated.resources.schedule_starts_at
import app.posato.generated.resources.schedule_time_end
import app.posato.generated.resources.schedule_time_start
import app.posato.generated.resources.sync_folder_code_done
import app.posato.generated.resources.wheel_hours_of
import app.posato.generated.resources.wheel_minutes_of
import kotlinx.collections.immutable.toPersistentSet
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScheduleEditor(
    draft: ScheduleDraft,
    state: SchedulesUiState,
    layout: PosatoLayout,
    actions: ScheduleActions,
    modifier: Modifier = Modifier,
) {
    val name = rememberTextFieldState(draft.name)
    val current by rememberUpdatedState(draft)
    LaunchedEffect(name) {
        snapshotFlow { name.text.toString() }.collect { text -> if (text != current.name) actions.onUpdateDraft(current.copy(name = text)) }
    }
    val focus = LocalFocusManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        if (platformUsesCupertinoChrome) {
            if (draft.id == null) PosatoLead(stringResource(Res.string.schedule_editor_lead))
        } else {
            PosatoButton(onClick = {
                focus.clearFocus()
                actions.onCloseEditor()
            }, style = PosatoButtonStyle.Quiet, controlId = "action_back_to_schedules") { Text(stringResource(Res.string.action_back_to_schedules)) }
            PosatoHeading(
                if (draft.id ==
                    null
                ) {
                    stringResource(Res.string.schedule_editor_lead)
                } else {
                    stringResource(Res.string.schedule_editor_title)
                },
                eyebrow = stringResource(Res.string.schedule_editor_eyebrow),
                layout = layout,
            )
        }
        PosatoTextField(state = name, label = stringResource(Res.string.schedule_name_label), onSubmit = { focus.clearFocus() })
        ScheduleSetRow(draft, state.pauseSets, actions.onUpdateDraft)
        ScheduleDays(draft, actions)
        ScheduleTimes(draft, actions)
        PosatoSwitchRow(
            modifier = Modifier.fillMaxWidth(),
            checked = draft.enabled,
            onCheckedChange = { actions.onUpdateDraft(draft.copy(enabled = it)) },
            supportingContent = { PosatoCaption(stringResource(Res.string.schedule_off_keeps)) },
        ) { Text(stringResource(Res.string.schedule_on)) }
        state.editorError?.let { error -> PosatoNotice(tone = PosatoTone.Caution) { Text(error.message()) } }
        PosatoActionRow {
            PosatoButton(onClick = {
                focus.clearFocus()
                actions.onSave()
            }, enabled = !state.saving, controlId = "action_save_schedule") { Text(stringResource(Res.string.action_save_schedule)) }
            PosatoButton(
                onClick = actions.onCloseEditor,
                style = PosatoButtonStyle.Quiet,
                controlId = "action_cancel",
            ) { Text(stringResource(Res.string.action_cancel)) }
        }
        if (platformUsesCupertinoChrome) ScheduleEditorRowActions(draft, state, actions)
    }
}

/**
 * On iOS a schedule's row hides Skip next and Delete under a swipe, so its editor offers them in sight at its foot,
 * as the Clock app does for an alarm. Deleting asks with the system alert and closes the editor.
 */
@Composable
private fun ScheduleEditorRowActions(
    draft: ScheduleDraft,
    state: SchedulesUiState,
    actions: ScheduleActions,
) {
    val row = draft.id?.let { id -> state.schedules.firstOrNull { it.id == id } } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoDivider()
        if (row.enabled && row.canSkip) {
            PosatoTextButton(
                onClick = { actions.onSkipNext(row) },
                controlId = "action_skip_next",
            ) { Text(stringResource(Res.string.action_skip_next)) }
            row.nextRunLabel?.let { PosatoCaption(it) }
        }
        row.skippedLabel?.let { PosatoCaption(it) }
        PosatoTextButton(
            onClick = { actions.onConfirmDelete(row.id) },
            destructive = true,
            controlId = "action_delete_schedule",
        ) { Text(stringResource(Res.string.action_delete_schedule)) }
    }
    if (state.confirmingDelete == row.id) ScheduleDeleteAlert(row, actions)
}

/** The schedule's hours: the system's compact time pickers on iOS, buttons that open the drawn wheels elsewhere. */
@Composable
private fun ScheduleTimes(
    draft: ScheduleDraft,
    actions: ScheduleActions,
) {
    val focus = LocalFocusManager.current
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    if (platformUsesCupertinoChrome) {
        Column {
            PosatoTimeRow(stringResource(Res.string.schedule_starts), draft.startHour, draft.startMinute, { hour, minute ->
                actions.onUpdateDraft(draft.copy(startHour = hour, startMinute = minute))
            })
            PosatoTimeRow(stringResource(Res.string.schedule_ends), draft.endHour, draft.endMinute, { hour, minute ->
                actions.onUpdateDraft(draft.copy(endHour = hour, endMinute = minute))
            }, supportingText = stringResource(Res.string.schedule_next_day).takeIf { draft.endsNextDay })
        }
    } else {
        PosatoActionRow {
            PosatoButton(onClick = {
                focus.clearFocus()
                editingStart = true
            }, style = PosatoButtonStyle.Secondary, controlId = "schedule_starts_at") {
                Text(stringResource(Res.string.schedule_starts_at, timeLabel(draft.startHour, draft.startMinute)))
            }
            PosatoButton(onClick = {
                focus.clearFocus()
                editingStart = false
            }, style = PosatoButtonStyle.Secondary, controlId = "schedule_ends_at") {
                Text(
                    if (draft.endsNextDay) {
                        stringResource(Res.string.schedule_ends_at_next, timeLabel(draft.endHour, draft.endMinute))
                    } else {
                        stringResource(Res.string.schedule_ends_at, timeLabel(draft.endHour, draft.endMinute))
                    },
                )
            }
        }
        editingStart?.let { start -> ScheduleTimeEditor(draft, start, actions.onUpdateDraft) { editingStart = null } }
    }
}

@Composable
private fun ScheduleDays(
    draft: ScheduleDraft,
    actions: ScheduleActions,
) {
    val toggle = { day: ScheduleDay ->
        val days = if (day in draft.days) draft.days - day else draft.days + day
        actions.onUpdateDraft(draft.copy(days = days.toPersistentSet()))
    }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoCaption(stringResource(Res.string.schedule_repeat_on))
        if (platformUsesCupertinoChrome) {
            PosatoDayRow {
                ScheduleDay.entries.forEach { day -> PosatoDayToggle(day.fullLabel(), selected = day in draft.days, onClick = { toggle(day) }) }
            }
        } else {
            PosatoChoiceGroup {
                ScheduleDay.entries.forEach { day ->
                    PosatoToggleButton(selected = day in draft.days, onClick = { toggle(day) }) { Text(day.fullLabel()) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditorError.message(): String {
    return when (this) {
        ScheduleEditorError.NAME -> stringResource(Res.string.schedule_error_name)
        ScheduleEditorError.NO_DAY -> stringResource(Res.string.schedule_error_day)
        ScheduleEditorError.TOO_SHORT -> stringResource(Res.string.schedule_error_short)
        ScheduleEditorError.SAME_TIMES -> stringResource(Res.string.schedule_error_same)
        ScheduleEditorError.FULL -> stringResource(Res.string.schedule_error_full)
        ScheduleEditorError.NOT_SAVED -> stringResource(Res.string.schedule_error_save)
    }
}

@Composable
private fun ScheduleTimeEditor(
    draft: ScheduleDraft,
    start: Boolean,
    onChange: (ScheduleDraft) -> Unit,
    onDone: () -> Unit,
) {
    val label = if (start) stringResource(Res.string.schedule_time_start) else stringResource(Res.string.schedule_time_end)
    PosatoPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = if (start) draft.startHour else draft.endHour,
                range = 0..23,
                label = stringResource(Res.string.wheel_hours_of, label),
                onValueChange = { onChange(if (start) draft.copy(startHour = it) else draft.copy(endHour = it)) },
            )
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = if (start) draft.startMinute else draft.endMinute,
                range = 0..59,
                label = stringResource(Res.string.wheel_minutes_of, label),
                onValueChange = { onChange(if (start) draft.copy(startMinute = it) else draft.copy(endMinute = it)) },
            )
        }
        PosatoButton(
            onClick = onDone,
            style = PosatoButtonStyle.Secondary,
            controlId = "sync_folder_code_done",
        ) { Text(stringResource(Res.string.sync_folder_code_done)) }
    }
}

/** The schedule's set below its name: tapping it opens the set choice with the current set marked. */
@Composable
private fun ScheduleSetRow(
    draft: ScheduleDraft,
    sets: List<PauseSetRow>,
    onUpdateDraft: (ScheduleDraft) -> Unit,
) {
    PauseSetPickerRow(sets, draft.setId, onChoose = { id ->
        onUpdateDraft(draft.copy(setId = id))
    }, placeholder = stringResource(Res.string.schedule_choose_set))
}
