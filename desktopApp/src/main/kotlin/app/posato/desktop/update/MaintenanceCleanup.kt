package app.posato.desktop.update

import app.posato.desktop.macos.HelperResult
import app.posato.desktop.macos.concludesReconciliation
import kotlinx.coroutines.CancellationException

internal class MaintenanceCleanup(
    private val helper: UpdateCleanupCommands,
    private val storedProxies: () -> StoredProxyEvidence,
) {
    fun confirm(): AdmissionRefusal? {
        return try {
            if (reconcilePendingOutcomes()) cleanupForService(helper.status()) else AdmissionRefusal.CLEANUP_UNCERTAIN
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            AdmissionRefusal.CLEANUP_UNCERTAIN
        }
    }

    fun serviceRevalidated(): Boolean {
        return try {
            reconcilePendingOutcomes() && revalidates(helper.status())
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            false
        }
    }

    private fun revalidates(status: HelperResult): Boolean {
        return when {
            status.isReady() -> status.ownershipPhase == HelperResult.Phase.Idle
            status.isNotRegistered() -> storedProxies() == StoredProxyEvidence.NO_LOOPBACK_PROXY
            else -> false
        }
    }

    private fun reconcilePendingOutcomes(): Boolean {
        return (1..RECONCILE_ATTEMPTS).any { helper.reconcileUnknown().concludesReconciliation() }
    }

    private fun cleanupForService(status: HelperResult): AdmissionRefusal? {
        return when {
            status.isReady() -> cleanupEnabledService(status)
            status.isNotRegistered() -> cleanupUnregisteredService()
            else -> AdmissionRefusal.SERVICE_ACTION_REQUIRED
        }
    }

    private fun cleanupEnabledService(status: HelperResult): AdmissionRefusal? {
        val restored = if (status.holdsLiveLease()) null else helper.restore()
        return when {
            restored == null -> AdmissionRefusal.FOREIGN_LEASE
            !restored.isIdleSuccess() -> AdmissionRefusal.CLEANUP_UNCERTAIN
            else -> clearApplications()
        }
    }

    private fun cleanupUnregisteredService(): AdmissionRefusal? {
        return if (storedProxies() == StoredProxyEvidence.NO_LOOPBACK_PROXY) clearApplications() else AdmissionRefusal.CLEANUP_UNCERTAIN
    }

    private fun clearApplications(): AdmissionRefusal? {
        val cleared = helper.configureApplications(emptyList(), null)
        return if (cleared.result.outcome == HelperResult.Outcome.Success) null else AdmissionRefusal.CLEANUP_UNCERTAIN
    }
}

private fun HelperResult.isIdleSuccess(): Boolean {
    return outcome == HelperResult.Outcome.Success && ownershipPhase == HelperResult.Phase.Idle
}

private fun HelperResult.holdsLiveLease(): Boolean {
    return ownershipPhase == HelperResult.Phase.Applied || ownershipPhase == HelperResult.Phase.Prepared
}

private fun HelperResult.isReady(): Boolean {
    return outcome == HelperResult.Outcome.Success && serviceState == HelperResult.State.Ready
}

private fun HelperResult.isNotRegistered(): Boolean {
    return outcome == HelperResult.Outcome.ActionRequired && serviceState == HelperResult.State.NotRegistered
}

private const val RECONCILE_ATTEMPTS: Int = 3
