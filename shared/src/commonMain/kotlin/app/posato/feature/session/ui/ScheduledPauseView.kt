package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoAlert
import app.posato.core.designsystem.PosatoAlertAction
import app.posato.core.designsystem.PosatoAlertRole
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
import app.posato.feature.schedules.domain.ScheduleBreakRules
import app.posato.feature.schedules.host.ScheduledPauseState
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_allow_schedules_mac
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_end_break
import app.posato.generated.resources.action_end_early
import app.posato.generated.resources.action_end_pause
import app.posato.generated.resources.action_finish_setup
import app.posato.generated.resources.action_keep_pausing
import app.posato.generated.resources.action_take_a_break
import app.posato.generated.resources.duration_minutes
import app.posato.generated.resources.notification_schedule_setup_title
import app.posato.generated.resources.session_break_body
import app.posato.generated.resources.session_break_manual
import app.posato.generated.resources.session_break_started
import app.posato.generated.resources.session_break_taken
import app.posato.generated.resources.session_break_title
import app.posato.generated.resources.session_break_together
import app.posato.generated.resources.session_end_scheduled
import app.posato.generated.resources.session_end_this_pause
import app.posato.generated.resources.session_end_timezone
import app.posato.generated.resources.session_named_couldnt_start
import app.posato.generated.resources.session_named_running
import app.posato.generated.resources.session_named_screen_time
import app.posato.generated.resources.session_named_starting
import app.posato.generated.resources.session_named_starts_when
import app.posato.generated.resources.session_on_break_until
import app.posato.generated.resources.session_ready_return
import app.posato.generated.resources.session_until
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The scheduled pause as Session shows it; [until] is the combined pause's latest end, ready to display. */
internal data class ScheduledPauseView(
    val name: String,
    val until: String,
    val state: ScheduledPauseState,
    /** Each running part: "Set: Work, until 17:00" for a manual session, "Evening (schedule), Set: Leisure, until 22:00". */
    val parts: List<String> = emptyList(),
    val notPausedYet: Int = 0,
    val breakView: ScheduledBreakView = ScheduledBreakView(),
) {
    val restricts: Boolean
        get() {
            return state == ScheduledPauseState.APPLIED
        }

    val onBreak: Boolean
        get() {
            return breakView.until != null
        }

    override fun toString(): String {
        return "ScheduledPauseView(redacted)"
    }
}

/** Why Take a break is disabled now. */
internal enum class BreakUnavailable {
    /** A running occurrence already used its one break. */
    TAKEN,

    /** A manual session is active in the workspace, here or received. */
    MANUAL_SESSION,
}

/**
 * The break of a running scheduled pause as Session shows it: [until] is the running break's end, ready to display;
 * [startedDuring] names the running parts that started during it and restrict; [names] are the running schedules.
 */
internal data class ScheduledBreakView(
    val until: String? = null,
    val startedDuring: List<String> = emptyList(),
    val unavailable: BreakUnavailable? = null,
    val names: List<String> = emptyList(),
) {
    override fun toString(): String {
        return "ScheduledBreakView(redacted)"
    }
}

/** Take a break asks for a length first; End break returns restrictions at once. */
internal class ScheduledBreakActions(
    val choosing: Boolean = false,
    val onRequest: () -> Unit = {},
    val onChoose: (Int) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onEnd: () -> Unit = {},
)

/** A restricting scheduled pause: its name, the latest end, a running break, and the break and End early actions. */
@Composable
internal fun ScheduledPauseRunning(
    view: ScheduledPauseView,
    onEnd: () -> Unit,
    breakActions: ScheduledBreakActions = ScheduledBreakActions(),
) {
    PosatoEndTime(stringResource(Res.string.session_until, view.until), supportingText = stringResource(Res.string.session_named_running, view.name))
    ScheduledBreakLines(view.breakView)
    view.parts.forEach { part -> PosatoCaption(part) }
    ScheduledPauseActions(view.breakView, breakActions, onEnd)
}

@Composable
private fun ScheduledBreakLines(view: ScheduledBreakView) {
    view.until?.let { until -> PosatoCaption(stringResource(Res.string.session_on_break_until, until)) }
    view.startedDuring.forEach { name -> PosatoCaption(stringResource(Res.string.session_break_started, name)) }
}

/** Take a break or End break beside End early, with the reason a break is unavailable under them. */
@Composable
private fun ScheduledPauseActions(
    view: ScheduledBreakView,
    actions: ScheduledBreakActions,
    onEnd: (() -> Unit)?,
) {
    PosatoActionRow {
        if (view.until != null) {
            PosatoButton(actions.onEnd, style = PosatoButtonStyle.Quiet, controlId = "action_end_break") {
                Text(stringResource(Res.string.action_end_break))
            }
        } else {
            PosatoButton(actions.onRequest, style = PosatoButtonStyle.Quiet, enabled = view.unavailable == null, controlId = "action_take_a_break") {
                Text(stringResource(Res.string.action_take_a_break))
            }
        }
        onEnd?.let { end ->
            PosatoButton(end, style = PosatoButtonStyle.Quiet, controlId = "action_end_early") { Text(stringResource(Res.string.action_end_early)) }
        }
    }
    if (view.until == null) {
        when (view.unavailable) {
            BreakUnavailable.TAKEN -> PosatoCaption(stringResource(Res.string.session_break_taken))
            BreakUnavailable.MANUAL_SESSION -> PosatoCaption(stringResource(Res.string.session_break_manual))
            null -> Unit
        }
    }
}

/** The length choice Take a break opens: the system alert on iOS, the dialog on a computer. */
@Composable
internal fun ScheduledBreakAlert(
    view: ScheduledPauseView,
    actions: ScheduledBreakActions,
) {
    if (!actions.choosing) {
        return
    }
    val body = stringResource(Res.string.session_break_body)
    val names = view.breakView.names
    val together = if (names.size > 1) {
        stringResource(Res.string.session_break_together, names.dropLast(1).joinToString(", "), names.last())
    } else {
        null
    }
    val lengths = ScheduleBreakRules.MINUTES.sorted().map { minutes ->
        PosatoAlertAction(
            pluralStringResource(Res.plurals.duration_minutes, minutes, minutes),
            { actions.onChoose(minutes) },
            controlId = "action_break_$minutes",
        )
    }
    PosatoAlert(
        title = stringResource(Res.string.session_break_title),
        message = listOfNotNull(together, body).joinToString(" "),
        onDismiss = actions.onCancel,
        actions = lengths + PosatoAlertAction(
            stringResource(Res.string.action_cancel),
            { actions.onCancel() },
            PosatoAlertRole.Cancel,
            controlId = "action_cancel_break",
        ),
    )
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
    breakActions: ScheduledBreakActions = ScheduledBreakActions(),
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
    // A break can be taken whenever a scheduled pause runs, also on a device it does not restrict now.
    ScheduledBreakLines(view.breakView)
    ScheduledPauseActions(view.breakView, breakActions, onEnd = null)
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
