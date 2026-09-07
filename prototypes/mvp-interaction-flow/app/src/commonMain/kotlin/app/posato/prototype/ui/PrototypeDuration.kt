package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoChoiceGroup
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoNavigationItem
import app.posato.prototype.designsystem.PosatoNumberWheel
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeClock
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.SessionAction
import app.posato.prototype.model.SetDuration

private val DurationPresets = listOf(25, 45, 60)

@Composable
internal fun PrototypeDuration(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    onReviewDuration: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = state.session.durationMinutes
    val duration = PrototypeDurationParts(minutes)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
        PosatoHeading(
            "How much space\ndo you need?",
            eyebrow = "YOUR NEXT PAUSE",
            layout = layout,
            description = "Choose a quick pause, or make it your own.",
        )
        PosatoChoiceGroup {
            DurationPresets.forEach { preset ->
                PosatoNavigationItem(selected = minutes == preset, onClick = { onAction(SetDuration(preset.toString())) }) {
                    Text("$preset min")
                }
            }
        }
        Row(Modifier.widthIn(max = PosatoSize.Phone).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = duration.hours,
                range = duration.hourRange,
                label = "Hours",
                onValueChange = { onAction(SetDuration(duration.withHours(it).toString())) },
            )
            PosatoNumberWheel(
                modifier = Modifier.weight(1f),
                value = duration.minutes,
                range = duration.minuteRange,
                label = "Minutes",
                onValueChange = { onAction(SetDuration(duration.withMinutes(it).toString())) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
            Text("Ends at ${PrototypeClock.resolvedEnd(minutes)}")
            PosatoCaption("5 minutes to 24 hours · Demo clock ${PrototypeClock.NOW}")
        }
        PosatoActionRow {
            PosatoButton(onClick = { onReviewDuration(minutes.toString()) }) { Text("Review session") }
            PosatoButton(onClick = { onAction(SessionAction.ReturnToSession) }, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
        }
    }
}
