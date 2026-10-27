package app.posato.desktop.resident

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import app.posato.feature.presence.DesktopPresence
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import app.posato.feature.presence.ResidenceDialogContent
import app.posato.feature.presence.ResidentPresenceCopy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

private enum class ResidentNotice { QUIT, FIRST_CLOSE, FIRST_CLOSE_NO_ICON }

/**
 * Posato as a resident application on a host that draws its own window (Linux, LINUX-003): the session host and
 * schedules run at application level, the window comes and goes, the status icon mirrors the Mac menu, and quitting
 * asks only while a schedule is on.
 */
@Composable
internal fun ApplicationScope.ResidentPosato(host: ResidentHost) {
    val presence = host.graph.presence
    val status = host.status
    val menu by presence.menu.collectAsState(LOADING_MENU)
    val iconShown by (status?.iconShown ?: remember { MutableStateFlow(false) }).collectAsState()
    val resident = rememberResidentWindow(!host.startHidden)
    var notice by remember { mutableStateOf<ResidentNotice?>(null) }
    val quit: () -> Unit = { if (menu.schedulesEnabled) notice = ResidentNotice.QUIT else exitApplication() }
    host.residence.onQuit = quit

    LaunchedEffect(Unit) { presence.runWhileResident() }
    status?.let { StatusSurfaceEffects(it, host.copy, menu, presence, resident, quit) }
    // Without a status icon, a hidden Posato stays only for a pause or an enabled schedule; a login launch waits for the
    // panel to come up first, and a panel that goes away is followed live.
    LaunchedEffect(resident.visible, iconShown, menu) {
        if (!hiddenWithNothingToDo(resident.visible, iconShown, menu)) return@LaunchedEffect
        delay(ICON_GRACE_MILLIS)
        exitApplication()
    }

    val navigation = remember { ApplicationNavigation() }
    if (resident.visible) {
        Window(
            onCloseRequest = {
                when (closeOutcome(status != null, iconShown, menu)) {
                    CloseOutcome.HIDE -> {
                        resident.visible = false
                        if (host.firstClose.firstTime()) notice = if (iconShown) ResidentNotice.FIRST_CLOSE else ResidentNotice.FIRST_CLOSE_NO_ICON
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
private fun StatusSurfaceEffects(
    status: StatusSurface,
    copy: ResidentPresenceCopy,
    menu: PresenceMenu,
    presence: DesktopPresence,
    resident: ResidentWindow,
    quit: () -> Unit,
) {
    val latestQuit by rememberUpdatedState(quit)
    LaunchedEffect(Unit) { status.showRequests.collect { resident.show() } }
    LaunchedEffect(Unit) { status.menuOpened.collect { presence.onMenuOpened() } }
    LaunchedEffect(Unit) {
        status.actions.collect { action ->
            if (action == PresenceMenuActionId.QUIT) return@collect latestQuit()
            resident.show()
            windowRequestOf(action)?.let(presence::request)
        }
    }
    // A panel may read the menu when the icon registers, which waits for the first lines, so they are never the loading ones.
    if (menu.state == PresenceState.LOADING) return
    // Remaining minutes only change during a pause; an idle icon is never refreshed on a timer.
    StatusMenuRefreshEffect(menu, copy.presence, ticking = menu.sessionEndEpochMillis != null, publish = status::update)
}

@Composable
private fun NoticeWindow(
    notice: ResidentNotice,
    copy: ResidentPresenceCopy,
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
            ResidentNotice.QUIT -> ResidenceDialogContent(
                title = copy.presence.quitTitle,
                message = copy.quitSchedules,
                primary = copy.presence.quitKeep,
                onPrimary = onDismiss,
                secondary = copy.presence.quitConfirm,
                onSecondary = onQuit,
            )

            ResidentNotice.FIRST_CLOSE -> ResidenceDialogContent(null, copy.firstClose, copy.presence.firstCloseOk, onDismiss)

            ResidentNotice.FIRST_CLOSE_NO_ICON -> ResidenceDialogContent(null, copy.firstCloseNoIcon, copy.presence.firstCloseOk, onDismiss)
        }
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isQuitKey(): Boolean = type == KeyEventType.KeyDown && isCtrlPressed && key == Key.Q

private const val ICON_GRACE_MILLIS = 30_000L
private const val WINDOW_WIDTH = 1_100
private const val WINDOW_HEIGHT = 780
private const val DIALOG_WIDTH = 460
private const val DIALOG_HEIGHT = 220
