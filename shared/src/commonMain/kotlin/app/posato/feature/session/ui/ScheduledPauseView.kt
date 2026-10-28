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
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_allow_schedules_mac
import app.posato.generated.resources.action_end_early
import app.posato.generated.resources.action_end_pause
import app.posato.generated.resources.action_finish_setup
import app.posato.generated.resources.action_keep_pausing
import app.posato.generated.resources.notification_schedule_setup_title
import app.posato.generated.resources.session_end_scheduled
import app.posato.generated.resources.session_end_this_pause
import app.posato.generated.resources.session_end_timezone
import app.posato.generated.resources.session_named_couldnt_start
import app.posato.generated.resources.session_named_running
import app.posato.generated.resources.session_named_screen_time
import app.posato.generated.resources.session_named_starting
import app.posato.generated.resources.session_named_starts_when
import app.posato.generated.resources.session_ready_return
import app.posato.generated.resources.session_until
import org.jetbrains.compose.resources.stringResource

/** The scheduled pause as Session shows it; [until] is the combined pause's latest end, ready to display. */
internal data class ScheduledPauseView(
    val name: String,
    val until: String,
    val state: ScheduledPauseState,
    /** Each running part: "Set: Work, until 17:00" for a manual session, "Evening (schedule), Set: Leisure, until 22:00". */
    val parts: List<String> = emptyList(),
    val notPausedYet: Int = 0,
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
    PosatoEndTime(stringResource(Res.string.session_until, view.until), supportingText = stringResource(Res.string.session_named_running, view.name))
    view.parts.forEach { part -> PosatoCaption(part) }
    PosatoButton(onEnd, style = PosatoButtonStyle.Quiet, controlId = "action_end_early") { Text(stringResource(Res.string.action_end_early)) }
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
        ScheduledPauseState.SETUP_REQUIRED -> if (macSetup == null) {
            // On iPhone, Screen Time access is the only requirement; Schedules offers it.
            PosatoNotice(tone = PosatoTone.Caution) {
                Text(stringResource(Res.string.session_named_screen_time, view.name))
            }
        } else {
            PosatoNotice(tone = PosatoTone.Caution) {
                Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
                    Text(stringResource(Res.string.notification_schedule_setup_title))
                    PosatoCaption(stringResource(Res.string.session_named_couldnt_start, view.name))
                    if (macSetup.schedulesNeedConsent) {
                        PosatoButton(macActions.allowSchedules, style = PosatoButtonStyle.Secondary, controlId = "action_allow_schedules_mac") {
                            Text(stringResource(Res.string.action_allow_schedules_mac))
                        }
                    } else {
                        PosatoButton(onSetup, style = PosatoButtonStyle.Secondary, controlId = "action_finish_setup") {
                            Text(stringResource(Res.string.action_finish_setup))
                        }
                    }
                }
            }
        }

        ScheduledPauseState.WAITING -> PosatoCaption(stringResource(Res.string.session_named_starts_when, view.name))

        ScheduledPauseState.RETRYING -> PosatoCaption(stringResource(Res.string.session_named_starting, view.name))

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
            stringResource(Res.string.session_ready_return),
            eyebrow = stringResource(Res.string.session_end_this_pause),
            description = stringResource(Res.string.session_end_scheduled, view.name),
            layout = layout,
        )
        PosatoCaption(stringResource(Res.string.session_end_timezone))
        PosatoButton(onConfirm, controlId = "action_end_pause") { Text(stringResource(Res.string.action_end_pause)) }
        PosatoButton(onCancel, style = PosatoButtonStyle.Quiet, controlId = "action_keep_pausing") {
            Text(stringResource(Res.string.action_keep_pausing))
        }
    }
}

/** Added items that this device's limits leave unpaused for now. */
@Composable
internal fun NotPausedYetNotice(notice: String?) {
    notice?.let { text -> PosatoNotice(tone = PosatoTone.Caution) { Text(text) } }
}
