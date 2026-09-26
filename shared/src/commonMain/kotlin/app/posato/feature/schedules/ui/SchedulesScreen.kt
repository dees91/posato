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
import app.posato.feature.onboarding.MacScheduleRequirements

@Composable
internal fun SchedulesScreen(
    holder: SchedulesNavigationState,
    device: PosatoDevice,
    layout: PosatoLayout,
    modifier: Modifier = Modifier,
) {
    SchedulesScreen(
        modifier = modifier,
        state = holder.state,
        device = device,
        layout = layout,
        onEdit = holder::edit,
        onCloseEditor = holder::closeEditor,
        onShowSetup = holder::showSetup,
    )
}

@Composable
internal fun SchedulesScreen(
    state: SchedulesUiState,
    device: PosatoDevice,
    layout: PosatoLayout,
    onEdit: (ScheduleUiModel) -> Unit,
    onCloseEditor: () -> Unit,
    onShowSetup: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inset = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
    ) {
        PosatoNotice { Text("Schedules are coming soon. You can explore the screens; saving and automatic starts are not available yet.") }
        val editor = state.editor
        val activeSchedule = state.activeSchedule
        when {
            editor != null -> {
                key(editor.id) { ScheduleEditor(editor, layout, onCloseEditor) }
            }

            state.showingSetup -> {
                ScheduleSetupPreview(device, layout, onBack = { onShowSetup(false) }, onPreviewEditor = { onEdit(ScheduleUiModel()) })
            }

            activeSchedule != null -> {
                ScheduledSessionPreview(activeSchedule, layout, onCloseEditor)
            }

            else -> {
                PosatoHeading("A rhythm that gives you room.", eyebrow = "SCHEDULES", layout = layout)
                if (state.schedules.isEmpty()) {
                    PosatoEmptyState(
                        title = "Make time for a regular pause.",
                        description = "Choose the days and hours that work for you. Start with one schedule, then add another when you need it.",
                    )
                }
                if (device == PosatoDevice.Mac) {
                    PosatoPanel(modifier = Modifier.fillMaxWidth()) {
                        PosatoBody("Prepare this Mac before your first schedule.")
                        PosatoCaption("Background helper, Open Posato at login and Start sessions without the password are all required.")
                        PosatoButton(onClick = { onShowSetup(true) }) { Text("Set up schedules") }
                    }
                } else {
                    PosatoButton(onClick = { onEdit(ScheduleUiModel()) }) { Text("Add schedule") }
                }
                state.schedules.forEach { schedule ->
                    key(schedule.id) { ScheduleRow(schedule, device, onEdit) { onShowSetup(true) } }
                }
                if (device != PosatoDevice.Mac) {
                    PosatoButton(onClick = { onShowSetup(true) }, style = PosatoButtonStyle.Quiet) { Text("Set up this ${device.noun}") }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: ScheduleUiModel,
    device: PosatoDevice,
    onEdit: (ScheduleUiModel) -> Unit,
    onSetup: () -> Unit,
) {
    PosatoPanel(modifier = Modifier.fillMaxWidth()) {
        PosatoSelectionRow(
            modifier = Modifier.fillMaxWidth(),
            checked = schedule.enabled,
            onCheckedChange = {},
            enabled = false,
            supportingContent = { PosatoCaption("${schedule.daysLabel()} · ${schedule.hoursLabel()}") },
        ) { Text(schedule.name) }
        PosatoBody(
            when {
                !schedule.enabled -> "Turned off"
                schedule.skipped -> "Next session skipped"
                else -> "Next run: ${schedule.nextRunLabel}"
            },
        )
        PosatoCaption("Requires setup on this ${device.noun}")
        PosatoActionRow {
            PosatoButton(onClick = { onEdit(schedule) }, style = PosatoButtonStyle.Secondary) { Text("Edit ${schedule.name}") }
            PosatoButton(onClick = onSetup, style = PosatoButtonStyle.Quiet) { Text("Set up this ${device.noun}") }
            PosatoButton(onClick = {}, style = PosatoButtonStyle.Quiet, enabled = false) {
                Text(if (schedule.skipped) "Next session skipped" else "Skip next session")
            }
            PosatoButton(onClick = {}, style = PosatoButtonStyle.Quiet, enabled = false) { Text("Remove ${schedule.name}") }
        }
    }
}

@Composable
private fun ScheduleSetupPreview(
    device: PosatoDevice,
    layout: PosatoLayout,
    onBack: () -> Unit,
    onPreviewEditor: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back to schedules") }
        PosatoHeading(
            "Prepare this ${device.noun} for schedules.",
            description = if (device == PosatoDevice.Mac) {
                "Complete all three requirements before you create a schedule on this Mac."
            } else {
                "Allow automatic blocking on this device."
            },
            layout = layout,
        )
        if (device == PosatoDevice.Mac) {
            PosatoPanel(modifier = Modifier.fillMaxWidth()) {
                PosatoCaption("REQUIRED FOR BLOCKING")
                PosatoBody("Enable the background helper")
                PosatoCaption("Allow Posato to block your chosen websites and apps on this Mac.")
                PosatoButton(onClick = {}, enabled = false) { Text("Enable blocking") }
            }
            PosatoPanel(modifier = Modifier.fillMaxWidth()) { MacScheduleRequirements() }
            PosatoCaption("Automatic starts need your explicit approval. You will not be asked for a password when a schedule is due.")
        } else {
            PosatoBody("Allow Screen Time access so Posato can pause your chosen websites and applications on this device.")
            PosatoButton(onClick = {}, enabled = false) { Text("Allow Screen Time access") }
        }
        PosatoActionRow {
            PosatoButton(onClick = {}, enabled = false) { Text("Continue to schedule") }
            PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Not now") }
        }
        PosatoButton(onClick = onPreviewEditor, style = PosatoButtonStyle.Quiet) { Text("Preview schedule editor") }
        PosatoCaption("Explore the form without creating a schedule. Saving is unavailable in this preview.")
    }
}

@Composable
private fun ScheduledSessionPreview(
    schedule: ScheduleUiModel,
    layout: PosatoLayout,
    onBack: () -> Unit
) {
    PosatoHeading("Room for what matters.", eyebrow = "SCHEDULED SESSION PREVIEW", layout = layout)
    PosatoPanel(modifier = Modifier.fillMaxWidth()) {
        Text(schedule.name)
        PosatoBody("${schedule.daysLabel()} · ${schedule.hoursLabel()}")
        PosatoCaption("Ends at ${timeLabel(schedule.endHour, schedule.endMinute)}")
        PosatoNotice { Text("No restrictions are active from this preview.") }
        PosatoButton(onClick = {}, enabled = false) { Text("End early") }
    }
    PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back to schedules") }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SchedulesCompactPreview(
    @PreviewParameter(SchedulesScreenPreviewDataProvider::class) case: SchedulesPreviewCase
) {
    PosatoTheme {
        SchedulesScreen(case.state, PosatoDevice.IPhone, PosatoLayout.Compact, {}, {}, {})
    }
}

@Preview(widthDp = 820, heightDp = 900)
@Composable
private fun SchedulesExpandedPreview(
    @PreviewParameter(SchedulesScreenPreviewDataProvider::class) case: SchedulesPreviewCase
) {
    PosatoTheme {
        SchedulesScreen(case.state, PosatoDevice.Mac, PosatoLayout.Expanded, {}, {}, {})
    }
}
