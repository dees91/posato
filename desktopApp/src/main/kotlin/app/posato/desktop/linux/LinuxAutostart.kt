package app.posato.desktop.linux

import app.posato.desktop.resident.LoginLaunch
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * **Open Posato at login** on Linux (LINUX-003): an XDG autostart entry in the user's configuration, written only on
 * the person's choice. The entry starts Posato in the background; `TryExec` makes an entry left after the package is
 * removed inert. An entry that GNOME Tweaks turned off (`Hidden=true` or `X-GNOME-Autostart-enabled=false`) reads as
 * off, and turning the switch on writes a fresh entry.
 */
internal class LinuxAutostart(
    private val directory: Path,
) : LoginLaunch {
    private val entry: Path = directory.resolve(ENTRY_NAME)

    override fun isEnabled(): Boolean {
        if (!entry.exists()) return false
        val lines = runCatching { entry.readLines() }.getOrDefault(emptyList()).map(String::trim)
        return HIDDEN !in lines && GNOME_DISABLED !in lines
    }

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            entry.deleteIfExists()
            return
        }
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".posato", ".tmp")
        try {
            Files.writeString(temporary, ENTRY)
            Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.deleteIfExists()
        }
    }

    private companion object {
        const val ENTRY_NAME = "posato.desktop"
        const val HIDDEN = "Hidden=true"
        const val GNOME_DISABLED = "X-GNOME-Autostart-enabled=false"
        const val ENTRY = "[Desktop Entry]\n" +
            "Type=Application\n" +
            "Name=Posato\n" +
            "Comment=Starts Posato in the background so schedules start on time.\n" +
            "Icon=$LINUX_ICON\n" +
            "Exec=$LINUX_LAUNCHER $BACKGROUND_ARGUMENT\n" +
            "TryExec=$LINUX_LAUNCHER\n" +
            "Terminal=false\n" +
            "NoDisplay=true\n"
    }
}

/** `$XDG_CONFIG_HOME/autostart` when it is absolute, as the XDG specification says, else `~/.config/autostart`. */
internal fun linuxAutostartDirectory(
    environment: Map<String, String>,
    home: String,
): Path {
    val config = environment["XDG_CONFIG_HOME"]?.takeIf { it.startsWith("/") } ?: "$home/.config"
    return Paths.get(config, "autostart")
}

/** The launcher jpackage installs, and the argument that starts Posato without its window. */
internal const val LINUX_LAUNCHER = "/opt/posato/bin/posato"
internal const val LINUX_ICON = "/opt/posato/lib/posato.png"
internal const val BACKGROUND_ARGUMENT = "--background"
