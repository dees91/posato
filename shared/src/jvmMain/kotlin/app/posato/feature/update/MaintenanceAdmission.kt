package app.posato.feature.update

import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.sync.macos.CompanionMaintenance
import app.posato.feature.update.data.MaintenanceCloseOutcome
import app.posato.feature.update.data.MaintenanceGate
import app.posato.feature.update.data.MaintenanceStoreResult
import app.posato.feature.update.data.UpdateMaintenanceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public sealed interface MaintenanceCloseResult {
    public data object Closed : MaintenanceCloseResult

    public data object SessionActive : MaintenanceCloseResult

    public data object StorageFailure : MaintenanceCloseResult
}

public sealed interface MaintenanceReopenResult {
    public data object Reopened : MaintenanceReopenResult

    public data object AlreadyOpen : MaintenanceReopenResult

    public data object CycleInProgress : MaintenanceReopenResult

    public data object EvidenceMissing : MaintenanceReopenResult

    public data object StorageFailure : MaintenanceReopenResult
}

public class ClosedMaintenanceGate(
    public val fromBuild: String,
    public val targetBuild: String,
    public val closedEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean {
        return other is ClosedMaintenanceGate &&
            fromBuild == other.fromBuild &&
            targetBuild == other.targetBuild &&
            closedEpochMillis == other.closedEpochMillis
    }

    override fun hashCode(): Int {
        var result = fromBuild.hashCode()
        result = 31 * result + targetBuild.hashCode()
        result = 31 * result + closedEpochMillis.hashCode()
        return result
    }
}

public interface UpdateMaintenanceGate {
    public suspend fun close(
        fromBuild: String,
        targetBuild: String,
    ): MaintenanceCloseResult

    public suspend fun closedGate(): ClosedMaintenanceGate?

    public suspend fun markCycleAdmitted()

    public suspend fun markCycleEnded()

    public suspend fun reopenWhen(evidence: suspend (ClosedMaintenanceGate) -> Boolean): MaintenanceReopenResult
}

public class MaintenanceAdmission internal constructor(
    private val store: UpdateMaintenanceStore,
    private val clock: () -> Long,
) : UpdateMaintenanceGate {
    private val mutex = Mutex()
    private var admittedCycle = false
    private val mutableClosed = MutableStateFlow<Boolean?>(null)

    public val closed: StateFlow<Boolean?> = mutableClosed.asStateFlow()

    internal suspend fun <T> withApplyPermit(
        refused: () -> T,
        block: suspend () -> T,
    ): T {
        return mutex.withLock {
            val gate = store.read()
            observe(gate)
            if (gate is MaintenanceStoreResult.Success && gate.value == MaintenanceGate.Open) {
                block()
            } else {
                refused()
            }
        }
    }

    override suspend fun close(
        fromBuild: String,
        targetBuild: String,
    ): MaintenanceCloseResult {
        return mutex.withLock {
            when (val closed = store.close(fromBuild, targetBuild, clock())) {
                is MaintenanceStoreResult.Failure -> MaintenanceCloseResult.StorageFailure

                is MaintenanceStoreResult.Success -> when (closed.value) {
                    MaintenanceCloseOutcome.CLOSED -> {
                        mutableClosed.value = true
                        MaintenanceCloseResult.Closed
                    }

                    MaintenanceCloseOutcome.SESSION_ACTIVE -> {
                        MaintenanceCloseResult.SessionActive
                    }
                }
            }
        }
    }

    override suspend fun closedGate(): ClosedMaintenanceGate? {
        return mutex.withLock {
            readClosedGate()
        }
    }

    override suspend fun markCycleAdmitted() {
        mutex.withLock {
            admittedCycle = true
        }
    }

    override suspend fun markCycleEnded() {
        mutex.withLock {
            admittedCycle = false
        }
    }

    override suspend fun reopenWhen(evidence: suspend (ClosedMaintenanceGate) -> Boolean): MaintenanceReopenResult {
        return mutex.withLock {
            val gate = store.read()
            observe(gate)
            val closed = ((gate as? MaintenanceStoreResult.Success)?.value as? MaintenanceGate.Closed)?.toPublic()
            when {
                gate !is MaintenanceStoreResult.Success -> {
                    MaintenanceReopenResult.StorageFailure
                }

                closed == null -> {
                    MaintenanceReopenResult.AlreadyOpen
                }

                admittedCycle -> {
                    MaintenanceReopenResult.CycleInProgress
                }

                !evidence(closed) -> {
                    MaintenanceReopenResult.EvidenceMissing
                }

                store.reopen() is MaintenanceStoreResult.Success -> {
                    mutableClosed.value = false
                    MaintenanceReopenResult.Reopened
                }

                else -> {
                    MaintenanceReopenResult.StorageFailure
                }
            }
        }
    }

    private suspend fun readClosedGate(): ClosedMaintenanceGate? {
        val read = store.read()
        observe(read)
        val gate = (read as? MaintenanceStoreResult.Success)?.value as? MaintenanceGate.Closed
        return gate?.toPublic()
    }

    private fun observe(read: MaintenanceStoreResult<MaintenanceGate>) {
        if (read is MaintenanceStoreResult.Success) {
            mutableClosed.value = read.value is MaintenanceGate.Closed
        }
    }
}

private fun MaintenanceGate.Closed.toPublic(): ClosedMaintenanceGate {
    return ClosedMaintenanceGate(fromBuild, targetBuild, closedEpochMillis)
}

public class GatedEnforcementPort(
    private val delegate: EnforcementPort,
    private val admission: MaintenanceAdmission,
) : EnforcementPort by delegate {
    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        return admission.withApplyPermit(
            refused = { EnforcementApplyReport(EnforcementOutcome.FAILED, false, delegate.reapplyRequiresPrompt) },
            block = { delegate.apply(request) },
        )
    }
}

public class DesktopUpdateMaintenance(
    public val gate: UpdateMaintenanceGate,
    public val companion: CompanionMaintenance,
)
