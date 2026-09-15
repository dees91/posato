package app.posato.desktop.macos

import java.io.IOException
import java.net.URI

internal object MacOsSystemSettings {
    private const val SETTINGS_SCHEME = "x-apple.systempreferences"
    private const val OPEN_EXECUTABLE = "/usr/bin/open"

    fun openCommand(uri: URI): List<String> {
        if (uri.scheme != SETTINGS_SCHEME) {
            throw IOException("Only System Settings links can be opened.")
        }
        return listOf(OPEN_EXECUTABLE, uri.toString())
    }

    fun open(uri: URI) {
        ProcessBuilder(openCommand(uri)).start()
    }
}
