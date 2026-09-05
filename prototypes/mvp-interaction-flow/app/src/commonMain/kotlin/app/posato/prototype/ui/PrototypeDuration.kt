package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoChoiceGroup
import app.posato.prototype.designsystem.PosatoDurationChoice
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoTextField
import app.posato.prototype.model.OutcomeTone
import app.posato.prototype.model.PrototypeAction
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
    val draft = rememberTextFieldState(state.session.durationMinutes.toString())
    LaunchedEffect(state.session.durationMinutes) { draft.setTextAndPlaceCursorAtEnd(state.session.durationMinutes.toString()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "How much space\ndo you need?",
            eyebrow = "YOUR NEXT PAUSE",
            layout = layout,
            description = "Give this pause a clear ending. You can always choose to end it early.",
        )
        PosatoChoiceGroup {
            DurationPresets.forEach { minutes ->
                PosatoDurationChoice(
                    valueLabel = minutes.toString(),
                    unitLabel = "minutes",
                    selected = draft.text.toString() == minutes.toString(),
                    onClick = {
                        draft.setTextAndPlaceCursorAtEnd(minutes.toString())
                        onAction(SetDuration(minutes.toString()))
                    },
                )
            }
        }
        PosatoTextField(
            state = draft,
            label = "Or choose your own",
            supportingText = "5 minutes to 24 hours. Demo clock: 17:45.",
            errorMessage = state.outcome.message.takeIf { state.outcome.tone == OutcomeTone.Blocked },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            onSubmit = { onReviewDuration(draft.text.toString()) },
        )
        PosatoActionRow {
            PosatoButton(onClick = { onReviewDuration(draft.text.toString()) }) { Text("Review session") }
            PosatoButton(onClick = { onAction(SessionAction.ReturnToSession) }, style = PosatoButtonStyle.Quiet) { Text("Cancel") }
        }
    }
}
