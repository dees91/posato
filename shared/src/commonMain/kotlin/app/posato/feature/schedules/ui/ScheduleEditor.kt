package app.posato.feature.schedules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNumberWheel
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSelectionRow
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTextField
import app.posato.core.designsystem.PosatoToggleButton
import kotlinx.collections.immutable.toPersistentList

@Composable
internal fun ScheduleEditor(
    schedule: ScheduleUiModel,
    layout: PosatoLayout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = rememberTextFieldState(schedule.name)
    var draft by remember(schedule) { mutableStateOf(schedule) }
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    val focus = LocalFocusManager.current
    val close = {
        focus.clearFocus()
        onBack()
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoButton(onClick = close, style = PosatoButtonStyle.Quiet) { Text("Back to schedules") }
        PosatoHeading(if (schedule.id == 0) "Make room, regularly." else "Edit schedule", layout = layout)
        PosatoTextField(state = name, label = "Schedule name", placeholder = "Morning focus", onSubmit = { focus.clearFocus() })
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            PosatoCaption("Repeat on")
            PosatoActionRow {
                ScheduleDay.entries.forEach { day ->
                    PosatoToggleButton(
                        selected = day in draft.days,
                        onClick = {
                            val days = if (day in draft.days) draft.days - day else draft.days + day
                            draft = draft.copy(days = days.sortedBy { it.ordinal }.toPersistentList())
                        },
                    ) { Text(day.label) }
                }
            }
        }
        PosatoActionRow {
            PosatoButton(onClick = {
                focus.clearFocus()
                editingStart = true
            }, style = PosatoButtonStyle.Secondary) {
                Text("Starts ${timeLabel(draft.startHour, draft.startMinute)}")
            }
            PosatoButton(onClick = {
                focus.clearFocus()
                editingStart = false
            }, style = PosatoButtonStyle.Secondary) {
                Text("Ends ${timeLabel(draft.endHour, draft.endMinute)}")
            }
        }
        editingStart?.let { start ->
            ScheduleTimeEditor(draft, start, { draft = it }, { editingStart = null })
        }
        PosatoSelectionRow(
            modifier = Modifier.fillMaxWidth(),
            checked = draft.enabled,
            onCheckedChange = {},
            enabled = false,
            supportingContent = { PosatoCaption("Automatic starts will need permission on each device.") },
        ) { Text("Schedule enabled") }
        PosatoCaption("Saving schedules will be available in a future update. Your changes are not saved yet.")
        PosatoActionRow {
            PosatoButton(onClick = {}, enabled = false) { Text("Save schedule") }
            PosatoButton(onClick = close, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
        }
    }
}

@Composable
private fun ScheduleTimeEditor(
    schedule: ScheduleUiModel,
    start: Boolean,
    onChange: (ScheduleUiModel) -> Unit,
    onDone: () -> Unit,
) {
    val label = if (start) "Start" else "End"
    PosatoPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = if (start) schedule.startHour else schedule.endHour,
                range = 0..23,
                label = "$label hours",
                onValueChange = { onChange(if (start) schedule.copy(startHour = it) else schedule.copy(endHour = it)) },
            )
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = if (start) schedule.startMinute else schedule.endMinute,
                range = 0..59,
                label = "$label minutes",
                onValueChange = { onChange(if (start) schedule.copy(startMinute = it) else schedule.copy(endMinute = it)) },
            )
        }
        PosatoButton(onClick = onDone, style = PosatoButtonStyle.Secondary) { Text("Done") }
    }
}
