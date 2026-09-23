package app.posato.desktop.update

import app.posato.feature.update.MaintenanceReopenResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdaterControllerTest {
    @Test
    fun `given an admitted new installation when requested then the matching reply grants it`() = runTest {
        val admission = FakeAdmission()
        val replies = RecordingReplies()
        val controller = controller(admission, replies)

        controller.onInstallRequested(TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        advanceUntilIdle()

        assertEquals(listOf(TOKEN to true), replies.replies)
        assertEquals(listOf("admit:0"), admission.calls)
    }

    @Test
    fun `given a refused installation when requested then the reply refuses it and release is re-evaluated`() = runTest {
        val admission = FakeAdmission(outcome = AdmissionOutcome.Refused(AdmissionRefusal.SESSION_ACTIVE))
        val replies = RecordingReplies()
        val controller = controller(admission, replies)

        controller.onInstallRequested(TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        advanceUntilIdle()

        assertEquals(listOf(TOKEN to false), replies.replies)
        assertTrue("evaluate" in admission.calls)
    }

    @Test
    fun `given admission that fails unexpectedly when requested then the reply refuses it instead of leaving the update waiting`() = runTest {
        val admission = FakeAdmission(beforeAdmit = { throw IllegalStateException("helper exploded") })
        val replies = RecordingReplies()
        val controller = controller(admission, replies)

        controller.onInstallRequested(TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        advanceUntilIdle()

        assertEquals(listOf(TOKEN to false), replies.replies)
        assertEquals(AdmissionRefusal.CLEANUP_UNCERTAIN, replies.refusals.single())
    }

    @Test
    fun `given a pending installation after relaunch when requested then it continues the closed gate`() = runTest {
        val admission = FakeAdmission()
        val replies = RecordingReplies()
        val controller = controller(admission, replies)

        controller.onInstallRequested(TOKEN, TARGET_BUILD, InstallRequestStage.PENDING_INSTALLATION)
        advanceUntilIdle()

        assertEquals(listOf("admit-pending:0"), admission.calls)
        assertEquals(listOf(TOKEN to true), replies.replies)
    }

    @Test
    fun `given the cycle ends while admission is running when admission completes then the cycle is ended again and not granted`() = runTest {
        val release = CompletableDeferred<Unit>()
        val admission = FakeAdmission(beforeAdmit = { release.await() })
        val replies = RecordingReplies()
        val controller = controller(admission, replies)

        controller.onInstallRequested(TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        runCurrent()
        controller.onCycleFinished()
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(TOKEN to false), replies.replies)
        assertEquals("cycle-ended:0", admission.calls.last { call -> call != "evaluate" })
    }

    @Test
    fun `given the cycle finishes when reported then the cycle ends and release is evaluated until the gate opens`() = runTest {
        val admission = FakeAdmission(releaseResults = mutableListOf(MaintenanceReopenResult.EvidenceMissing, MaintenanceReopenResult.Reopened))
        val controller = controller(admission, RecordingReplies())

        controller.onCycleFinished()
        advanceUntilIdle()

        assertEquals(listOf("cycle-ended:0", "evaluate", "evaluate"), admission.calls)
    }

    @Test
    fun `given consecutive cycles when each finishes then admission and cycle end carry the same cycle id`() = runTest {
        val admission = FakeAdmission()
        val controller = controller(admission, RecordingReplies())

        controller.onInstallRequested(TOKEN, TARGET_BUILD, InstallRequestStage.NEW_INSTALLATION)
        advanceUntilIdle()
        controller.onCycleFinished()
        advanceUntilIdle()
        controller.onInstallRequested(TOKEN + 1, TARGET_BUILD, InstallRequestStage.PENDING_INSTALLATION)
        advanceUntilIdle()
        controller.onCycleFinished()
        advanceUntilIdle()

        assertEquals(
            listOf("admit:0", "cycle-ended:0", "admit-pending:1", "cycle-ended:1"),
            admission.calls.filter { call -> call != "evaluate" },
        )
    }

    @Test
    fun `given a closed gate at startup when started then release is evaluated`() = runTest {
        val admission = FakeAdmission(closedAtStartup = true, releaseResults = mutableListOf(MaintenanceReopenResult.Reopened))
        val controller = controller(admission, RecordingReplies())

        controller.start()
        advanceUntilIdle()

        assertEquals(listOf("restore-startup", "evaluate"), admission.calls)
    }

    private fun TestScope.controller(
        admission: FakeAdmission,
        replies: RecordingReplies,
    ): UpdaterController {
        return UpdaterController(this, admission, replies)
    }

    private class RecordingReplies : UpdaterReplies {
        val replies = mutableListOf<Pair<Long, Boolean>>()
        val refusals = mutableListOf<AdmissionRefusal?>()

        override fun completeAdmission(
            token: Long,
            granted: Boolean,
            refusal: AdmissionRefusal?,
        ) {
            replies += token to granted
            refusals += refusal
        }
    }

    private class FakeAdmission(
        private val outcome: AdmissionOutcome = AdmissionOutcome.Admitted,
        private val beforeAdmit: suspend () -> Unit = {},
        private val closedAtStartup: Boolean = false,
        private val releaseResults: MutableList<MaintenanceReopenResult> = mutableListOf(MaintenanceReopenResult.AlreadyOpen),
    ) : UpdateAdmission {
        val calls = mutableListOf<String>()

        override suspend fun admit(
            targetBuild: String,
            cycle: Long,
        ): AdmissionOutcome {
            beforeAdmit()
            calls += "admit:$cycle"
            return outcome
        }

        override suspend fun admitPendingInstallation(
            targetBuild: String,
            cycle: Long,
        ): AdmissionOutcome {
            calls += "admit-pending:$cycle"
            return outcome
        }

        override suspend fun onCycleEnded(cycle: Long) {
            calls += "cycle-ended:$cycle"
        }

        override suspend fun evaluateRelease(): MaintenanceReopenResult {
            calls += "evaluate"
            return if (releaseResults.size > 1) releaseResults.removeAt(0) else releaseResults.first()
        }

        override suspend fun restoreMaintenanceAtStartup(): Boolean {
            calls += "restore-startup"
            return closedAtStartup
        }
    }

    private companion object {
        const val TOKEN: Long = 7L
        const val TARGET_BUILD: String = "9"
    }
}
