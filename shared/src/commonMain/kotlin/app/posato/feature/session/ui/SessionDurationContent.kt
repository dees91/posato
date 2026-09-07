package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoChoiceGroup
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNavigationItem
import app.posato.core.designsystem.PosatoNumberWheel
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.session.domain.SessionLimits
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionDurationContent(
    state: SessionUiState,
    layout: PosatoLayout,
    onSetDuration: (Int) -> Unit,
    onReview: () -> Unit,
    onCancel: () -> Unit,
) {
    val duration = SessionDurationParts(state.durationMinutes)
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
        PosatoHeading(
            "How much space\ndo you need?",
            eyebrow = "YOUR NEXT PAUSE",
            layout = layout,
            description = "Choose a quick pause, or make it your own.",
        )
        PosatoChoiceGroup {
            DurationPresets.forEach { preset ->
                PosatoNavigationItem(selected = state.durationMinutes == preset, onClick = { onSetDuration(preset) }) { Text("$preset min") }
            }
        }
        Row(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            PosatoNumberWheel(duration.hours, duration.hourRange, "Hours", { onSetDuration(duration.withHours(it)) }, Modifier.weight(1f))
            PosatoNumberWheel(duration.minutes, duration.minuteRange, "Minutes", { onSetDuration(duration.withMinutes(it)) }, Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            Text("Ends at ${state.formattedPreviewEnd.orEmpty()}")
            PosatoCaption("5 minutes to 24 hours · you stay in control")
            state.setupFailure?.let { Text(stringResource(it.setupMessage())) }
        }
        PosatoActionRow {
            PosatoButton(onReview) { Text("Review session") }
            PosatoButton(onCancel, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
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

private const val MINUTES_PER_HOUR: Int = 60
private val DurationPresets = listOf(25, 45, 60)
