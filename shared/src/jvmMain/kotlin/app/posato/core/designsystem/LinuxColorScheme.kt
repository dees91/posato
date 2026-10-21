package app.posato.core.designsystem

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit

/**
 * The desktop's light or dark choice on Linux, which Compose cannot read there: GNOME keeps it as
 * `org.gnome.desktop.interface color-scheme`, which Ubuntu's Appearance settings write. Posato reads it once and
 * again whenever dconf rewrites the user's settings database, so it changes with the desktop while it runs. The
 * file is watched rather than the session bus because Posato may run on a session bus of its own. The first read
 * happens once, when the theme is first drawn, so the window opens in the right palette instead of flashing.
 * Desktops that do not keep this GNOME key, such as KDE, get the light palette; reading the XDG desktop portal's
 * appearance setting instead is a later idea.
 */
internal object LinuxColorScheme {
    private val dark = MutableStateFlow(readPrefersDark())

    val prefersDark: StateFlow<Boolean> by lazy {
        Thread(::watch, "posato-color-scheme").apply { isDaemon = true }.start()
        dark.asStateFlow()
    }

    private fun watch() {
        val directory = dconfDirectory() ?: return
        try {
            while (true) {
                // A new account has no dconf folder until the first setting is written, so wait for it to appear.
                while (!Files.isDirectory(directory)) {
                    Thread.sleep(FOLDER_POLL_MILLIS)
                }
                watchFolder(directory)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Reads the scheme once the folder is watched, so a change in between is not missed, and again after every
     * change in the dconf folder, until the folder goes away.
     */
    private fun watchFolder(directory: Path) {
        try {
            FileSystems.getDefault().newWatchService().use { service ->
                directory.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
                dark.value = readPrefersDark()
                do {
                    val key = service.take()
                    key.pollEvents()
                    dark.value = readPrefersDark()
                } while (key.reset())
            }
        } catch (_: IOException) {
            Thread.sleep(FOLDER_POLL_MILLIS)
        }
    }

    private fun dconfDirectory(): Path? {
        val config = System.getenv("XDG_CONFIG_HOME")?.takeIf(String::isNotBlank)
            ?: System.getProperty("user.home")?.let { home -> "$home/.config" }
        return config?.let { base -> Paths.get(base, "dconf") }
    }

    private fun readPrefersDark(): Boolean {
        return try {
            val process = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.inputStream.close()
                process.destroyForcibly().waitFor()
                return false
            }
            val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
            process.exitValue() == 0 && prefersDark(output)
        } catch (_: IOException) {
            false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private const val READ_TIMEOUT_SECONDS = 2L
    private const val FOLDER_POLL_MILLIS = 5_000L
}

/** Whether `gsettings get … color-scheme` output asks for dark; anything else keeps the light palette. */
internal fun prefersDark(gsettingsOutput: String): Boolean {
    return gsettingsOutput.trim() == "'prefer-dark'"
}
