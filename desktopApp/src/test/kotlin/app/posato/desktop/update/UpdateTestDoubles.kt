package app.posato.desktop.update

import app.posato.desktop.macos.ApplicationEnforcementResponse
import app.posato.desktop.macos.HelperResult
import app.posato.feature.sync.macos.CompanionMaintenance
import app.posato.feature.update.ClosedMaintenanceGate
import app.posato.feature.update.MaintenanceCloseResult
import app.posato.feature.update.MaintenanceReopenResult
import app.posato.feature.update.UpdateMaintenanceGate

internal class FakeGate(
    private val closeResult: MaintenanceCloseResult = MaintenanceCloseResult.Closed,
) : UpdateMaintenanceGate {
    var closed: ClosedMaintenanceGate? = null
    var cycleAdmitted = false

    override suspend fun close(
        fromBuild: String,
        targetBuild: String,
    ): MaintenanceCloseResult {
        if (closeResult == MaintenanceCloseResult.Closed) {
            closed = ClosedMaintenanceGate(fromBuild, targetBuild, 1L)
        }
        return closeResult
    }

    override suspend fun closedGate(): ClosedMaintenanceGate? {
        return closed
    }

    override suspend fun markCycleAdmitted() {
        cycleAdmitted = true
    }

    override suspend fun markCycleEnded() {
        cycleAdmitted = false
    }

    override suspend fun reopenWhen(evidence: suspend (ClosedMaintenanceGate) -> Boolean): MaintenanceReopenResult {
        val current = closed ?: return MaintenanceReopenResult.AlreadyOpen
        return when {
            cycleAdmitted -> MaintenanceReopenResult.CycleInProgress
            !evidence(current) -> MaintenanceReopenResult.EvidenceMissing
            else -> MaintenanceReopenResult.Reopened.also { closed = null }
        }
    }
}

internal class FakeLock(
    private val upgrades: Boolean = true,
) : AdmissionInstanceLock {
    var downgraded = false

    override fun tryUpgradeForAdmission(): Boolean {
        return upgrades
    }

    override fun downgradeAfterMaintenance() {
        downgraded = true
    }
}

internal class FakeCompanion(
    private val drains: Boolean = true,
    var beforeDrain: suspend () -> Unit = {},
) : CompanionMaintenance {
    var draining = false

    override suspend fun drainForMaintenance(): Boolean {
        draining = true
        beforeDrain()
        return drains
    }

    override fun resumeAfterMaintenance() {
        draining = false
    }
}

internal class FakeCleanup(
    private val reconcileResults: MutableList<HelperResult> = mutableListOf(SUCCESS_IDLE),
    var statusResult: HelperResult = SUCCESS_IDLE,
    private val restoreResult: HelperResult = SUCCESS_IDLE,
    private val failure: Exception? = null,
) : UpdateCleanupCommands {
    val calls = mutableListOf<String>()
    private var pending = reconcileResults.size > 1

    override fun reconcileUnknown(): HelperResult {
        failure?.let { throw it }
        if (!pending) {
            return statusResult
        }
        calls += "reconcile"
        val next = reconcileResults.removeAt(0)
        if (next.outcome != HelperResult.Outcome.UnknownOutcome) {
            pending = false
        }
        return next
    }

    override fun status(): HelperResult {
        failure?.let { throw it }
        calls += "status"
        return statusResult
    }

    override fun restore(): HelperResult {
        calls += "restore"
        return restoreResult
    }

    override fun configureApplications(
        requirements: List<ByteArray>,
        sessionEndEpochMilliseconds: Long?,
    ): ApplicationEnforcementResponse {
        calls += if (requirements.isEmpty()) "configure-empty" else "configure"
        return ApplicationEnforcementResponse(SUCCESS_IDLE, 0)
    }

    override fun enable(): HelperResult {
        calls += "enable"
        return SUCCESS_IDLE
    }
}

internal val SUCCESS_IDLE: HelperResult = result(HelperResult.Outcome.Success, HelperResult.State.Ready, HelperResult.Phase.Idle)

internal fun result(
    outcome: HelperResult.Outcome,
    state: HelperResult.State,
    phase: HelperResult.Phase,
): HelperResult {
    return HelperResult(outcome, state, phase, HelperResult.RequiredAction.None, HelperResult.Failure.None)
}
