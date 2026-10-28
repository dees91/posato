package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.duration_hours
import app.posato.generated.resources.duration_minutes
import app.posato.generated.resources.session_duration_hint
import app.posato.generated.resources.session_duration_hours
import app.posato.generated.resources.session_duration_label
import app.posato.generated.resources.session_duration_range
import app.posato.generated.resources.session_duration_range_cupertino
import app.posato.generated.resources.session_ends_count
import app.posato.generated.resources.session_eyebrow_next_pause
import app.posato.generated.resources.session_heading_space
import app.posato.generated.resources.session_no_pause_set
import app.posato.generated.resources.session_preset_1_h
import app.posato.generated.resources.session_preset_25_min
import app.posato.generated.resources.session_preset_2_h
import app.posato.generated.resources.session_preset_45_min
import app.posato.generated.resources.session_preset_4_h
import app.posato.generated.resources.session_preset_8_h
import app.posato.generated.resources.session_setup_continue
import app.posato.generated.resources.session_setup_preview
import app.posato.generated.resources.session_until_end_of_day
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
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
            stringResource(Res.string.session_heading_space),
            eyebrow = stringResource(Res.string.session_eyebrow_next_pause).takeUnless { platformUsesCupertinoChrome },
            layout = layout,
            description = stringResource(Res.string.session_duration_hint),
        )
        PauseSetPickerRow(
            state.pauseSets,
            state.setId,
            onChoose = onChoosePauseSet,
            placeholder = state.setName ?: stringResource(Res.string.session_no_pause_set),
        )
        if (platformUsesCupertinoChrome) {
            CupertinoDurationChoices(state, onChooseDuration)
        } else {
            DrawnDurationChoices(state, onChooseDuration)
        }
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            Text(stringResource(Res.string.session_setup_preview, state.formattedPreviewEnd.orEmpty()))
            PosatoCaption(
                if (platformUsesCupertinoChrome) {
                    stringResource(Res.string.session_duration_range_cupertino)
                } else {
                    stringResource(Res.string.session_duration_range)
                },
            )
            state.setupFailure?.let { Text(stringResource(it.setupMessage())) }
        }
        PosatoActionRow {
            PosatoButton(onReview, controlId = "session_setup_continue") { Text(stringResource(Res.string.session_setup_continue)) }
            if (!platformUsesCupertinoChrome) {
                PosatoButton(
                    onCancel,
                    style = PosatoButtonStyle.Quiet,
                    controlId = "action_cancel",
                ) { Text(stringResource(Res.string.action_cancel)) }
            }
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
        CompactLengthRows(state, onChooseDuration)
        state.formattedEndOfDay?.let { end ->
            PosatoTabBar(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth()) {
                PosatoTab(
                    selected = state.untilEndOfDay,
                    onClick = { onChooseDuration(SessionDurationChoice.EndOfDay) },
                    modifier = Modifier.weight(1f),
                    role = Role.RadioButton,
                    countContent = { Text(stringResource(Res.string.session_ends_count, end)) },
                    controlId = "session_until_end_of_day",
                ) { Text(stringResource(Res.string.session_until_end_of_day)) }
            }
        }
        PlatformDurationPicker(
            minutes = state.durationMinutes,
            range = SessionLimits.MIN_DURATION_MINUTES..minOf(SessionLimits.MAX_DURATION_MINUTES, COUNTDOWN_LIMIT_MINUTES),
            onChange = { minutes -> onChooseDuration(SessionDurationChoice.Length(minutes)) },
        )
    }
}

/** One row of six segments, or two rows of three when the widest label does not fit a sixth of the width, as with large text. */
@Composable
private fun CompactLengthRows(
    state: SessionUiState,
    onChooseDuration: (SessionDurationChoice) -> Unit,
) {
    BoxWithConstraints(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
        val widest = DurationPresets.maxOf { preset -> measurer.measure(preset.compactLabel, style).size.width }
        val segment = (maxWidth - PosatoSpace.Tiny * (DurationPresets.size + 1)) / DurationPresets.size - PosatoSpace.Tiny * 2
        val perRow = if (widest <= with(LocalDensity.current) { segment.toPx() }) DurationPresets.size else DurationPresets.size / 2
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            DurationPresets.chunked(perRow).forEach { row ->
                PosatoTabBar(Modifier.fillMaxWidth()) {
                    row.forEach { preset ->
                        PosatoTab(
                            selected = preset.isSelected(state),
                            onClick = { onChooseDuration(SessionDurationChoice.Length(preset.minutes)) },
                            modifier = Modifier.weight(1f),
                            role = Role.RadioButton,
                            contentPadding = PaddingValues(horizontal = PosatoSpace.Tiny, vertical = PosatoSpace.Medium),
                            controlId = preset.controlId,
                        ) { PresetLabel(preset.compactLabel, preset.minutes) }
                    }
                }
            }
        }
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
                PosatoNavigationItem(selected = preset.isSelected(state), onClick = { onLength(preset.minutes) }, controlId = preset.controlId) {
                    PresetLabel(stringResource(preset.label), preset.minutes)
                }
            }
            state.formattedEndOfDay?.let { end ->
                PosatoNavigationItem(
                    selected = state.untilEndOfDay,
                    onClick = { onChooseDuration(SessionDurationChoice.EndOfDay) },
                    controlId = "session_until_end_of_day",
                ) {
                    Text(stringResource(Res.string.session_until_end_of_day))
                    PosatoCaption(stringResource(Res.string.session_ends_count, end))
                }
            }
        }
        Row(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            PosatoNumberWheel(duration.hours, duration.hourRange, stringResource(Res.string.session_duration_hours), {
                onLength(duration.withHours(it))
            }, Modifier.weight(1f))
            PosatoNumberWheel(duration.minutes, duration.minuteRange, stringResource(Res.string.session_duration_label), {
                onLength(duration.withMinutes(it))
            }, Modifier.weight(1f))
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

@Composable
internal fun sessionLengthText(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    val hourText = pluralStringResource(Res.plurals.duration_hours, hours, hours)
    val minuteText = pluralStringResource(Res.plurals.duration_minutes, rest, rest)
    return when {
        hours == 0 -> minuteText
        rest == 0 -> hourText
        else -> "$hourText $minuteText"
    }
}

private class DurationPreset(
    val minutes: Int,
    val compactLabel: String,
    val label: StringResource,
    val controlId: String,
) {
    fun isSelected(state: SessionUiState): Boolean {
        return !state.untilEndOfDay && state.durationMinutes == minutes
    }
}

private const val MINUTES_PER_HOUR: Int = 60
private val DurationPresets = listOf(
    DurationPreset(25, "25m", Res.string.session_preset_25_min, "session_preset_25_min"),
    DurationPreset(45, "45m", Res.string.session_preset_45_min, "session_preset_45_min"),
    DurationPreset(60, "1h", Res.string.session_preset_1_h, "session_preset_1_h"),
    DurationPreset(120, "2h", Res.string.session_preset_2_h, "session_preset_2_h"),
    DurationPreset(240, "4h", Res.string.session_preset_4_h, "session_preset_4_h"),
    DurationPreset(480, "8h", Res.string.session_preset_8_h, "session_preset_8_h"),
)

/** The longest time the system countdown picker offers on iOS: 23 hours 59 minutes. */
private const val COUNTDOWN_LIMIT_MINUTES = 23 * 60 + 59
