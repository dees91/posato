package app.posato.windows.helper

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Winsvc
import java.lang.ref.Reference

/**
 * The service's entry in the Service Control Manager: `LocalSystem`, started automatically, restarted after a failure,
 * with only the privileges it needs, and its binary path quoted.
 */
internal object ServiceRegistration {
    fun createAndStart(binaryPath: String): Boolean {
        val manager = Advapi32.INSTANCE.OpenSCManager(null, null, Winsvc.SC_MANAGER_ALL_ACCESS) ?: return false
        try {
            val service = create(manager, binaryPath) ?: return false
            try {
                return configure(service) && start(service)
            } finally {
                Advapi32.INSTANCE.CloseServiceHandle(service)
            }
        } finally {
            Advapi32.INSTANCE.CloseServiceHandle(manager)
        }
    }

    private fun create(
        manager: Winsvc.SC_HANDLE,
        binaryPath: String,
    ): Winsvc.SC_HANDLE? {
        return Advapi32.INSTANCE.CreateService(
            manager,
            ServiceHost.SERVICE_NAME,
            DISPLAY_NAME,
            Winsvc.SERVICE_ALL_ACCESS,
            SERVICE_WIN32_OWN_PROCESS,
            SERVICE_AUTO_START,
            SERVICE_ERROR_NORMAL,
            binaryPath,
            null,
            null,
            null,
            null,
            null,
        ) ?: if (Native.getLastError() == Win32.ERROR_SERVICE_EXISTS) updateExisting(manager, binaryPath) else null
    }

    /** A repair or an upgrade finds the service in place and points it at this binary again. */
    private fun updateExisting(
        manager: Winsvc.SC_HANDLE,
        binaryPath: String,
    ): Winsvc.SC_HANDLE? {
        val existing = Advapi32.INSTANCE.OpenService(manager, ServiceHost.SERVICE_NAME, Winsvc.SERVICE_ALL_ACCESS) ?: return null
        val updated = Advapi32Extra.INSTANCE.changeServiceConfigW(
            existing,
            SERVICE_WIN32_OWN_PROCESS,
            SERVICE_AUTO_START,
            SERVICE_ERROR_NORMAL,
            WString(binaryPath),
            null,
            null,
            null,
            WString(LOCAL_SYSTEM),
            null,
            WString(DISPLAY_NAME),
        )
        if (updated) return existing
        Advapi32.INSTANCE.CloseServiceHandle(existing)
        return null
    }

    /** Restarts after a failure, runs with only the privileges it needs, and says what it is. */
    private fun configure(service: Winsvc.SC_HANDLE): Boolean {
        val description = Winsvc.SERVICE_DESCRIPTION().apply { lpDescription = serviceDescription() }
        val actions = Winsvc.SC_ACTION.ByReference().toArray(RESTART_DELAYS.size).map { it as Winsvc.SC_ACTION }
        actions.forEachIndexed { index, action ->
            action.type = SC_ACTION_RESTART
            action.delay = RESTART_DELAYS[index]
        }
        val failure = Winsvc.SERVICE_FAILURE_ACTIONS().apply {
            dwResetPeriod = RESET_PERIOD_SECONDS
            cActions = actions.size
            lpsaActions = actions[0] as Winsvc.SC_ACTION.ByReference
        }
        val failureOnExit = Winsvc.SERVICE_FAILURE_ACTIONS_FLAG().apply { fFailureActionsOnNonCrashFailures = 1 }
        val privileges = multiString(REQUIRED_PRIVILEGES)
        val required = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, privileges) }
        val advapi = Advapi32.INSTANCE
        return advapi.ChangeServiceConfig2(service, SERVICE_CONFIG_DESCRIPTION, description) &&
            advapi.ChangeServiceConfig2(service, SERVICE_CONFIG_FAILURE_ACTIONS, failure) &&
            advapi.ChangeServiceConfig2(service, SERVICE_CONFIG_FAILURE_ACTIONS_FLAG, failureOnExit) &&
            Advapi32Extra.INSTANCE.changeServiceConfig2W(service, SERVICE_CONFIG_REQUIRED_PRIVILEGES_INFO, required).also {
                Reference.reachabilityFence(privileges)
                Reference.reachabilityFence(actions)
            }
    }

    private fun start(service: Winsvc.SC_HANDLE): Boolean {
        // An upgrade's new binary must run, so a service still running from before is restarted.
        stop(service)
        return Advapi32.INSTANCE.StartService(service, 0, null) || Native.getLastError() == Win32.ERROR_SERVICE_ALREADY_RUNNING
    }

    fun stop(service: Winsvc.SC_HANDLE) {
        val status = Winsvc.SERVICE_STATUS()
        if (!Advapi32.INSTANCE.ControlService(service, Winsvc.SERVICE_CONTROL_STOP, status)) return
        repeat(STOP_POLLS) {
            if (!Advapi32.INSTANCE.QueryServiceStatus(service, status) || status.dwCurrentState == Winsvc.SERVICE_STOPPED) return
            Thread.sleep(STOP_POLL_MILLIS)
        }
    }

    private const val DISPLAY_NAME = "Posato helper"
    private const val DESCRIPTION = "Pauses the websites and apps you choose in Posato."

    private fun serviceDescription(): String {
        val resource = if (java.util.Locale.getDefault().language == "pl") {
            "values-pl/service.properties"
        } else {
            "values/service.properties"
        }
        val stream = ServiceRegistration::class.java.classLoader?.getResourceAsStream(resource) ?: return DESCRIPTION
        return stream.reader(Charsets.UTF_8).use { reader ->
            java.util.Properties().apply { load(reader) }.getProperty("description") ?: DESCRIPTION
        }
    }

    private const val LOCAL_SYSTEM = "LocalSystem"
    private const val SERVICE_WIN32_OWN_PROCESS = 0x10
    private const val SERVICE_AUTO_START = 2
    private const val SERVICE_ERROR_NORMAL = 1
    private const val SERVICE_CONFIG_DESCRIPTION = 1
    private const val SERVICE_CONFIG_FAILURE_ACTIONS = 2
    private const val SERVICE_CONFIG_FAILURE_ACTIONS_FLAG = 4
    private const val SERVICE_CONFIG_REQUIRED_PRIVILEGES_INFO = 6
    private const val SC_ACTION_RESTART = 1
    private val RESTART_DELAYS = listOf(5_000, 5_000, 30_000)
    private const val RESET_PERIOD_SECONDS = 86_400
    private val REQUIRED_PRIVILEGES = listOf("SeChangeNotifyPrivilege", "SeImpersonatePrivilege")
    private const val STOP_POLLS = 60
    private const val STOP_POLL_MILLIS = 500L
}
