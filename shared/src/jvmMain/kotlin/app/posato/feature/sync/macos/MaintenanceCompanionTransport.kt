package app.posato.feature.sync.macos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

public interface CompanionMaintenance {
    public suspend fun drainForMaintenance(): Boolean

    public fun resumeAfterMaintenance()
}

internal class MaintenanceCompanionTransport(
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
        inFlight.first { count -> count == 0 }
        val processes = synchronized(monitor) { startedProcesses.toList() }
        return withContext(Dispatchers.IO) {
            processes.all { process -> process.waitFor(exitTimeoutMillis, TimeUnit.MILLISECONDS) }
        }
    }

    override fun resumeAfterMaintenance() {
        synchronized(monitor) {
            refusing = false
        }
    }
}

private const val COMPANION_EXIT_TIMEOUT_MILLIS: Long = 5_000L
