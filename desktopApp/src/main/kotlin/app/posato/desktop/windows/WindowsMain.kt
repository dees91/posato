package app.posato.desktop.windows

import androidx.compose.ui.window.application
import app.posato.desktop.resident.DesktopResidence
import app.posato.desktop.resident.FirstCloseMarker
import app.posato.desktop.resident.InstanceLock
import app.posato.desktop.resident.LoginLaunch
import app.posato.desktop.resident.ResidentHost
import app.posato.desktop.resident.ResidentPosato
import app.posato.desktop.resident.ResidentStartup
import app.posato.desktop.resident.residentStartup
import app.posato.di.createDesktopApplicationGraph
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.unavailableMacHelper
import app.posato.feature.presence.loadLinuxPresenceCopy
import app.posato.feature.targets.data.CatalogApplicationMappings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.lang.ref.Reference
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Posato for Windows (ADR 0010): the shared interface in one window, the pausing service that the MSI installed
 * reached over its pipe, app choices from the Start menu, and the folder workspace for synchronization. One Posato
 * runs per user; closing the window keeps it running with its icon in the notification area, so schedules start, and
 * `--background` starts it without a window, as the login launch does.
 */
fun main(args: Array<String>) {
    val data = dataDirectory()
    val background = BACKGROUND_ARGUMENT in args
    val showSocket = data.resolve(SHOW_SOCKET)
    val lock = InstanceLock(data.resolve(INSTANCE_LOCK))
    when (residentStartup(lock) { if (background) true else forwardShowWithRetry(showSocket) }) {
        ResidentStartup.RUN -> Unit
        ResidentStartup.FORWARDED, ResidentStartup.EXIT -> exitProcess(0)
    }
    val client = WindowsHelperClient()
    val mappings = CatalogApplicationMappings(data.resolve("application-choices"), StartMenuCatalog(), Dispatchers.IO)
    val graph = createDesktopApplicationGraph(
        applicationMappings = mappings,
        enforcement = WindowsSessionEnforcement(client, mappings),
        macHelper = unavailableMacHelper(),
        databasePath = data.resolve("posato-policy.db").toString(),
        platform = OnboardingPermissionPlatform.WINDOWS,
        applicationAccess = WindowsApplicationAccess(client),
    )
    mappings.keptTargets = graph.keptApplicationRequirements::of
    val copy = runBlocking { loadLinuxPresenceCopy() }
    val launcher = System.getProperty(LAUNCHER_PROPERTY)?.let(Paths::get)
    val residence = DesktopResidence(launcher?.let(::WindowsAutostart) ?: NoLoginLaunch)
    // Without a notification area the tray reads as no icon, and closing the window follows the no-icon rules; a
    // second launch still shows the window. The socket closes with the process, which application {} exits.
    val tray = WindowsTray().apply { start() }
    ShowRequests(showSocket).listen(onShow = tray::show)
    val host = ResidentHost(graph, copy, tray, residence, FirstCloseMarker(data.resolve("first-close")), background)
    application {
        ResidentPosato(host)
    }
    // A collected lock's channel is closed by its cleaner, which would let a second Posato start.
    Reference.reachabilityFence(lock)
}

/** A second launch asks the running Posato to show its window, retrying while that Posato is still starting. */
private fun forwardShowWithRetry(socket: Path): Boolean {
    repeat(FORWARD_ATTEMPTS) { attempt ->
        if (ShowRequests.forward(socket)) return true
        if (attempt < FORWARD_ATTEMPTS - 1) Thread.sleep(FORWARD_RETRY_MILLIS)
    }
    return false
}

/** `%LOCALAPPDATA%\Posato`: the database, the app choices, and the instance lock, all the user's own. */
private fun dataDirectory(): Path {
    val base = System.getenv("LOCALAPPDATA")?.let(Paths::get)?.takeIf { it.isAbsolute }
        ?: Paths.get(System.getProperty("user.home"), "AppData", "Local")
    return base.resolve("Posato").also(Files::createDirectories)
}

/** Run outside the installed package, there is no launcher to start at login. */
private object NoLoginLaunch : LoginLaunch {
    override fun isEnabled(): Boolean = false

    override fun setEnabled(enabled: Boolean) = Unit
}

/** The installed `Posato.exe`, which jpackage's launcher names in this property. */
private const val LAUNCHER_PROPERTY = "jpackage.app-path"
private const val BACKGROUND_ARGUMENT = "--background"
private const val INSTANCE_LOCK = "instance.lock"
private const val SHOW_SOCKET = "show.sock"
private const val FORWARD_ATTEMPTS = 3
private const val FORWARD_RETRY_MILLIS = 500L
