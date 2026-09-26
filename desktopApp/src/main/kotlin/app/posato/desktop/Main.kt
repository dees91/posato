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
    MacPresenceNative.installLaunchProbe()
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
                loginItem = MacLoginItemState,
            )
            val applicationGraph = createDesktopApplicationGraph(applicationMappings, enforcement, helperState)
            val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val updater = createUpdaterController(enforcementClient, applicationGraph.updateMaintenance, instanceLock, updaterScope)
            runBlocking { updater.start() }
            val presence = applicationGraph.presence
            val presenceCopy = runBlocking { loadPresenceCopy() }
            val launchedAtLogin = MacPresenceNative.launchedAtLogin()

            application {
                ResidentPosato(applicationGraph, updater, presenceCopy, launchedAtLogin)
            }
        }
    }
}
