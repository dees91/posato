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
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoLead
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoNumberWheel
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoSwitchRow
import app.posato.core.designsystem.PosatoTextField
import app.posato.core.designsystem.PosatoToggleButton
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.feature.targets.ui.PauseSetPickerRow
import app.posato.feature.targets.ui.PauseSetRow
import kotlinx.collections.immutable.toPersistentSet

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
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    val focus = LocalFocusManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        if (platformUsesCupertinoChrome) {
            if (draft.id == null) PosatoLead("Make room, regularly.")
        } else {
            PosatoButton(onClick = {
                focus.clearFocus()
                actions.onCloseEditor()
            }, style = PosatoButtonStyle.Quiet) { Text("Back to schedules") }
            PosatoHeading(if (draft.id == null) "Make room, regularly." else "Edit schedule", eyebrow = "SCHEDULE", layout = layout)
        }
        PosatoTextField(state = name, label = "Schedule name", onSubmit = { focus.clearFocus() })
        ScheduleSetRow(draft, state.pauseSets, actions.onUpdateDraft)
        ScheduleDays(draft, actions)
        PosatoActionRow {
            PosatoButton(onClick = {
                focus.clearFocus()
                editingStart = true
            }, style = PosatoButtonStyle.Secondary) { Text("Starts ${timeLabel(draft.startHour, draft.startMinute)}") }
            PosatoButton(onClick = {
                focus.clearFocus()
                editingStart = false
            }, style = PosatoButtonStyle.Secondary) {
                Text("Ends ${timeLabel(draft.endHour, draft.endMinute)}" + if (draft.endsNextDay) " next day" else "")
            }
        }
        editingStart?.let { start -> ScheduleTimeEditor(draft, start, actions.onUpdateDraft) { editingStart = null } }
        PosatoSwitchRow(
            modifier = Modifier.fillMaxWidth(),
            checked = draft.enabled,
            onCheckedChange = { actions.onUpdateDraft(draft.copy(enabled = it)) },
            supportingContent = { PosatoCaption("A schedule that is off keeps its days and hours but never starts.") },
        ) { Text("Schedule on") }
        state.editorError?.let { error -> PosatoNotice(tone = PosatoTone.Caution) { Text(error.message()) } }
        PosatoActionRow {
            PosatoButton(onClick = {
                focus.clearFocus()
                actions.onSave()
            }, enabled = !state.saving) { Text("Save schedule") }
            PosatoButton(onClick = actions.onCloseEditor, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
        }
    }
}

@Composable
private fun ScheduleDays(
    draft: ScheduleDraft,
    actions: ScheduleActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoCaption("Repeat on")
        PosatoChoiceGroup {
            ScheduleDay.entries.forEach { day ->
                PosatoToggleButton(
                    selected = day in draft.days,
                    onClick = {
                        val days = if (day in draft.days) draft.days - day else draft.days + day
                        actions.onUpdateDraft(draft.copy(days = days.toPersistentSet()))
                    },
                ) { Text(day.label) }
            }
        }
    }
}

private fun ScheduleEditorError.message(): String {
    return when (this) {
        ScheduleEditorError.NAME -> "Give the schedule a name of up to 80 characters, without line breaks."
        ScheduleEditorError.NO_DAY -> "Choose at least one day."
        ScheduleEditorError.TOO_SHORT -> "A schedule lasts at least 15 minutes."
        ScheduleEditorError.SAME_TIMES -> "Choose an end time different from the start."
        ScheduleEditorError.FULL -> "You have 10 schedules, the most Posato keeps. Delete one to add another."
        ScheduleEditorError.NOT_SAVED -> "Couldn't save. Try again."
    }
}

@Composable
private fun ScheduleTimeEditor(
    draft: ScheduleDraft,
    start: Boolean,
    onChange: (ScheduleDraft) -> Unit,
    onDone: () -> Unit,
) {
    val label = if (start) "Start" else "End"
    PosatoPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = if (start) draft.startHour else draft.endHour,
                range = 0..23,
                label = "$label hours",
                onValueChange = { onChange(if (start) draft.copy(startHour = it) else draft.copy(endHour = it)) },
            )
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = if (start) draft.startMinute else draft.endMinute,
                range = 0..59,
                label = "$label minutes",
                onValueChange = { onChange(if (start) draft.copy(startMinute = it) else draft.copy(endMinute = it)) },
            )
        }
        PosatoButton(onClick = onDone, style = PosatoButtonStyle.Secondary) { Text("Done") }
    }
}

/** The schedule's set below its name: tapping it opens the set choice with the current set marked. */
@Composable
private fun ScheduleSetRow(
    draft: ScheduleDraft,
    sets: List<PauseSetRow>,
    onUpdateDraft: (ScheduleDraft) -> Unit,
) {
    PauseSetPickerRow(sets, draft.setId, onChoose = { id -> onUpdateDraft(draft.copy(setId = id)) }, placeholder = "Choose a set")
}
