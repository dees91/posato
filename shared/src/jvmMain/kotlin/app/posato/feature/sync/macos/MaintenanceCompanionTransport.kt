package app.posato.feature.sync.macos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

public interface CompanionMaintenance {
    public suspend fun drainForMaintenance(): Boolean

    public fun resumeAfterMaintenance()
}

internal class MaintenanceCompanionTransport(
    private val drainGraceMillis: Long = COMPANION_DRAIN_GRACE_MILLIS,
    private val exitTimeoutMillis: Long = COMPANION_EXIT_TIMEOUT_MILLIS,
    createDelegate: (onProcessStarted: (Process) -> Unit) -> SyncCompanionTransport,
) : SyncCompanionTransport,
    CompanionMaintenance {
    private val monitor = Any()
    private val inFlight = MutableStateFlow(0)
    private val startedProcesses = mutableListOf<Process>()
    private val delegate = createDelegate(::registerProcess)
    private var refusing = false

    fun registerProcess(process: Process) {
        synchronized(monitor) {
            startedProcesses.removeAll { started -> !started.isAlive }
            startedProcesses += process
        }
    }

    override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
        val admitted = synchronized(monitor) {
            if (!refusing) {
                inFlight.update { count -> count + 1 }
            }
            !refusing
        }
        if (!admitted) {
            message.clear()
            return CompanionExchange.Unknown
        }
        try {
            return delegate.transact(message)
        } finally {
            inFlight.update { count -> count - 1 }
        }
    }

    override fun newRequestIdentifier(): ByteArray {
        return delegate.newRequestIdentifier()
    }

    override suspend fun drainForMaintenance(): Boolean {
        synchronized(monitor) {
            refusing = true
        }
        if (awaitIdle(drainGraceMillis)) {
            return awaitProcessExit()
        }
        val processes = synchronized(monitor) { startedProcesses.toList() }
        withContext(Dispatchers.IO) {
            processes.filter(Process::isAlive).forEach(Process::destroy)
        }
        val exited = awaitProcessExit()
        return awaitIdle(exitTimeoutMillis) && exited
    }

    override fun resumeAfterMaintenance() {
        synchronized(monitor) {
            refusing = false
        }
    }

    private suspend fun awaitIdle(timeoutMillis: Long): Boolean {
        return withTimeoutOrNull(timeoutMillis) { inFlight.first { count -> count == 0 } } != null
    }

    private suspend fun awaitProcessExit(): Boolean {
        val processes = synchronized(monitor) { startedProcesses.toList() }
        return withContext(Dispatchers.IO) {
            processes.all { process ->
                process.waitFor(exitTimeoutMillis, TimeUnit.MILLISECONDS) ||
                    process.destroyForcibly().waitFor(exitTimeoutMillis, TimeUnit.MILLISECONDS)
            }
        }
    }
}

private const val COMPANION_DRAIN_GRACE_MILLIS: Long = 2_000L
private const val COMPANION_EXIT_TIMEOUT_MILLIS: Long = 5_000L
