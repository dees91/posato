package app.posato.windows.helper

import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.Winsvc

/**
 * The installer's three steps, run by the MSI as the system. `install` records the installing user and sets up and
 * starts the service; `uninstall` stops and deletes the service, which removes every filter, and keeps the state so a
 * major upgrade keeps a running pause; `purge`, on a full removal only, deletes the state, the folder, and the user's
 * start-at-login value when that user's registry hive is loaded.
 */
internal class Installer(
    private val folder: HelperFolder = HelperFolder(),
) {
    fun install(userSid: String): Int {
        val config = recordedUser() ?: newUser(userSid) ?: return USER_REFUSED
        if (!folder.prepare()) return FOLDER_FAILED
        if (folder.read(folder.config)?.let(HelperConfig::decode) != config && !folder.write(folder.config, config.encode())) return FOLDER_FAILED
        // A state file that is not the system's own is never trusted; the next pause writes a new one.
        if (folder.read(folder.state) == null) folder.delete(folder.state)
        val executable = System.getProperty("jpackage.app-path") ?: ProcessHandle.current().info().command().orElse(null) ?: return SERVICE_FAILED
        return if (ServiceRegistration.createAndStart("\"$executable\" serve")) 0 else SERVICE_FAILED
    }

    fun uninstall(): Int {
        val manager = Advapi32.INSTANCE.OpenSCManager(null, null, Winsvc.SC_MANAGER_CONNECT) ?: return SERVICE_FAILED
        try {
            val service = Advapi32.INSTANCE.OpenService(manager, ServiceHost.SERVICE_NAME, SERVICE_STOP_QUERY_DELETE)
                ?: return if (Native.getLastError() == Win32.ERROR_SERVICE_DOES_NOT_EXIST) 0 else SERVICE_FAILED
            try {
                ServiceRegistration.stop(service)
                return if (Advapi32.INSTANCE.DeleteService(service)) 0 else SERVICE_FAILED
            } finally {
                Advapi32.INSTANCE.CloseServiceHandle(service)
            }
        } finally {
            Advapi32.INSTANCE.CloseServiceHandle(manager)
        }
    }

    fun purge(): Int {
        val config = folder.read(folder.config)?.let(HelperConfig::decode)
        folder.purge()
        config?.let { removeStartAtLogin(it.sid) }
        return 0
    }

    /** A repair or an upgrade keeps the user recorded at the first installation. */
    private fun recordedUser(): HelperConfig? {
        return folder.read(folder.config)?.let(HelperConfig::decode)
    }

    /**
     * The installing account from Windows Installer's `UserSID`, which names local, domain, and Microsoft Entra
     * accounts alike; its name is kept for people reading the configuration, and the SID itself when the name cannot
     * be looked up.
     */
    private fun newUser(userSid: String): HelperConfig? {
        if (!InstallingUser.isPerson(userSid)) return null
        val name = try {
            Advapi32Util.getAccountBySid(userSid).fqn
        } catch (_: Win32Exception) {
            null
        }
        return HelperConfig(name?.takeIf(InstallingUser::isAccountName) ?: userSid, userSid)
    }

    private fun removeStartAtLogin(sid: String) {
        val key = "$sid\\$RUN_KEY"
        runCatching {
            if (Advapi32Util.registryKeyExists(WinReg.HKEY_USERS, sid) && Advapi32Util.registryValueExists(WinReg.HKEY_USERS, key, RUN_VALUE)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_USERS, key, RUN_VALUE)
            }
        }
    }

    companion object {
        const val USER_REFUSED = 10
        const val FOLDER_FAILED = 11
        const val SERVICE_FAILED = 12
        const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val RUN_VALUE = "Posato"
        private const val SERVICE_STOP_QUERY_DELETE = 0x0020 or 0x0004 or 0x10000
    }
}
