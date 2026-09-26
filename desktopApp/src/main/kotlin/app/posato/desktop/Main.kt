package app.posato.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.posato.desktop.macos.DesktopMacHelperState
import app.posato.desktop.macos.MacOsApplicationEnforcer
import app.posato.desktop.macos.MacOsBrowserDomainEnforcer
import app.posato.desktop.macos.MacOsHelperClient
import app.posato.desktop.macos.MacOsHelperSigningVerifier
import app.posato.desktop.macos.MacOsSystemSettings
import app.posato.desktop.mappings.DesktopLocalApplicationMappings
import app.posato.desktop.session.MacOsApplicationEnforcementLink
import app.posato.desktop.session.MacOsBrowserEnforcementLink
import app.posato.desktop.update.MacUpdateSettings
import app.posato.desktop.update.MacUpdater
import app.posato.desktop.update.createUpdaterController
import app.posato.desktop.update.openInstanceLock
import app.posato.di.createDesktopApplicationGraph
import app.posato.feature.about.ApplicationUpdates
import app.posato.feature.enforcement.JvmSessionEnforcement
import app.posato.feature.presence.PresenceAction
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import app.posato.feature.presence.QuitPrompt
import app.posato.feature.presence.SessionWindowRequest
import app.posato.feature.presence.loadPresenceCopy
import app.posato.feature.presence.quitPromptFor
import app.posato.feature.update.loadUpdaterCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import java.awt.Dimension
import java.awt.desktop.AppReopenedListener
import kotlin.system.exitProcess

fun main() {
    MacPresence.installLaunchProbe()
    val instanceLock = openInstanceLock()
    if (!instanceLock.acquireShared()) {
        exitProcess(0)
    }
    MacOsHelperClient().use { enforcementClient ->
        DesktopLocalApplicationMappings().use { applicationMappings ->
            Runtime.getRuntime().addShutdownHook(Thread(applicationMappings::close, "application-mappings-shutdown"))
            val enforcement = JvmSessionEnforcement(
                MacOsBrowserEnforcementLink(MacOsBrowserDomainEnforcer(enforcementClient), enforcementClient),
                MacOsApplicationEnforcementLink(
                    MacOsApplicationEnforcer(enforcementClient, applicationMappings::designatedRequirements),
                    applicationMappings,
                    enforcementClient,
                ),
            )
            val helperState = DesktopMacHelperState(
                commands = enforcementClient,
                verifyHelper = {
                    MacOsHelperSigningVerifier.verify(MacOsHelperSigningVerifier.installedHelperPath())
                },
                ioDispatcher = Dispatchers.IO,
                openSettings = MacOsSystemSettings::open,
            )
            val applicationGraph = createDesktopApplicationGraph(applicationMappings, enforcement, helperState)
            val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val updater = createUpdaterController(enforcementClient, applicationGraph.updateMaintenance, instanceLock, updaterScope)
            runBlocking { updater.start() }
            val presence = applicationGraph.presence
            val presenceCopy = runBlocking { loadPresenceCopy() }
            val launchedAtLogin = MacPresence.launchedAtLogin()

            application {
                var highContrast by remember { mutableStateOf(false) }
                var updates by remember { mutableStateOf<ApplicationUpdates?>(null) }
                var windowVisible by remember { mutableStateOf(!launchedAtLogin) }
                var menuOpen by remember { mutableStateOf(false) }
                var powerOffEpochMillis by remember { mutableStateOf<Long?>(null) }
                val menu by presence.menu.collectAsState(PresenceMenu(PresenceState.LOADING, PresenceAction.OPEN_POSATO, null))
                val scope = rememberCoroutineScope()
                val showWindow = { windowVisible = true }
                val confirmQuit: suspend () -> Boolean = {
                    when (quitPromptFor(menu, powerOffEpochMillis, System.currentTimeMillis())) {
                        QuitPrompt.NONE -> true
                        QuitPrompt.CONFIRM_ENFORCING -> confirmsQuit(presenceCopy, presenceCopy.quitEnforcing)
                        QuitPrompt.CONFIRM_NOT_ENFORCING -> confirmsQuit(presenceCopy, presenceCopy.quitNotEnforcing)
                    }
                }
                LaunchedEffect(Unit) { presence.runWhileResident() }
                LaunchedEffect(Unit) {
                    MacPresence.start(presenceCopy.closeWindow)
                    if (MacUpdater.start(updater, loadUpdaterCopy())) updates = MacUpdateSettings
                }
                LaunchedEffect(windowVisible) { MacPresence.setMainWindowVisible(windowVisible) }
                LaunchedEffect(menu, menuOpen) {
                    do {
                        val model = presenceMenuModel(menu, presenceCopy, System.currentTimeMillis(), ::formatClockTime)
                        MacPresence.updateMenu(model.items, model.accessibilityDescription, model.filled)
                        if (menuOpen) delay(MENU_REFRESH_MILLIS)
                    } while (menuOpen)
                }
                LaunchedEffect(Unit) {
                    MacPresence.menuEvents.collect { event ->
                        when (event) {
                            PresenceEvent.MENU_OPENED -> {
                                menuOpen = true
                                presence.onMenuOpened()
                            }

                            PresenceEvent.MENU_CLOSED -> {
                                menuOpen = false
                            }

                            PresenceEvent.POWER_OFF -> {
                                powerOffEpochMillis = System.currentTimeMillis()
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    MacPresence.menuActions.collect { code ->
                        when (PresenceMenuActionId.of(code) ?: return@collect) {
                            PresenceMenuActionId.START_SESSION -> {
                                showWindow()
                                presence.request(SessionWindowRequest.START_SESSION)
                            }

                            PresenceMenuActionId.END_SESSION_EARLY -> {
                                showWindow()
                                presence.request(SessionWindowRequest.END_SESSION_EARLY)
                            }

                            PresenceMenuActionId.RESUME_RESTRICTIONS -> {
                                showWindow()
                                presence.request(SessionWindowRequest.SESSION)
                            }

                            PresenceMenuActionId.OPEN_POSATO -> {
                                showWindow()
                            }

                            PresenceMenuActionId.QUIT -> {
                                if (confirmQuit()) exitApplication()
                            }
                        }
                    }
                }
                DisposableEffect(Unit) {
                    val desktop = Desktop.getDesktop()
                    desktop.setQuitHandler { _, response ->
                        scope.launch { if (confirmQuit()) response.performQuit() else response.cancelQuit() }
                    }
                    desktop.addAppEventListener(AppReopenedListener { showWindow() })
                    onDispose { desktop.setQuitHandler(null) }
                }
                val state = rememberWindowState(width = 1060.dp, height = 780.dp)
                Window(
                    onCloseRequest = {
                        scope.launch {
                            when (firstCloseChoice(menu, presenceCopy)) {
                                FirstCloseChoice.HIDE -> windowVisible = false
                                FirstCloseChoice.QUIT -> if (confirmQuit()) exitApplication()
                            }
                        }
                    },
                    visible = windowVisible,
                    title = "Posato",
                    state = state,
                ) {
                    SideEffect {
                        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                        window.minimumSize = Dimension(MINIMUM_WINDOW_WIDTH, 0)
                    }
                    WindowChrome(window, state.placement == WindowPlacement.Fullscreen, onContrastChange = { highContrast = it })
                    applicationGraph.application.Content(
                        highContrast = highContrast,
                        onAnnouncement = MacWindow::announce,
                        updates = updates,
                        hostsSession = false,
                        visible = windowVisible,
                        windowRequests = presence.windowRequests,
                    )
                }
            }
        }
    }
}

private const val MINIMUM_WINDOW_WIDTH: Int = 614
private const val MENU_REFRESH_MILLIS: Long = 60_000L
