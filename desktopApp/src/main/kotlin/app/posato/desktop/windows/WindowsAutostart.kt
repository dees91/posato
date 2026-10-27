package app.posato.desktop.windows

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Path

/**
 * **Open Posato at login** on Windows: the value `Posato` under the user's `Run` key starts [launcher] in the
 * background, written only on the person's choice. Removing the package removes the value when the user's registry is
 * loaded; a value left behind is inert because its launcher is gone.
 */
internal class WindowsAutostart(
    private val launcher: Path,
) {
    fun isEnabled(): Boolean {
        return try {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME) &&
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME).isNotBlank()
        } catch (_: Win32Exception) {
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME, "\"$launcher\" $BACKGROUND")
        } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
        }
    }

    private companion object {
        const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE_NAME = "Posato"
        const val BACKGROUND = "--background"
    }
}
