package app.posato.desktop.update

import app.posato.desktop.macos.ApplicationEnforcementResponse
import app.posato.desktop.macos.HelperMaintenance
import app.posato.desktop.macos.HelperResult
import app.posato.desktop.macos.concludesReconciliation
import app.posato.feature.sync.macos.CompanionMaintenance
import app.posato.feature.update.MaintenanceCloseResult
import app.posato.feature.update.MaintenanceReopenResult
import app.posato.feature.update.UpdateMaintenanceGate
import kotlinx.coroutines.CancellationException

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
    private val runningBuild: String,
    private val gate: UpdateMaintenanceGate,
    private val instanceLock: AdmissionInstanceLock,
    private val helper: UpdateCleanupCommands,
    private val helperMaintenance: HelperMaintenance,
    private val companion: CompanionMaintenance,
    private val storedProxies: () -> StoredProxyEvidence,
    private val installer: () -> InstallerObservation,
    private val bundleIdentity: () -> BundleIdentity,
) {
    suspend fun admit(targetBuild: String): AdmissionOutcome {
        if (!instanceLock.tryUpgradeForAdmission()) {
            return AdmissionOutcome.Refused(AdmissionRefusal.OTHER_INSTANCE)
        }
        when (gate.close(runningBuild, targetBuild)) {
            MaintenanceCloseResult.Closed -> Unit
            MaintenanceCloseResult.SessionActive -> return refuseWithOpenGate(AdmissionRefusal.SESSION_ACTIVE)
            MaintenanceCloseResult.StorageFailure -> return refuseWithOpenGate(AdmissionRefusal.STORAGE)
        }
        helperMaintenance.engageBackstop()
        val cleanup = confirmCleanup()
        if (cleanup != null) {
            return AdmissionOutcome.Refused(cleanup)
        }
        if (!enterMaintenanceMode()) {
            return AdmissionOutcome.Refused(AdmissionRefusal.SHUTDOWN_INCOMPLETE)
        }
        gate.markCycleAdmitted()
        return AdmissionOutcome.Admitted
    }

    suspend fun onCycleEnded() {
        gate.markCycleEnded()
    }

    suspend fun restoreMaintenanceAtStartup(): Boolean {
        if (gate.closedGate() == null) {
            return false
        }
        helperMaintenance.engageBackstop()
        enterMaintenanceMode()
        return true
    }

    suspend fun evaluateRelease(): MaintenanceReopenResult {
        val result = gate.reopenWhen { closed ->
            val settled = replacementSettled(GateBuilds(closed.fromBuild, closed.targetBuild), installer(), bundleIdentity())
            if (settled) {
                helperMaintenance.allowSpawns()
                companion.resumeAfterMaintenance()
                serviceRevalidated()
            } else {
                false
            }
        }
        if (result == MaintenanceReopenResult.Reopened || result == MaintenanceReopenResult.AlreadyOpen) {
            helperMaintenance.allowSpawns()
            companion.resumeAfterMaintenance()
            helperMaintenance.releaseBackstop()
            instanceLock.downgradeAfterMaintenance()
        }
        return result
    }

    private fun refuseWithOpenGate(reason: AdmissionRefusal): AdmissionOutcome {
        instanceLock.downgradeAfterMaintenance()
        return AdmissionOutcome.Refused(reason)
    }

    private suspend fun confirmCleanup(): AdmissionRefusal? {
        return try {
            if (!reconcilePendingOutcomes()) {
                AdmissionRefusal.CLEANUP_UNCERTAIN
            } else {
                cleanupForService(helper.status())
            }
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            AdmissionRefusal.CLEANUP_UNCERTAIN
        }
    }

    private fun reconcilePendingOutcomes(): Boolean {
        repeat(RECONCILE_ATTEMPTS) {
            if (helper.reconcileUnknown().concludesReconciliation()) {
                return true
            }
        }
        return false
    }

    private fun cleanupForService(status: HelperResult): AdmissionRefusal? {
        return when {
            status.isReady() -> cleanupEnabledService(status)
            status.isNotRegistered() -> cleanupUnregisteredService()
            else -> AdmissionRefusal.SERVICE_ACTION_REQUIRED
        }
    }

    private fun cleanupEnabledService(status: HelperResult): AdmissionRefusal? {
        if (status.ownershipPhase == HelperResult.Phase.Applied || status.ownershipPhase == HelperResult.Phase.Prepared) {
            return AdmissionRefusal.FOREIGN_LEASE
        }
        val restored = helper.restore()
        if (restored.outcome != HelperResult.Outcome.Success || restored.ownershipPhase != HelperResult.Phase.Idle) {
            return AdmissionRefusal.CLEANUP_UNCERTAIN
        }
        return clearApplications()
    }

    private fun cleanupUnregisteredService(): AdmissionRefusal? {
        if (storedProxies() != StoredProxyEvidence.NO_LOOPBACK_PROXY) {
            return AdmissionRefusal.CLEANUP_UNCERTAIN
        }
        return clearApplications()
    }

    private fun clearApplications(): AdmissionRefusal? {
        val cleared = helper.configureApplications(emptyList(), null)
        return if (cleared.result.outcome == HelperResult.Outcome.Success) null else AdmissionRefusal.CLEANUP_UNCERTAIN
    }

    private suspend fun enterMaintenanceMode(): Boolean {
        val helpersStopped = helperMaintenance.refuseSpawnsAndStopHelpers()
        val companionStopped = companion.drainForMaintenance()
        return helpersStopped && companionStopped
    }

    private fun serviceRevalidated(): Boolean {
        return try {
            if (!reconcilePendingOutcomes()) {
                false
            } else {
                val status = helper.status()
                when {
                    status.isReady() -> status.ownershipPhase == HelperResult.Phase.Idle
                    status.isNotRegistered() -> storedProxies() == StoredProxyEvidence.NO_LOOPBACK_PROXY
                    else -> false
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}

private fun HelperResult.isReady(): Boolean {
    return outcome == HelperResult.Outcome.Success && serviceState == HelperResult.State.Ready
}

private fun HelperResult.isNotRegistered(): Boolean {
    return outcome == HelperResult.Outcome.ActionRequired && serviceState == HelperResult.State.NotRegistered
}

private const val RECONCILE_ATTEMPTS: Int = 3
