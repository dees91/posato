package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoIntervalArtwork
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.SessionAction

@Composable
internal fun PrototypeSessionInterruption(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        when (state.surface) {
            PrototypeSurface.Blocked -> {
                PosatoIntervalArtwork()
                PosatoHeading(
                    "A moment to choose.",
                    eyebrow = "THIS ITEM IS PAUSED",
                    layout = layout,
                    description = "You chose a little space from this item until ${state.session.endsAt}. " +
                        "Open Posato to see your session or end it early.",
                )
                PosatoButton(onClick = { onAction(SessionAction.OpenPosato) }) { Text("Open Posato") }
                PosatoCaption("A study of the paused message, not the final platform-owned screen.")
            }

            PrototypeSurface.EarlyEnd -> {
                PosatoHeading(
                    "Ready to end this pause?",
                    eyebrow = "YOU’RE IN CONTROL",
                    layout = layout,
                    description = "Your selected items will be available on this ${state.platform.label} again. " +
                        "The change will be queued for your other devices to receive when they sync.",
                )
                PrototypeSessionEnd(state)
                PosatoActionRow {
                    PosatoButton(onClick = { onAction(SessionAction.CancelEarlyEnd) }) { Text("Keep session") }
                    PosatoButton(onClick = {
                        onAction(SessionAction.ConfirmEarlyEnd)
                    }, style = PosatoButtonStyle.Destructive) { Text("End session early") }
                }
            }

            PrototypeSurface.Recovery -> {
                PosatoHeading(
                    "Let’s get your pause ready.",
                    eyebrow = "NEEDS YOUR ATTENTION",
                    layout = layout,
                    description = "Something changed on this ${state.platform.label}. Your saved choices are still here.",
                )
                PrototypeRepairNotice(state, onAction)
                PosatoButton(onClick = { onAction(SessionAction.ReturnToSession) }, style = PosatoButtonStyle.Quiet) { Text("Back to session") }
            }

            PrototypeSurface.Welcome, PrototypeSurface.Privacy, PrototypeSurface.WorkspaceCheck, PrototypeSurface.KeyWait,
            PrototypeSurface.Permission, PrototypeSurface.Targets, PrototypeSurface.DomainEditor, PrototypeSurface.AppPicker,
            PrototypeSurface.Items, PrototypeSurface.Home, PrototypeSurface.SessionSetup,
            PrototypeSurface.SessionReview, PrototypeSurface.Active -> {
                error("Not an interruption surface.")
            }
        }
    }
}
