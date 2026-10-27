package app.posato.windows.helper

import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Winsvc
import java.util.concurrent.CountDownLatch

/**
 * The Service Control Manager's side of `posato-helper.exe serve`: the dispatcher runs [HelperService] as the service
 * [SERVICE_NAME], reports its state, and stops it on a stop or shutdown request. Started outside the Service Control
 * Manager, the same command serves in the foreground.
 */
internal class ServiceHost(
    private val service: HelperService,
) {
    private val stopped = CountDownLatch(1)
    private var statusHandle: Winsvc.SERVICE_STATUS_HANDLE? = null

    @Volatile
    private var exitCode = 0

    // The Service Control Manager calls back through these from its own threads, so they are kept referenced.
    private val handler = Winsvc.HandlerEx { control, _, _, _ ->
        when (control) {
            Winsvc.SERVICE_CONTROL_STOP, Winsvc.SERVICE_CONTROL_SHUTDOWN -> {
                report(Winsvc.SERVICE_STOP_PENDING)
                stop()
                NO_ERROR
            }

            Winsvc.SERVICE_CONTROL_INTERROGATE -> {
                NO_ERROR
            }

            else -> {
                ERROR_CALL_NOT_IMPLEMENTED
            }
        }
    }
    private val main = Winsvc.SERVICE_MAIN_FUNCTION { _, _ ->
        statusHandle = Advapi32.INSTANCE.RegisterServiceCtrlHandlerEx(SERVICE_NAME, handler, null)
        report(Winsvc.SERVICE_START_PENDING)
        val serving = Thread({ runService() }, "posato-helper-pipe").apply { start() }
        report(Winsvc.SERVICE_RUNNING)
        stopped.await()
        serving.join(STOP_WAIT_MILLIS)
        report(Winsvc.SERVICE_STOPPED)
    }

    /** Returns the process exit code. */
    fun run(): Int {
        val table = Winsvc.SERVICE_TABLE_ENTRY().toArray(2).map { it as Winsvc.SERVICE_TABLE_ENTRY }
        table[0].lpServiceName = SERVICE_NAME
        table[0].lpServiceProc = main
        table.forEach { it.write() }
        if (Advapi32.INSTANCE.StartServiceCtrlDispatcher(table.toTypedArray())) return exitCode
        if (Native.getLastError() != Win32.ERROR_FAILED_SERVICE_CONTROLLER_CONNECT) return DISPATCHER_FAILED
        runService()
        return exitCode
    }

    private fun runService() {
        // A pipe name taken by someone else is a failure, so the Service Control Manager restarts the service later.
        if (!service.serve()) exitCode = PIPE_TAKEN
        stop()
    }

    private fun stop() {
        if (stopped.count == 0L) return
        service.stop()
        stopped.countDown()
    }

    private fun report(state: Int) {
        val handle = statusHandle ?: return
        val status = Winsvc.SERVICE_STATUS().apply {
            dwServiceType = SERVICE_WIN32_OWN_PROCESS
            dwCurrentState = state
            dwControlsAccepted = if (state == Winsvc.SERVICE_RUNNING) Winsvc.SERVICE_ACCEPT_STOP or Winsvc.SERVICE_ACCEPT_SHUTDOWN else 0
            dwWin32ExitCode = if (state == Winsvc.SERVICE_STOPPED && exitCode != 0) ERROR_SERVICE_SPECIFIC_ERROR else NO_ERROR
            dwServiceSpecificExitCode = exitCode
            dwWaitHint = if (state == Winsvc.SERVICE_RUNNING || state == Winsvc.SERVICE_STOPPED) 0 else WAIT_HINT_MILLIS
        }
        Advapi32.INSTANCE.SetServiceStatus(handle, status)
    }

    companion object {
        const val SERVICE_NAME = "PosatoHelper"
        private const val SERVICE_WIN32_OWN_PROCESS = 0x10
        private const val NO_ERROR = 0
        private const val ERROR_CALL_NOT_IMPLEMENTED = 120
        private const val ERROR_SERVICE_SPECIFIC_ERROR = 1066
        private const val WAIT_HINT_MILLIS = 10_000
        private const val STOP_WAIT_MILLIS = 5_000L
        private const val DISPATCHER_FAILED = 4
        private const val PIPE_TAKEN = 5
    }
}
