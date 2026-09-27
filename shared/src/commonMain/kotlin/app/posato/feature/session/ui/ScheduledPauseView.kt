package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoEndTime
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTone
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.schedules.host.ScheduledPauseState

/** The scheduled pause as Session shows it; [until] is the combined pause's latest end, ready to display. */
internal data class ScheduledPauseView(
    val name: String,
    val until: String,
    val state: ScheduledPauseState,
) {
    val restricts: Boolean
        get() {
            return state == ScheduledPauseState.APPLIED
        }

    override fun toString(): String {
        return "ScheduledPauseView(redacted)"
    }
}

/** A restricting scheduled pause: its name, the latest end, and End early. */
@Composable
internal fun ScheduledPauseRunning(
    view: ScheduledPauseView,
    onEnd: () -> Unit,
) {
    PosatoEndTime("Until ${view.until}", supportingText = "${view.name} is running on this device.")
    PosatoButton(onEnd, style = PosatoButtonStyle.Quiet) { Text("End early") }
}

/**
 * A scheduled pause that is due but does not restrict this Mac. Setup required offers the one missing
 * action; waiting and retrying only say what happens, and Start stays available.
 */
@Composable
internal fun ScheduledPauseAttention(
    view: ScheduledPauseView,
    macSetup: MacSetupPresentation?,
    macActions: MacSetupCallbacks,
    onSetup: () -> Unit,
) {
    when (view.state) {
        ScheduledPauseState.SETUP_REQUIRED -> PosatoNotice(tone = PosatoTone.Caution) {
            Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                Text("Setup required on this Mac")
                PosatoCaption(
                    "${view.name} couldn't start here. Finish setup so schedules start on their own. No password is asked when a schedule is due.",
                )
                if (macSetup?.schedulesNeedConsent == true) {
                    PosatoButton(macActions.allowSchedules, style = PosatoButtonStyle.Secondary) { Text("Allow schedules to start on this Mac") }
                } else {
                    PosatoButton(onSetup, style = PosatoButtonStyle.Secondary) { Text("Finish setup") }
                }
            }
        }

        ScheduledPauseState.WAITING -> PosatoCaption("${view.name} starts here when this Mac is available again.")

        ScheduledPauseState.RETRYING -> PosatoCaption("Starting ${view.name} on this Mac…")

        ScheduledPauseState.APPLIED -> Unit
    }
}

@Composable
internal fun ScheduledEarlyEndContent(
    view: ScheduledPauseView,
    layout: PosatoLayout,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "Ready to return?",
            eyebrow = "END THIS PAUSE",
            description = "This ends ${view.name} and any session running now, here and on your other devices. The schedule keeps repeating.",
            layout = layout,
        )
        PosatoCaption("A device whose date is different, in another time zone around midnight, keeps its own pause until it ends there.")
        PosatoButton(onConfirm) { Text("End pause") }
        PosatoButton(onCancel, style = PosatoButtonStyle.Quiet) { Text("Keep pausing") }
    }
}
