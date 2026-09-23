package app.posato.desktop.update

import app.posato.desktop.macos.HelperMaintenance
import app.posato.desktop.macos.HelperResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateAdmissionRaceTest {
    @Test
    fun `given a release poll left by a refused cycle when a retried admission is draining then the poll cannot reopen maintenance`() = runTest {
        val gate = FakeGate()
        val approvalRequired = result(HelperResult.Outcome.ActionRequired, HelperResult.State.ApprovalRequired, HelperResult.Phase.RecoveryRequired)
        val helper = FakeCleanup(statusResult = approvalRequired)
        val companion = FakeCompanion()
        val helperMaintenance = HelperMaintenance()
        val lock = FakeLock()
        val replies = mutableListOf<Pair<Long, Boolean>>()
        val coordinator = UpdateAdmissionCoordinator(
            runningBuild = { FROM_BUILD },
            gate = gate,
            instanceLock = lock,
            helper = helper,
            helperMaintenance = helperMaintenance,
            companion = companion,
            storedProxies = { StoredProxyEvidence.NO_LOOPBACK_PROXY },
            installer = { InstallerObservation.TERMINATED },
            bundleIdentity = { BundleIdentity(FROM_BUILD, FROM_BUILD, signedByTeam = true) },
        )
        val controller = UpdaterController(this, coordinator) { token, granted, _ -> replies += token to granted }

        controller.onInstallRequested(FIRST_TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        runCurrent()
        assertEquals(listOf(FIRST_TOKEN to false), replies)

        helper.statusResult = SUCCESS_IDLE
        val drained = CompletableDeferred<Unit>()
        companion.beforeDrain = { drained.await() }
        controller.onInstallRequested(SECOND_TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        runCurrent()
        advanceTimeBy(RELEASE_POLL_MILLIS)
        runCurrent()
        drained.complete(Unit)
        runCurrent()

        assertEquals(SECOND_TOKEN to true, replies.last())
        assertNotNull(gate.closed)
        assertTrue(gate.cycleAdmitted)
        assertTrue(helperMaintenance.isBackstopEngaged)
        assertFalse(helperMaintenance.allowsSpawns)
        assertTrue(companion.draining)
        assertFalse(lock.downgraded)
        advanceUntilIdle()
        assertNotNull(gate.closed)
    }

    @Test
    fun `given an admission from an ended cycle that runs after a newer admission then it cannot reopen maintenance`() = runTest {
        val gate = FakeGate()
        val companion = FakeCompanion()
        val helperMaintenance = HelperMaintenance()
        val replies = mutableListOf<Pair<Long, Boolean>>()
        val coordinator = UpdateAdmissionCoordinator(
            runningBuild = { FROM_BUILD },
            gate = gate,
            instanceLock = FakeLock(),
            helper = FakeCleanup(),
            helperMaintenance = helperMaintenance,
            companion = companion,
            storedProxies = { StoredProxyEvidence.NO_LOOPBACK_PROXY },
            installer = { InstallerObservation.TERMINATED },
            bundleIdentity = { BundleIdentity(FROM_BUILD, FROM_BUILD, signedByTeam = true) },
        )
        val heldFirst = CompletableDeferred<Unit>()
        val delayedFirst = HoldFirstAdmission(coordinator, heldFirst)
        val controller = UpdaterController(this, delayedFirst) { token, granted, _ -> replies += token to granted }

        controller.onInstallRequested(FIRST_TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        runCurrent()
        controller.onCycleFinished()
        runCurrent()
        controller.onInstallRequested(SECOND_TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        runCurrent()
        assertEquals(listOf(SECOND_TOKEN to true), replies)
        heldFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(FIRST_TOKEN to false, replies.last())
        assertNotNull(gate.closed)
        assertTrue(gate.cycleAdmitted)
        assertTrue(helperMaintenance.isBackstopEngaged)
        assertTrue(companion.draining)
    }

    private class HoldFirstAdmission(
        private val delegate: UpdateAdmission,
        private val heldFirst: CompletableDeferred<Unit>,
    ) : UpdateAdmission by delegate {
        private var admissions = 0

        override suspend fun admit(
            targetBuild: String,
            cycle: Long,
        ): AdmissionOutcome {
            admissions += 1
            if (admissions == 1) {
                heldFirst.await()
            }
            return delegate.admit(targetBuild, cycle)
        }
    }

    private companion object {
        const val FROM_BUILD: String = "8"
        const val TARGET_BUILD: String = "9"
        const val FIRST_TOKEN: Long = 1L
        const val SECOND_TOKEN: Long = 2L
        const val RELEASE_POLL_MILLIS: Long = 2_001L
    }
}
