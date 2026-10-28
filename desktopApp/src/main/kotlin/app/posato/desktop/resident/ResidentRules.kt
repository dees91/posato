package app.posato.desktop.resident

import app.posato.desktop.PresenceMenuActionId
import app.posato.feature.presence.PresenceAction
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import app.posato.feature.presence.SessionWindowRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a status menu action asks of the window it shows; null for one that only shows it, and for Quit Posato. */
internal fun windowRequestOf(action: PresenceMenuActionId): SessionWindowRequest? = when (action) {
    PresenceMenuActionId.START_SESSION -> SessionWindowRequest.START_SESSION
    PresenceMenuActionId.END_SESSION_EARLY -> SessionWindowRequest.END_SESSION_EARLY
    PresenceMenuActionId.RESUME_RESTRICTIONS -> SessionWindowRequest.SESSION
    PresenceMenuActionId.OPEN_POSATO, PresenceMenuActionId.QUIT -> null
}

internal enum class CloseOutcome { HIDE, QUIT_ASKING, EXIT }

/**
 * Closing hides Posato only when it can be shown again: by its icon, or, for a pause or an enabled schedule, by a
 * second launch. When nothing could show it again ([reachable] false), closing quits as **Quit Posato** does.
 */
internal fun closeOutcome(
    reachable: Boolean,
    iconShown: Boolean,
    menu: PresenceMenu,
): CloseOutcome = when {
    !reachable -> CloseOutcome.QUIT_ASKING
    iconShown || keepsRunning(menu) -> CloseOutcome.HIDE
    else -> CloseOutcome.EXIT
}

/** A hidden Posato without an icon and with no pause or enabled schedule has nothing left to do. */
internal fun hiddenWithNothingToDo(
    visible: Boolean,
    iconShown: Boolean,
    menu: PresenceMenu,
): Boolean = !visible && !iconShown && menu.state != PresenceState.LOADING && !keepsRunning(menu)

/** A pause runs or a schedule is on: the work a hidden Posato keeps doing. */
internal fun keepsRunning(menu: PresenceMenu): Boolean =
    menu.state == PresenceState.ENFORCING || menu.state == PresenceState.NOT_ENFORCING || menu.schedulesEnabled

internal fun formatClockTime(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** The menu before the presence state is known. */
internal val LOADING_MENU = PresenceMenu(PresenceState.LOADING, PresenceAction.OPEN_POSATO, null)
