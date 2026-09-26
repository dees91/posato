package app.posato.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import app.posato.ApplicationNavigation
import app.posato.desktop.update.MacUpdateSettings
import app.posato.desktop.update.MacUpdater
import app.posato.desktop.update.UpdaterController
import app.posato.di.DesktopApplicationComponents
import app.posato.feature.about.ApplicationUpdates
import app.posato.feature.presence.DesktopPresence
import app.posato.feature.presence.PresenceAction
import app.posato.feature.presence.PresenceCopy
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import app.posato.feature.presence.SessionWindowRequest
import app.posato.feature.presence.quitPromptFor
import app.posato.feature.update.loadUpdaterCopy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Dimension
import java.awt.desktop.AppReopenedListener
import java.awt.desktop.QuitResponse

@Stable
internal class ResidentWindow(
    initiallyVisible: Boolean
) {
    var visible by mutableStateOf(initiallyVisible)
        private set

    fun show() {
        visible = true
        MacPresenceNative.setMainWindowVisible(true)
    }

    fun hide() {
        visible = false
    }
}

@Composable
internal fun ApplicationScope.ResidentPosato(
    graph: DesktopApplicationComponents,
    updater: UpdaterController,
    copy: PresenceCopy,
    launchedAtLogin: Boolean,
) {
    val presence = graph.presence
    val window = remember { ResidentWindow(!launchedAtLogin) }
    val menu by presence.menu.collectAsState(LOADING_MENU)
    var powerOffEpochMillis by remember { mutableStateOf<Long?>(null) }
    var updates by remember { mutableStateOf<ApplicationUpdates?>(null) }
    val scope = rememberCoroutineScope()
    val confirmQuit: suspend () -> Boolean = { confirmsQuit(copy, quitPromptFor(menu, powerOffEpochMillis, System.currentTimeMillis())) }
    val quit: suspend () -> Unit = { if (confirmQuit()) exitApplication() }
    LaunchedEffect(Unit) { presence.runWhileResident() }
    LaunchedEffect(Unit) {
        MacPresenceNative.start(copy.closeWindow)
        if (MacUpdater.start(updater, loadUpdaterCopy())) updates = MacUpdateSettings
    }
    LaunchedEffect(window.visible) { MacPresenceNative.setMainWindowVisible(window.visible) }
    StatusMenuEffects(presence, copy, menu, window, onPowerOff = { powerOffEpochMillis = it }, onQuit = quit)
    ApplicationEventsEffect(window, onQuitRequest = { response ->
        scope.launch { if (confirmQuit()) response.performQuit() else response.cancelQuit() }
    })
    val navigation = remember { ApplicationNavigation() }
    val windowState = rememberWindowState(width = 1060.dp, height = 780.dp)
    if (window.visible) {
        PosatoWindow(graph, windowState, navigation, updates, onCloseRequest = {
            scope.launch {
                when (firstCloseChoice(menu, copy)) {
                    FirstCloseChoice.HIDE -> window.hide()
                    FirstCloseChoice.QUIT -> quit()
                }
            }
        })
    }
}

@Composable
private fun StatusMenuEffects(
    presence: DesktopPresence,
    copy: PresenceCopy,
    menu: PresenceMenu,
    window: ResidentWindow,
    onPowerOff: (Long) -> Unit,
    onQuit: suspend () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val latestQuit by rememberUpdatedState(onQuit)
    val latestPowerOff by rememberUpdatedState(onPowerOff)
    LaunchedEffect(menu, menuOpen) {
        do {
            MacPresenceNative.updateMenu(presenceMenuModel(menu, copy, System.currentTimeMillis(), ::formatClockTime))
            if (menuOpen) delay(MENU_REFRESH_MILLIS)
        } while (menuOpen)
    }
    LaunchedEffect(Unit) {
        MacPresenceEvents.menuEvents.collect { event ->
            when (event) {
                PresenceEvent.MENU_OPENED -> {
                    menuOpen = true
                    launch { presence.onMenuOpened() }
                }

                PresenceEvent.MENU_CLOSED -> {
                    menuOpen = false
                }

                PresenceEvent.POWER_OFF -> {
                    latestPowerOff(System.currentTimeMillis())
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        MacPresenceEvents.menuActions.collect { code ->
            val action = PresenceMenuActionId.of(code) ?: return@collect
            handleMenuAction(action, presence, window, latestQuit)
        }
    }
}

private suspend fun handleMenuAction(
    action: PresenceMenuActionId,
    presence: DesktopPresence,
    window: ResidentWindow,
    quit: suspend () -> Unit,
) {
    val request = when (action) {
        PresenceMenuActionId.START_SESSION -> SessionWindowRequest.START_SESSION
        PresenceMenuActionId.END_SESSION_EARLY -> SessionWindowRequest.END_SESSION_EARLY
        PresenceMenuActionId.RESUME_RESTRICTIONS -> SessionWindowRequest.SESSION
        PresenceMenuActionId.OPEN_POSATO -> null
        PresenceMenuActionId.QUIT -> return quit()
    }
    window.show()
    request?.let(presence::request)
}

@Composable
private fun ApplicationEventsEffect(
    window: ResidentWindow,
    onQuitRequest: (QuitResponse) -> Unit,
) {
    val latestQuitRequest by rememberUpdatedState(onQuitRequest)
    DisposableEffect(Unit) {
        val desktop = Desktop.getDesktop()
        desktop.setQuitHandler { _, response -> latestQuitRequest(response) }
        desktop.addAppEventListener(AppReopenedListener { window.show() })
        onDispose { desktop.setQuitHandler(null) }
    }
}

@Composable
private fun PosatoWindow(
    graph: DesktopApplicationComponents,
    state: WindowState,
    navigation: ApplicationNavigation,
    updates: ApplicationUpdates?,
    onCloseRequest: () -> Unit,
) {
    var highContrast by remember { mutableStateOf(false) }
    Window(onCloseRequest = onCloseRequest, title = "Posato", state = state) {
        SideEffect {
            this.window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            this.window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            this.window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            this.window.minimumSize = Dimension(MINIMUM_WINDOW_WIDTH, 0)
        }
        WindowChrome(this.window, state.placement == WindowPlacement.Fullscreen, onContrastChange = { highContrast = it })
        graph.application.Content(
            highContrast = highContrast,
            onAnnouncement = MacWindow::announce,
            updates = updates,
            hostsSession = false,
            windowRequests = graph.presence.windowRequests,
            navigation = navigation,
        )
    }
}

private val LOADING_MENU = PresenceMenu(PresenceState.LOADING, PresenceAction.OPEN_POSATO, null)
private const val MINIMUM_WINDOW_WIDTH: Int = 614
private const val MENU_REFRESH_MILLIS: Long = 60_000L
