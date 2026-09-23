package app.posato.desktop.update

import app.posato.desktop.macos.ApplicationEnforcementResponse
import app.posato.desktop.macos.HelperMaintenance
import app.posato.desktop.macos.HelperResult
import app.posato.feature.sync.macos.CompanionMaintenance
import app.posato.feature.update.ClosedMaintenanceGate
import app.posato.feature.update.MaintenanceCloseResult
import app.posato.feature.update.MaintenanceReopenResult
import app.posato.feature.update.UpdateMaintenanceGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateAdmissionCoordinatorTest {
    @Test
    fun `given an enabled idle service when admission runs then cleanup is confirmed and maintenance mode holds`() = runTest {
        val fixture = Fixture()

        assertEquals(AdmissionOutcome.Admitted, fixture.coordinator.admit(TARGET_BUILD))
        assertEquals(listOf("status", "restore", "configure-empty"), fixture.helper.calls)
        assertTrue(fixture.gate.cycleAdmitted)
        assertTrue(fixture.helperMaintenance.isBackstopEngaged)
        assertFalse(fixture.helperMaintenance.allowsSpawns)
        assertTrue(fixture.companion.draining)
    }

    @Test
    fun `given another instance when admission runs then it is refused before the gate closes`() = runTest {
        val fixture = Fixture(lock = FakeLock(upgrades = false))

        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.OTHER_INSTANCE), fixture.coordinator.admit(TARGET_BUILD))
        assertNull(fixture.gate.closed)
        assertTrue(fixture.helper.calls.isEmpty())
    }

    @Test
    fun `given an active session when admission runs then it is refused with the gate open and the lock downgraded`() = runTest {
        val fixture = Fixture(gate = FakeGate(closeResult = MaintenanceCloseResult.SessionActive))

        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.SESSION_ACTIVE), fixture.coordinator.admit(TARGET_BUILD))
        assertTrue(fixture.lock.downgraded)
        assertFalse(fixture.helperMaintenance.isBackstopEngaged)
    }

    @Test
    fun `given a pending unknown outcome when admission runs then it is reconciled before restore`() = runTest {
        val helper = FakeCleanup(reconcileResults = mutableListOf(UNKNOWN, SUCCESS_IDLE))
        val fixture = Fixture(helper = helper)

        assertEquals(AdmissionOutcome.Admitted, fixture.coordinator.admit(TARGET_BUILD))
        assertEquals(listOf("reconcile", "reconcile", "status", "restore", "configure-empty"), helper.calls)
    }

    @Test
    fun `given an outcome that stays unknown when admission runs then the gate stays closed and admission is refused`() = runTest {
        val helper = FakeCleanup(reconcileResults = mutableListOf(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN))
        val fixture = Fixture(helper = helper)

        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.CLEANUP_UNCERTAIN), fixture.coordinator.admit(TARGET_BUILD))
        assertTrue(fixture.gate.closed != null)
        assertFalse(fixture.gate.cycleAdmitted)
        assertFalse(helper.calls.contains("restore"))
    }

    @Test
    fun `given a live lease with no local session when admission runs then it is refused without restoring`() = runTest {
        val helper = FakeCleanup(statusResult = result(HelperResult.Outcome.Success, HelperResult.State.Ready, HelperResult.Phase.Applied))
        val fixture = Fixture(helper = helper)

        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.FOREIGN_LEASE), fixture.coordinator.admit(TARGET_BUILD))
        assertFalse(helper.calls.contains("restore"))
    }

    @Test
    fun `given restore that does not reach idle when admission runs then admission is refused as uncertain`() = runTest {
        listOf(
            result(HelperResult.Outcome.Conflict, HelperResult.State.Ready, HelperResult.Phase.RecoveryRequired),
            UNKNOWN,
            result(HelperResult.Outcome.Success, HelperResult.State.Ready, HelperResult.Phase.RestorePending),
        ).forEach { restore ->
            val fixture = Fixture(helper = FakeCleanup(restoreResult = restore))

            assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.CLEANUP_UNCERTAIN), fixture.coordinator.admit(TARGET_BUILD), "$restore")
            assertFalse(fixture.gate.cycleAdmitted)
        }
    }

    @Test
    fun `given a failing helper call when admission runs then admission is refused as uncertain`() = runTest {
        val fixture = Fixture(helper = FakeCleanup(failure = IllegalStateException("helper unavailable")))

        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.CLEANUP_UNCERTAIN), fixture.coordinator.admit(TARGET_BUILD))
    }

    @Test
    fun `given a service that was never registered when admission runs then stored proxies decide cleanup`() = runTest {
        val notRegistered = result(HelperResult.Outcome.ActionRequired, HelperResult.State.NotRegistered, HelperResult.Phase.RecoveryRequired)
        val clean = Fixture(helper = FakeCleanup(statusResult = notRegistered))
        val dirty = Fixture(helper = FakeCleanup(statusResult = notRegistered), proxies = StoredProxyEvidence.LOOPBACK_PROXY_ENABLED)
        val unreadable = Fixture(helper = FakeCleanup(statusResult = notRegistered), proxies = StoredProxyEvidence.UNREADABLE)

        assertEquals(AdmissionOutcome.Admitted, clean.coordinator.admit(TARGET_BUILD))
        assertFalse(clean.helper.calls.contains("restore"))
        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.CLEANUP_UNCERTAIN), dirty.coordinator.admit(TARGET_BUILD))
        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.CLEANUP_UNCERTAIN), unreadable.coordinator.admit(TARGET_BUILD))
    }

    @Test
    fun `given approval or recovery required when admission runs then it is refused as action required`() = runTest {
        listOf(HelperResult.State.ApprovalRequired, HelperResult.State.RecoveryRequired, HelperResult.State.UnavailableOrIncompatible).forEach { state ->
            val fixture = Fixture(helper = FakeCleanup(statusResult = result(HelperResult.Outcome.ActionRequired, state, HelperResult.Phase.RecoveryRequired)))

            assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.SERVICE_ACTION_REQUIRED), fixture.coordinator.admit(TARGET_BUILD), "$state")
        }
    }

    @Test
    fun `given a companion that does not stop when admission runs then admission is refused`() = runTest {
        val fixture = Fixture(companion = FakeCompanion(drains = false))

        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.SHUTDOWN_INCOMPLETE), fixture.coordinator.admit(TARGET_BUILD))
        assertFalse(fixture.gate.cycleAdmitted)
    }

    @Test
    fun `given a settled replacement and a ready idle service when release is evaluated then the gate reopens and maintenance ends`() = runTest {
        val fixture = Fixture(installer = InstallerObservation.TERMINATED, identity = BundleIdentity(TARGET_BUILD, TARGET_BUILD, signedByTeam = true))
        fixture.coordinator.admit(TARGET_BUILD)
        fixture.coordinator.onCycleEnded()

        assertEquals(MaintenanceReopenResult.Reopened, fixture.coordinator.evaluateRelease())
        assertTrue(fixture.helperMaintenance.allowsSpawns)
        assertFalse(fixture.helperMaintenance.isBackstopEngaged)
        assertFalse(fixture.companion.draining)
        assertTrue(fixture.lock.downgraded)
    }

    @Test
    fun `given a running installer when release is evaluated then maintenance mode stays on`() = runTest {
        val fixture = Fixture(installer = InstallerObservation.RUNNING, identity = BundleIdentity(FROM_BUILD, FROM_BUILD, signedByTeam = true))
        fixture.coordinator.admit(TARGET_BUILD)
        fixture.coordinator.onCycleEnded()

        assertEquals(MaintenanceReopenResult.EvidenceMissing, fixture.coordinator.evaluateRelease())
        assertFalse(fixture.helperMaintenance.allowsSpawns)
        assertTrue(fixture.companion.draining)
    }

    @Test
    fun `given a settled replacement but a service needing action when release is evaluated then spawns resume while the gate stays closed`() = runTest {
        val fixture = Fixture(
            installer = InstallerObservation.TERMINATED,
            identity = BundleIdentity(FROM_BUILD, FROM_BUILD, signedByTeam = true),
        )
        fixture.coordinator.admit(TARGET_BUILD)
        fixture.coordinator.onCycleEnded()
        fixture.helper.statusResult = result(HelperResult.Outcome.ActionRequired, HelperResult.State.ApprovalRequired, HelperResult.Phase.RecoveryRequired)

        assertEquals(MaintenanceReopenResult.EvidenceMissing, fixture.coordinator.evaluateRelease())
        assertTrue(fixture.helperMaintenance.allowsSpawns)
        assertTrue(fixture.helperMaintenance.isBackstopEngaged)
        assertTrue(fixture.gate.closed != null)
    }

    @Test
    fun `given a disabled service after a settled replacement when release is evaluated then the gate reopens and the service stays disabled`() = runTest {
        val notRegistered = result(HelperResult.Outcome.ActionRequired, HelperResult.State.NotRegistered, HelperResult.Phase.RecoveryRequired)
        val fixture = Fixture(
            helper = FakeCleanup(statusResult = notRegistered),
            installer = InstallerObservation.TERMINATED,
            identity = BundleIdentity(TARGET_BUILD, TARGET_BUILD, signedByTeam = true),
        )
        fixture.coordinator.admit(TARGET_BUILD)
        fixture.coordinator.onCycleEnded()

        assertEquals(MaintenanceReopenResult.Reopened, fixture.coordinator.evaluateRelease())
        assertFalse(fixture.helper.calls.contains("enable"))
    }

    @Test
    fun `given a closed gate at startup when maintenance is restored then spawns and companion traffic stop before anything else`() = runTest {
        val gate = FakeGate().apply { closed = ClosedMaintenanceGate(FROM_BUILD, TARGET_BUILD, 1L) }
        val fixture = Fixture(gate = gate)

        assertTrue(fixture.coordinator.restoreMaintenanceAtStartup())
        assertFalse(fixture.helperMaintenance.allowsSpawns)
        assertTrue(fixture.helperMaintenance.isBackstopEngaged)
        assertTrue(fixture.companion.draining)
    }

    @Test
    fun `given an open gate at startup when maintenance is restored then nothing changes`() = runTest {
        val fixture = Fixture()

        assertFalse(fixture.coordinator.restoreMaintenanceAtStartup())
        assertTrue(fixture.helperMaintenance.allowsSpawns)
        assertFalse(fixture.companion.draining)
    }

    @Test
    fun `given a closed gate after relaunch when a pending installation continues then the cycle is admitted without new cleanup`() = runTest {
        val gate = FakeGate().apply { closed = ClosedMaintenanceGate(FROM_BUILD, TARGET_BUILD, 1L) }
        val fixture = Fixture(gate = gate)
        fixture.coordinator.restoreMaintenanceAtStartup()

        assertEquals(AdmissionOutcome.Admitted, fixture.coordinator.admitPendingInstallation(TARGET_BUILD))
        assertTrue(gate.cycleAdmitted)
        assertTrue(fixture.helper.calls.isEmpty())
    }

    @Test
    fun `given an open gate when a pending installation continues then full admission runs first`() = runTest {
        val fixture = Fixture()

        assertEquals(AdmissionOutcome.Admitted, fixture.coordinator.admitPendingInstallation(TARGET_BUILD))
        assertEquals(listOf("status", "restore", "configure-empty"), fixture.helper.calls)
    }

    @Test
    fun `given a closed gate whose maintenance mode did not hold when a pending installation continues then it is refused`() = runTest {
        val gate = FakeGate().apply { closed = ClosedMaintenanceGate(FROM_BUILD, TARGET_BUILD, 1L) }
        val fixture = Fixture(gate = gate, companion = FakeCompanion(drains = false))
        fixture.coordinator.restoreMaintenanceAtStartup()

        assertEquals(AdmissionOutcome.Refused(AdmissionRefusal.SHUTDOWN_INCOMPLETE), fixture.coordinator.admitPendingInstallation(TARGET_BUILD))
        assertFalse(gate.cycleAdmitted)
    }

    private class Fixture(
        val gate: FakeGate = FakeGate(),
        val lock: FakeLock = FakeLock(),
        val helper: FakeCleanup = FakeCleanup(),
        val companion: FakeCompanion = FakeCompanion(),
        proxies: StoredProxyEvidence = StoredProxyEvidence.NO_LOOPBACK_PROXY,
        installer: InstallerObservation = InstallerObservation.RUNNING,
        identity: BundleIdentity = BundleIdentity(FROM_BUILD, FROM_BUILD, signedByTeam = true),
    ) {
        val helperMaintenance = HelperMaintenance()
        val coordinator = UpdateAdmissionCoordinator(
            runningBuild = FROM_BUILD,
            gate = gate,
            instanceLock = lock,
            helper = helper,
            helperMaintenance = helperMaintenance,
            companion = companion,
            storedProxies = { proxies },
            installer = { installer },
            bundleIdentity = { identity },
        )
    }

    private class FakeGate(
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

    private class FakeLock(
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

    private class FakeCompanion(
        private val drains: Boolean = true,
    ) : CompanionMaintenance {
        var draining = false

        override suspend fun drainForMaintenance(): Boolean {
            draining = true
            return drains
        }

        override fun resumeAfterMaintenance() {
            draining = false
        }
    }

    private class FakeCleanup(
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

    private companion object {
        const val FROM_BUILD: String = "8"
        const val TARGET_BUILD: String = "9"
        val SUCCESS_IDLE: HelperResult = result(HelperResult.Outcome.Success, HelperResult.State.Ready, HelperResult.Phase.Idle)
        val UNKNOWN: HelperResult = HelperResult.unknownOutcome()

        fun result(
            outcome: HelperResult.Outcome,
            state: HelperResult.State,
            phase: HelperResult.Phase,
        ): HelperResult {
            return HelperResult(outcome, state, phase, HelperResult.RequiredAction.None, HelperResult.Failure.None)
        }
    }
}
