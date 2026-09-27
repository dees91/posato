package app.posato.feature.schedules.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.posato.feature.schedules.domain.ScheduleId
import kotlinx.collections.immutable.persistentListOf

internal data class SchedulesPreviewCase(
    val name: String,
    val state: SchedulesUiState,
) {
    override fun toString(): String {
        return name
    }
}

internal class SchedulesScreenPreviewDataProvider : PreviewParameterProvider<SchedulesPreviewCase> {
    private val morning = ScheduleRowModel(
        id = ScheduleId("000000000000400080000000000000a1"),
        name = "Morning focus",
        daysLabel = "Mon, Tue, Wed, Thu, Fri",
        hoursLabel = "09:00 - 11:00",
        enabled = true,
        refused = false,
        nextRunLabel = "Next: Mon, 9:00 AM",
        skippedLabel = null,
        canSkip = true,
    )
    override val values: Sequence<SchedulesPreviewCase> = sequenceOf(
        SchedulesPreviewCase("Empty", SchedulesUiState(loaded = true)),
        SchedulesPreviewCase("One schedule", SchedulesUiState(loaded = true, schedules = persistentListOf(morning))),
        SchedulesPreviewCase(
            "Skipped",
            SchedulesUiState(loaded = true, schedules = persistentListOf(morning.copy(skippedLabel = "Skipped: Mon, 9:00 AM"))),
        ),
        SchedulesPreviewCase("Turned off", SchedulesUiState(loaded = true, schedules = persistentListOf(morning.copy(enabled = false)))),
        SchedulesPreviewCase("Refused", SchedulesUiState(loaded = true, schedules = persistentListOf(morning.copy(refused = true)))),
        SchedulesPreviewCase("New schedule", SchedulesUiState(loaded = true, editor = ScheduleDraft())),
        SchedulesPreviewCase("Device setup", SchedulesUiState(loaded = true, showingSetup = true)),
    )
}
