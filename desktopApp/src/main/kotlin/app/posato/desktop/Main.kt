package app.posato.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.posato.feature.update.loadUpdaterCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import kotlin.system.exitProcess

fun main() {
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

            application {
                var highContrast by remember { mutableStateOf(false) }
                var updates by remember { mutableStateOf<ApplicationUpdates?>(null) }
                val state = rememberWindowState(width = 1060.dp, height = 780.dp)
                Window(
                    onCloseRequest = ::exitApplication,
                    title = "Posato",
                    state = state,
                ) {
                    SideEffect {
                        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                        window.minimumSize = Dimension(MINIMUM_WINDOW_WIDTH, 0)
                    }
                    LaunchedEffect(Unit) {
                        if (MacUpdater.start(updater, loadUpdaterCopy())) updates = MacUpdateSettings
                    }
                    WindowChrome(window, state.placement == WindowPlacement.Fullscreen, onContrastChange = { highContrast = it })
                    applicationGraph.application.Content(
                        highContrast = highContrast,
                        onAnnouncement = MacWindow::announce,
                        updates = updates,
                    )
                }
            }
        }
    }
}

private const val MINIMUM_WINDOW_WIDTH: Int = 614
