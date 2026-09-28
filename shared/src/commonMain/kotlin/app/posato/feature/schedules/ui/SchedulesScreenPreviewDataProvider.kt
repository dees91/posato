package app.posato.feature.schedules.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf

internal data class SchedulesPreviewCase(
    val name: String,
    val state: SchedulesUiState
) {
    override fun toString(): String {
        return name
    }
}

internal class SchedulesScreenPreviewDataProvider : PreviewParameterProvider<SchedulesPreviewCase> {
    private val morning = ScheduleUiModel(id = 1, name = "Morning focus", nextRunLabel = "Monday at 09:00")
    override val values: Sequence<SchedulesPreviewCase> = sequenceOf(
        SchedulesPreviewCase("Empty", SchedulesUiState()),
        SchedulesPreviewCase("Requires setup", SchedulesUiState(schedules = persistentListOf(morning))),
        SchedulesPreviewCase("Skipped", SchedulesUiState(schedules = persistentListOf(morning.copy(skipped = true)))),
        SchedulesPreviewCase("Disabled", SchedulesUiState(schedules = persistentListOf(morning.copy(enabled = false)))),
        SchedulesPreviewCase("New schedule", SchedulesUiState(editor = ScheduleUiModel())),
        SchedulesPreviewCase("Edit schedule", SchedulesUiState(editor = morning)),
        SchedulesPreviewCase("Device setup", SchedulesUiState(showingSetup = true)),
        SchedulesPreviewCase("Scheduled session", SchedulesUiState(activeSchedule = morning)),
    )
}
