package app.posato.desktop.linux.status

import app.posato.desktop.PresenceMenuActionId

/**
 * Verification probe for the packaged runtime (LINUX-003 plan step 3): connects to the session bus, owns Posato's
 * name, exports the status item with a sample menu, and registers it, then stays up for the given seconds.
 * Run with `/opt/posato/lib/runtime/bin/java -cp /opt/posato/lib/app/posato.jar app.posato.desktop.linux.status.StatusAreaProbeKt 60`.
 */
fun main(arguments: Array<String>) {
    val area = LinuxStatusArea(
        icons = StatusIcons(LINUX_ICON_THEME_PATH),
        onShow = { token -> println("show $token") },
        onAction = { action -> println("action $action") },
        onMenuOpened = { println("menu opened") },
        onBusLost = { println("bus lost") },
    )
    val started = area.start()
    area.update(
        StatusAreaState(
            entries = listOf(
                StatusMenuEntry(1, "No session active", enabled = false, separator = false, action = null),
                StatusMenuEntry(100, "", enabled = false, separator = true, action = null),
                StatusMenuEntry(200, "Start a session…", enabled = true, separator = false, action = PresenceMenuActionId.START_SESSION),
                StatusMenuEntry(203, "Open Posato", enabled = true, separator = false, action = PresenceMenuActionId.OPEN_POSATO),
            ),
            title = "Posato",
            description = "Posato",
            filled = false,
        ),
    )
    println("started=$started watcher=${area.watcherPresent.value}")
    System.out.flush()
    Thread.sleep((arguments.firstOrNull()?.toLongOrNull() ?: DEFAULT_SECONDS) * MILLIS_PER_SECOND)
    area.close()
}

/** Where the package installs the symbolic icons (jpackage copies `linux/` into the application directory). */
internal const val LINUX_ICON_THEME_PATH = "/opt/posato/lib/app/icons"
private const val DEFAULT_SECONDS = 60L
private const val MILLIS_PER_SECOND = 1_000L
