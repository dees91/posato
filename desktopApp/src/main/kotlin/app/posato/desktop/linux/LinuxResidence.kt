package app.posato.desktop.linux

import app.posato.feature.about.ApplicationResidence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path

/** Open at login and Quit for About Posato on Linux; quitting goes through the resident application's confirmation. */
internal class LinuxResidence(
    private val autostart: LinuxAutostart,
) : ApplicationResidence {
    private val enabled = MutableStateFlow(autostart.isEnabled())

    @Volatile var onQuit: () -> Unit = {}

    override val opensAtLogin: StateFlow<Boolean> = enabled

    override fun setOpensAtLogin(enabled: Boolean) {
        runCatching { autostart.setEnabled(enabled) }
        this.enabled.value = autostart.isEnabled()
    }

    override fun quit() = onQuit()
}

/** The one-time notice after the first close that keeps Posato running, remembered next to the database. */
internal class LinuxFirstCloseMarker(
    private val marker: Path,
) {
    fun firstTime(): Boolean {
        if (Files.exists(marker)) return false
        runCatching { Files.createFile(marker) }
        return true
    }
}
