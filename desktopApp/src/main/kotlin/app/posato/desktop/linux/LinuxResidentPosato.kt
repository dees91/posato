package app.posato.desktop.linux

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import app.posato.ApplicationNavigation
import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.linux.status.StatusAreaState
import app.posato.desktop.linux.status.statusMenuEntries
import app.posato.desktop.presenceMenuModel
import app.posato.feature.presence.LinuxPresenceCopy
import app.posato.feature.presence.PresenceAction
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import app.posato.feature.presence.ResidenceDialogContent
import app.posato.feature.presence.SessionWindowRequest
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class LinuxNotice { QUIT, FIRST_CLOSE, FIRST_CLOSE_NO_ICON }

/**
 * Posato for Linux as a resident application (LINUX-003): the session host and schedules run at application level,
 * the window comes and goes, the status icon mirrors the Mac menu, and quitting asks only while a schedule is on.
 */
@Composable
internal fun ApplicationScope.LinuxResidentPosato(host: LinuxResidentHost) {
    val presence = host.graph.presence
    val menu by presence.menu.collectAsState(LOADING_MENU)
    val watcher by (host.statusArea?.watcherPresent ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val resident = rememberResidentWindow(!host.startHidden)
    var notice by remember { mutableStateOf<LinuxNotice?>(null) }
    val quit: () -> Unit = { if (menu.schedulesEnabled) notice = LinuxNotice.QUIT else exitApplication() }
    host.residence.onQuit = quit

    LaunchedEffect(Unit) { presence.runWhileResident() }
    LaunchedEffect(Unit) { host.events.showRequests.collect { resident.show() } }
    LaunchedEffect(Unit) { host.events.menuOpened.collect { presence.onMenuOpened() } }
    LaunchedEffect(Unit) {
        host.events.actions.collect { action ->
            if (action == PresenceMenuActionId.QUIT) return@collect quit()
            resident.show()
            windowRequestOf(action)?.let(presence::request)
        }
    }
    StatusIconEffect(host, menu)
    // Without a status icon, a hidden Posato stays only for a pause or an enabled schedule; a login launch waits for the
    // panel to come up first, and a panel that goes away is followed live.
    LaunchedEffect(resident.visible, watcher, menu) {
        if (!hiddenWithNothingToDo(resident.visible, watcher, menu)) return@LaunchedEffect
        delay(WATCHER_GRACE_MILLIS)
        exitApplication()
    }

    val navigation = remember { ApplicationNavigation() }
    if (resident.visible) {
        Window(
            onCloseRequest = {
                when (closeOutcome(host.statusArea != null, watcher, menu)) {
                    CloseOutcome.HIDE -> {
                        resident.visible = false
                        if (host.firstClose.firstTime()) notice = if (watcher) LinuxNotice.FIRST_CLOSE else LinuxNotice.FIRST_CLOSE_NO_ICON
                    }

                    CloseOutcome.QUIT_ASKING -> {
                        quit()
                    }

                    CloseOutcome.EXIT -> {
                        exitApplication()
                    }
                }
            },
            title = "Posato",
            state = resident.state,
            onPreviewKeyEvent = { event -> event.isQuitKey().also { quitKey -> if (quitKey) quit() } },
        ) {
            DisposableEffect(window) {
                resident.composed = window
                onDispose { resident.composed = null }
            }
            host.graph.application.Content(
                hostsSession = false,
                windowRequests = presence.windowRequests,
                navigation = navigation,
                residence = host.residence,
            )
        }
    }
    notice?.let { shown -> NoticeWindow(shown, host.copy, onDismiss = { notice = null }, onQuit = ::exitApplication) }
}

/** Posato's window, which comes and goes while Posato stays. */
private class ResidentWindow(
    val state: WindowState,
    visible: Boolean,
) {
    var visible by mutableStateOf(visible)
    var composed: ComposeWindow? = null

    /** A minimized or buried window is still visible, so showing it also restores and raises it. */
    fun show() {
        visible = true
        state.isMinimized = false
        composed?.run {
            toFront()
            requestFocus()
        }
    }
}

@Composable
private fun rememberResidentWindow(visible: Boolean): ResidentWindow {
    val state = rememberWindowState(placement = WindowPlacement.Maximized, width = WINDOW_WIDTH.dp, height = WINDOW_HEIGHT.dp)
    return remember { ResidentWindow(state, visible) }
}

@Composable
private fun StatusIconEffect(
    host: LinuxResidentHost,
    menu: PresenceMenu,
) {
    val area = host.statusArea ?: return
    // The panel reads the menu when the icon registers, which waits for the first lines, so they are never the loading ones.
    if (menu.state == PresenceState.LOADING) return
    LaunchedEffect(menu) {
        while (true) {
            val model = presenceMenuModel(menu, host.copy.presence, System.currentTimeMillis(), ::formatClockTime)
            area.update(StatusAreaState(statusMenuEntries(model), "Posato", model.accessibilityDescription, model.filled))
            // Remaining minutes only change during a pause; an idle icon is never refreshed on a timer.
            if (menu.sessionEndEpochMillis == null) break
            delay(MENU_REFRESH_MILLIS)
        }
    }
}

@Composable
private fun NoticeWindow(
    notice: LinuxNotice,
    copy: LinuxPresenceCopy,
    onDismiss: () -> Unit,
    onQuit: () -> Unit,
) {
    val state = rememberDialogState(width = DIALOG_WIDTH.dp, height = DIALOG_HEIGHT.dp)
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "Posato",
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
            if (escape) onDismiss()
            escape
        },
    ) {
        when (notice) {
            LinuxNotice.QUIT -> ResidenceDialogContent(
                title = copy.presence.quitTitle,
                message = copy.quitSchedules,
                primary = copy.presence.quitKeep,
                onPrimary = onDismiss,
                secondary = copy.presence.quitConfirm,
                onSecondary = onQuit,
            )

            LinuxNotice.FIRST_CLOSE -> ResidenceDialogContent(null, copy.firstClose, copy.presence.firstCloseOk, onDismiss)

            LinuxNotice.FIRST_CLOSE_NO_ICON -> ResidenceDialogContent(null, copy.firstCloseNoIcon, copy.presence.firstCloseOk, onDismiss)
        }
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isQuitKey(): Boolean = type == KeyEventType.KeyDown && isCtrlPressed && key == Key.Q

private enum class CloseOutcome { HIDE, QUIT_ASKING, EXIT }

/**
 * Closing hides Posato only when it can be shown again: by its icon, or, for a pause or an enabled schedule, by a
 * second launch over the session bus. Without a session bus nothing could show it again, so closing quits as
 * **Quit Posato** does.
 */
private fun closeOutcome(
    sessionBus: Boolean,
    watcher: Boolean,
    menu: PresenceMenu,
): CloseOutcome = when {
    !sessionBus -> CloseOutcome.QUIT_ASKING
    watcher || keepsRunning(menu) -> CloseOutcome.HIDE
    else -> CloseOutcome.EXIT
}

private fun windowRequestOf(action: PresenceMenuActionId): SessionWindowRequest? = when (action) {
    PresenceMenuActionId.START_SESSION -> SessionWindowRequest.START_SESSION
    PresenceMenuActionId.END_SESSION_EARLY -> SessionWindowRequest.END_SESSION_EARLY
    PresenceMenuActionId.RESUME_RESTRICTIONS -> SessionWindowRequest.SESSION
    PresenceMenuActionId.OPEN_POSATO, PresenceMenuActionId.QUIT -> null
}

private fun hiddenWithNothingToDo(
    visible: Boolean,
    watcher: Boolean,
    menu: PresenceMenu,
): Boolean = !visible && !watcher && menu.state != PresenceState.LOADING && !keepsRunning(menu)

/** A pause runs or a schedule is on: the work a hidden Posato keeps doing. */
private fun keepsRunning(menu: PresenceMenu): Boolean =
    menu.state == PresenceState.ENFORCING || menu.state == PresenceState.NOT_ENFORCING || menu.schedulesEnabled

private fun formatClockTime(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val LOADING_MENU = PresenceMenu(PresenceState.LOADING, PresenceAction.OPEN_POSATO, null)
private const val MENU_REFRESH_MILLIS = 60_000L
private const val WATCHER_GRACE_MILLIS = 30_000L
private const val WINDOW_WIDTH = 1_100
private const val WINDOW_HEIGHT = 780
private const val DIALOG_WIDTH = 460
private const val DIALOG_HEIGHT = 220
