package app.posato.desktop.linux

import androidx.compose.ui.window.application
import app.posato.desktop.linux.status.LINUX_ICON_THEME_PATH
import app.posato.desktop.linux.status.LinuxStatusArea
import app.posato.desktop.linux.status.StatusIcons
import app.posato.desktop.resident.DesktopResidence
import app.posato.desktop.resident.FirstCloseMarker
import app.posato.desktop.resident.InstanceLock
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
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Posato for Linux (ADR 0010): the shared interface in one window, the pausing service reached over its socket, app
 * choices from desktop entries, and the folder workspace for synchronization. One Posato runs per user (LINUX-003);
 * closing the window keeps it running with its status icon, so schedules start, and `--background` starts it
 * without a window, as a login launch does.
 */
fun main(args: Array<String>) {
    val data = dataDirectory()
    val background = BACKGROUND_ARGUMENT in args
    val token = System.getenv("XDG_ACTIVATION_TOKEN") ?: System.getenv("DESKTOP_STARTUP_ID").orEmpty()
    val lock = InstanceLock(data.resolve("instance.lock"))
    when (residentStartup(lock) { if (background) true else forwardShowWithRetry(token) }) {
        ResidentStartup.RUN -> Unit
        ResidentStartup.FORWARDED, ResidentStartup.EXIT -> exitProcess(0)
    }
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
    mappings.keptTargets = graph.keptApplicationRequirements::of
    val copy = runBlocking { loadLinuxPresenceCopy() }
    val residence = DesktopResidence(LinuxAutostart(linuxAutostartDirectory(System.getenv(), System.getProperty("user.home"))))
    val events = LinuxHostEvents()
    val statusArea = LinuxStatusArea(
        icons = StatusIcons(LINUX_ICON_THEME_PATH),
        onShow = { events.show() },
        onAction = events::act,
        onMenuOpened = events::openMenu,
        // The session ended under Posato: the desktop is logging out.
        onBusLost = { exitProcess(0) },
    ).takeIf { it.start() }
    val status = statusArea?.let { LinuxStatusSurface(it, events) }
    val host = ResidentHost(graph, copy, status, residence, FirstCloseMarker(data.resolve("first-close")), background)
    // The lock and the bus connection end with the process, which application {} exits.
    application {
        ResidentPosato(host)
    }
    // A collected lock's channel is closed by its cleaner, which would let a second Posato start.
    Reference.reachabilityFence(lock)
}

/** A second launch asks the running Posato to show its window, retrying while that Posato is still starting. */
private fun forwardShowWithRetry(token: String): Boolean {
    repeat(FORWARD_ATTEMPTS) { attempt ->
        if (LinuxStatusArea.forwardShow(token)) return true
        if (attempt < FORWARD_ATTEMPTS - 1) Thread.sleep(FORWARD_RETRY_MILLIS)
    }
    return false
}

private fun dataDirectory(): Path {
    val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.startsWith("/") } ?: (System.getProperty("user.home") + "/.local/share")
    return Paths.get(base, "Posato").also { it.toFile().mkdirs() }
}

private const val FORWARD_ATTEMPTS = 3
private const val FORWARD_RETRY_MILLIS = 500L
