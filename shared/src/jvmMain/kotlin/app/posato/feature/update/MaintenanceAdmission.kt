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

public class ClosedMaintenanceGate internal constructor(
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

public class MaintenanceAdmission internal constructor(
    private val store: UpdateMaintenanceStore,
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private var admittedCycle = false

    internal suspend fun <T> withApplyPermit(
        refused: () -> T,
        block: suspend () -> T,
    ): T {
        return mutex.withLock {
            val gate = store.read()
            if (gate is MaintenanceStoreResult.Success && gate.value == MaintenanceGate.Open) {
                block()
            } else {
                refused()
            }
        }
    }

    public suspend fun close(
        fromBuild: String,
        targetBuild: String,
    ): MaintenanceCloseResult {
        return mutex.withLock {
            when (val closed = store.close(fromBuild, targetBuild, clock())) {
                is MaintenanceStoreResult.Failure -> MaintenanceCloseResult.StorageFailure
                is MaintenanceStoreResult.Success -> when (closed.value) {
                    MaintenanceCloseOutcome.CLOSED -> MaintenanceCloseResult.Closed
                    MaintenanceCloseOutcome.SESSION_ACTIVE -> MaintenanceCloseResult.SessionActive
                }
            }
        }
    }

    public suspend fun closedGate(): ClosedMaintenanceGate? {
        return mutex.withLock {
            readClosedGate()
        }
    }

    public suspend fun isGateOpen(): Boolean {
        return mutex.withLock {
            val gate = store.read()
            gate is MaintenanceStoreResult.Success && gate.value == MaintenanceGate.Open
        }
    }

    public suspend fun markCycleAdmitted() {
        mutex.withLock {
            admittedCycle = true
        }
    }

    public suspend fun markCycleEnded() {
        mutex.withLock {
            admittedCycle = false
        }
    }

    public suspend fun isCycleAdmitted(): Boolean {
        return mutex.withLock {
            admittedCycle
        }
    }

    public suspend fun reopenWhen(evidence: suspend (ClosedMaintenanceGate) -> Boolean): MaintenanceReopenResult {
        return mutex.withLock {
            val gate = store.read()
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
                    MaintenanceReopenResult.Reopened
                }

                else -> {
                    MaintenanceReopenResult.StorageFailure
                }
            }
        }
    }

    private suspend fun readClosedGate(): ClosedMaintenanceGate? {
        val gate = (store.read() as? MaintenanceStoreResult.Success)?.value as? MaintenanceGate.Closed
        return gate?.toPublic()
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
    public val admission: MaintenanceAdmission,
    public val companion: CompanionMaintenance,
)
