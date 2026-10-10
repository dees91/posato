package app.posato.desktop.linux

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.posato.di.createDesktopApplicationGraph
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.unavailableMacHelper
import app.posato.feature.targets.data.CatalogApplicationMappings
import kotlinx.coroutines.Dispatchers
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Posato for Linux (ADR 0010): the shared interface in one window, the pausing service reached over its socket, app
 * choices from desktop entries, and the folder workspace for synchronization. Closing the window quits; a running
 * pause stays enforced by the service until its end.
 */
fun main() {
    val data = dataDirectory()
    val client = LinuxHelperClient()
    val installer = LinuxHelperInstaller(client)
    val mappings = CatalogApplicationMappings(data.resolve("application-choices"), DesktopEntryCatalog(), Dispatchers.IO)
    val graph = createDesktopApplicationGraph(
        applicationMappings = mappings,
        enforcement = LinuxSessionEnforcement(client, installer, mappings),
        macHelper = unavailableMacHelper(),
        databasePath = data.resolve("posato-policy.db").toString(),
        platform = OnboardingPermissionPlatform.LINUX,
        applicationAccess = LinuxApplicationAccess(installer),
    )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Posato",
            state = rememberWindowState(width = WINDOW_WIDTH.dp, height = WINDOW_HEIGHT.dp),
        ) {
            graph.application.Content()
        }
    }
}

private fun dataDirectory(): Path {
    val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.startsWith("/") } ?: (System.getProperty("user.home") + "/.local/share")
    return Paths.get(base, "Posato").also { it.toFile().mkdirs() }
}

private const val WINDOW_WIDTH = 1_100
private const val WINDOW_HEIGHT = 780
