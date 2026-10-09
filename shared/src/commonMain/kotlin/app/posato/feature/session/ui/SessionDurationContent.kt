package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.posato.core.designsystem.PlatformDurationPicker
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoChoiceGroup
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNavigationItem
import app.posato.core.designsystem.PosatoNumberWheel
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTab
import app.posato.core.designsystem.PosatoTabBar
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.ui.PauseSetPickerRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionDurationContent(
    state: SessionUiState,
    layout: PosatoLayout,
    onChooseDuration: (SessionDurationChoice) -> Unit,
    onReview: () -> Unit,
    onCancel: () -> Unit,
    onChoosePauseSet: (PauseSetId) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "How much space\ndo you need?",
            eyebrow = "YOUR NEXT PAUSE".takeUnless { platformUsesCupertinoChrome },
            layout = layout,
            description = "Choose a quick pause, or make it your own.",
        )
        PauseSetPickerRow(state.pauseSets, state.setId, onChoose = onChoosePauseSet, placeholder = state.setName ?: "No pause set")
        if (platformUsesCupertinoChrome) {
            CupertinoDurationChoices(state, onChooseDuration)
        } else {
            DrawnDurationChoices(state, onChooseDuration)
        }
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            Text("Ends at ${state.formattedPreviewEnd.orEmpty()}")
            PosatoCaption(
                if (platformUsesCupertinoChrome) {
                    "5 minutes to 23 hours 59 minutes · you stay in control"
                } else {
                    "5 minutes to 24 hours · you stay in control"
                },
            )
            state.setupFailure?.let { Text(stringResource(it.setupMessage())) }
        }
        PosatoActionRow {
            PosatoButton(onReview) { Text("Review session") }
            if (!platformUsesCupertinoChrome) PosatoButton(onCancel, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
        }
    }
}

internal class SessionDurationParts(
    totalMinutes: Int
) {
    val hours: Int = totalMinutes / MINUTES_PER_HOUR
    val minutes: Int = totalMinutes % MINUTES_PER_HOUR
    val hourRange: IntRange = 0..SessionLimits.MAX_DURATION_MINUTES / MINUTES_PER_HOUR
    val minuteRange: IntRange = when (hours) {
        0 -> SessionLimits.MIN_DURATION_MINUTES until MINUTES_PER_HOUR
        hourRange.last -> 0..0
        else -> 0 until MINUTES_PER_HOUR
    }

    fun withHours(hours: Int): Int {
        return (hours * MINUTES_PER_HOUR + minutes).coerceIn(SessionLimits.MIN_DURATION_MINUTES, SessionLimits.MAX_DURATION_MINUTES)
    }

    fun withMinutes(minutes: Int): Int {
        return (hours * MINUTES_PER_HOUR + minutes).coerceIn(SessionLimits.MIN_DURATION_MINUTES, SessionLimits.MAX_DURATION_MINUTES)
    }
}

@Composable
private fun CupertinoDurationChoices(
    state: SessionUiState,
    onChooseDuration: (SessionDurationChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoTabBar(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth()) {
            DurationPresets.forEach { preset ->
                PosatoTab(
                    selected = preset.isSelected(state),
                    onClick = { onChooseDuration(SessionDurationChoice.Length(preset.minutes)) },
                    modifier = Modifier.weight(1f),
                    role = Role.RadioButton,
                    contentPadding = PaddingValues(horizontal = PosatoSpace.Tiny, vertical = PosatoSpace.Medium),
                ) { PresetLabel(preset.compactLabel, preset.minutes) }
            }
        }
        state.formattedEndOfDay?.let { end ->
            PosatoTabBar(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth()) {
                PosatoTab(
                    selected = state.untilEndOfDay,
                    onClick = { onChooseDuration(SessionDurationChoice.EndOfDay) },
                    modifier = Modifier.weight(1f),
                    role = Role.RadioButton,
                    countContent = { Text("ends $end") },
                ) { Text(END_OF_DAY_LABEL) }
            }
        }
        PlatformDurationPicker(
            minutes = state.durationMinutes,
            range = SessionLimits.MIN_DURATION_MINUTES..minOf(SessionLimits.MAX_DURATION_MINUTES, COUNTDOWN_LIMIT_MINUTES),
            onChange = { minutes -> onChooseDuration(SessionDurationChoice.Length(minutes)) },
        )
    }
}

@Composable
private fun DrawnDurationChoices(
    state: SessionUiState,
    onChooseDuration: (SessionDurationChoice) -> Unit,
) {
    val duration = SessionDurationParts(state.durationMinutes)
    val onLength = { minutes: Int -> onChooseDuration(SessionDurationChoice.Length(minutes)) }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoChoiceGroup {
            DurationPresets.forEach { preset ->
                PosatoNavigationItem(selected = preset.isSelected(state), onClick = { onLength(preset.minutes) }) {
                    PresetLabel(preset.label, preset.minutes)
                }
            }
            state.formattedEndOfDay?.let { end ->
                PosatoNavigationItem(selected = state.untilEndOfDay, onClick = { onChooseDuration(SessionDurationChoice.EndOfDay) }) {
                    Text(END_OF_DAY_LABEL)
                    PosatoCaption("ends $end")
                }
            }
        }
        Row(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            PosatoNumberWheel(duration.hours, duration.hourRange, "Hours", { onLength(duration.withHours(it)) }, Modifier.weight(1f))
            PosatoNumberWheel(duration.minutes, duration.minuteRange, "Minutes", { onLength(duration.withMinutes(it)) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PresetLabel(
    text: String,
    minutes: Int,
) {
    val spoken = sessionLengthText(minutes)
    Text(text, Modifier.semantics { contentDescription = spoken }, maxLines = 1)
}

internal fun sessionLengthText(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    val hourText = if (hours == 1) "1 hour" else "$hours hours"
    val minuteText = if (rest == 1) "1 minute" else "$rest minutes"
    return when {
        hours == 0 -> minuteText
        rest == 0 -> hourText
        else -> "$hourText $minuteText"
    }
}

private class DurationPreset(
    val minutes: Int,
    val compactLabel: String,
    val label: String,
) {
    fun isSelected(state: SessionUiState): Boolean {
        return !state.untilEndOfDay && state.durationMinutes == minutes
    }
}

internal const val END_OF_DAY_LABEL: String = "Until end of day"
private const val MINUTES_PER_HOUR: Int = 60
private val DurationPresets = listOf(
    DurationPreset(25, "25m", "25 min"),
    DurationPreset(45, "45m", "45 min"),
    DurationPreset(60, "1h", "1 h"),
    DurationPreset(120, "2h", "2 h"),
    DurationPreset(240, "4h", "4 h"),
    DurationPreset(480, "8h", "8 h"),
)

/** The longest time the system countdown picker offers on iOS: 23 hours 59 minutes. */
private const val COUNTDOWN_LIMIT_MINUTES = 23 * 60 + 59
