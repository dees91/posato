package app.posato.feature.schedules.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal enum class ScheduleDay(
    val label: String
) {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday"),
}

internal data class ScheduleUiModel(
    val id: Int = 0,
    val name: String = "",
    val days: ImmutableList<ScheduleDay> = persistentListOf(
        ScheduleDay.MONDAY,
        ScheduleDay.TUESDAY,
        ScheduleDay.WEDNESDAY,
        ScheduleDay.THURSDAY,
        ScheduleDay.FRIDAY,
    ),
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 11,
    val endMinute: Int = 0,
    val enabled: Boolean = true,
    val skipped: Boolean = false,
    val nextRunLabel: String = "Not available yet",
) {
    fun hoursLabel(): String {
        return "${timeLabel(startHour, startMinute)} - ${timeLabel(endHour, endMinute)}"
    }

    fun daysLabel(): String {
        return days.joinToString(", ") { it.label.take(WEEKDAY_ABBREVIATION_LENGTH) }
    }

    override fun toString(): String {
        return "ScheduleUiModel(redacted)"
    }
}

internal fun timeLabel(
    hour: Int,
    minute: Int
): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

internal data class SchedulesUiState(
    val schedules: ImmutableList<ScheduleUiModel> = persistentListOf(),
    val editor: ScheduleUiModel? = null,
    val showingSetup: Boolean = false,
    val activeSchedule: ScheduleUiModel? = null,
)

@Stable
internal class SchedulesNavigationState {
    var state by mutableStateOf(SchedulesUiState())
        private set

    fun edit(schedule: ScheduleUiModel) {
        state = state.copy(editor = schedule)
    }

    fun closeEditor() {
        state = state.copy(editor = null, showingSetup = false)
    }

    fun showSetup(visible: Boolean) {
        state = state.copy(showingSetup = visible)
    }
}

private const val WEEKDAY_ABBREVIATION_LENGTH = 3
