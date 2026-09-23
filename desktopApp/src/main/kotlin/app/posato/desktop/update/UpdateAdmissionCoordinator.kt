package app.posato.desktop.update

import app.posato.desktop.macos.ApplicationEnforcementResponse
import app.posato.desktop.macos.HelperMaintenance
import app.posato.desktop.macos.HelperResult
import app.posato.feature.sync.macos.CompanionMaintenance
import app.posato.feature.update.MaintenanceCloseResult
import app.posato.feature.update.MaintenanceReopenResult
import app.posato.feature.update.UpdateMaintenanceGate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface UpdateCleanupCommands {
    fun reconcileUnknown(): HelperResult

    fun status(): HelperResult

    fun restore(): HelperResult

    fun enable(): HelperResult

    fun configureApplications(
        requirements: List<ByteArray>,
        sessionEndEpochMilliseconds: Long?,
    ): ApplicationEnforcementResponse
}

internal interface AdmissionInstanceLock {
    fun tryUpgradeForAdmission(): Boolean

    fun downgradeAfterMaintenance()
}

internal enum class AdmissionRefusal {
    OTHER_INSTANCE,
    SESSION_ACTIVE,
    STORAGE,
    FOREIGN_LEASE,
    CLEANUP_UNCERTAIN,
    SERVICE_ACTION_REQUIRED,
    SHUTDOWN_INCOMPLETE,
}

internal sealed interface AdmissionOutcome {
    data object Admitted : AdmissionOutcome

    data class Refused(
        val reason: AdmissionRefusal,
    ) : AdmissionOutcome
}

internal class UpdateAdmissionCoordinator(
    private val runningBuild: () -> String,
    private val gate: UpdateMaintenanceGate,
    private val instanceLock: AdmissionInstanceLock,
    helper: UpdateCleanupCommands,
    private val helperMaintenance: HelperMaintenance,
    private val companion: CompanionMaintenance,
    storedProxies: () -> StoredProxyEvidence,
    private val installer: () -> InstallerObservation,
    private val bundleIdentity: () -> BundleIdentity,
) : UpdateAdmission {
    private val cleanup = MaintenanceCleanup(helper, storedProxies)
    private val transitions = Mutex()
    private var admittedCycle: Long? = null

    override suspend fun admit(
        targetBuild: String,
        cycle: Long,
    ): AdmissionOutcome {
        return transitions.withLock { admitLocked(targetBuild, cycle) }
    }

    override suspend fun admitPendingInstallation(
        targetBuild: String,
        cycle: Long,
    ): AdmissionOutcome {
        return transitions.withLock { admitPendingLocked(targetBuild, cycle) }
    }

    override suspend fun onCycleEnded(cycle: Long) {
        transitions.withLock {
            if (admittedCycle == cycle) {
                admittedCycle = null
                gate.markCycleEnded()
            }
        }
    }

    override suspend fun restoreMaintenanceAtStartup(): Boolean {
        return transitions.withLock { restoreAtStartupLocked() }
    }

    override suspend fun evaluateRelease(): MaintenanceReopenResult {
        return transitions.withLock { evaluateReleaseLocked() }
    }

    private suspend fun admitLocked(
        targetBuild: String,
        cycle: Long,
    ): AdmissionOutcome {
        val refusal = if (instanceLock.tryUpgradeForAdmission()) closeAndClean(targetBuild) else AdmissionRefusal.OTHER_INSTANCE
        if (refusal != null) {
            return AdmissionOutcome.Refused(refusal)
        }
        gate.markCycleAdmitted()
        admittedCycle = cycle
        return AdmissionOutcome.Admitted
    }

    private suspend fun admitPendingLocked(
        targetBuild: String,
        cycle: Long,
    ): AdmissionOutcome {
        if (gate.closedGate() == null) {
            return admitLocked(targetBuild, cycle)
        }
        helperMaintenance.engageBackstop()
        if (!enterMaintenanceMode()) {
            return AdmissionOutcome.Refused(AdmissionRefusal.SHUTDOWN_INCOMPLETE)
        }
        gate.markCycleAdmitted()
        admittedCycle = cycle
        return AdmissionOutcome.Admitted
    }

    private suspend fun restoreAtStartupLocked(): Boolean {
        if (gate.closedGate() == null) {
            return false
        }
        helperMaintenance.engageBackstop()
        enterMaintenanceMode()
        return true
    }

    private suspend fun evaluateReleaseLocked(): MaintenanceReopenResult {
        val result = gate.reopenWhen { closed ->
            val settled = replacementSettled(GateBuilds(closed.fromBuild, closed.targetBuild), installer(), bundleIdentity())
            if (settled) {
                helperMaintenance.allowSpawns()
                companion.resumeAfterMaintenance()
            }
            settled && cleanup.serviceRevalidated()
        }
        if (result == MaintenanceReopenResult.Reopened || result == MaintenanceReopenResult.AlreadyOpen) {
            helperMaintenance.allowSpawns()
            companion.resumeAfterMaintenance()
            helperMaintenance.releaseBackstop()
            instanceLock.downgradeAfterMaintenance()
        }
        return result
    }

    private suspend fun closeAndClean(targetBuild: String): AdmissionRefusal? {
        val closed = gate.close(runningBuild(), targetBuild)
        if (closed != MaintenanceCloseResult.Closed) {
            instanceLock.downgradeAfterMaintenance()
            return if (closed == MaintenanceCloseResult.SessionActive) AdmissionRefusal.SESSION_ACTIVE else AdmissionRefusal.STORAGE
        }
        helperMaintenance.engageBackstop()
        return cleanup.confirm() ?: if (enterMaintenanceMode()) null else AdmissionRefusal.SHUTDOWN_INCOMPLETE
    }

    private suspend fun enterMaintenanceMode(): Boolean {
        val helpersStopped = helperMaintenance.refuseSpawnsAndStopHelpers()
        val companionStopped = companion.drainForMaintenance()
        return helpersStopped && companionStopped
    }
}
