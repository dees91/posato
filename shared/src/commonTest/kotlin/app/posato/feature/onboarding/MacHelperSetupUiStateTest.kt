package app.posato.feature.onboarding

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacHelperSetupUiStateTest {
    @Test
    fun `given an unchanged helper answer when checked again then completion remains observable`() = runTest {
        listOf(MacHelperReadiness.READY, MacHelperReadiness.UNAVAILABLE).forEach { answer ->
            val holder = MacHelperSetupUiState(RecordingMacHelper(answer), this)
            holder.check()
            runCurrent()
            val first = holder.presentation()

            holder.check()
            runCurrent()

            assertEquals(answer, holder.presentation().readiness)
            assertNotEquals(first, holder.presentation())
        }
    }

    @Test
    fun `given a fresh holder when nothing is pressed then the port is never called and readiness stays unknown`() = runTest {
        val helper = RecordingMacHelper()
        val holder = MacHelperSetupUiState(helper, this)

        runCurrent()

        assertTrue(helper.calls.isEmpty())
        assertEquals(MacSetupPresentation(), holder.presentation())
    }

    @Test
    fun `given each helper answer when checked then only recheck runs and the readiness is kept`() = runTest {
        MacHelperReadiness.entries.forEach { answer ->
            val helper = RecordingMacHelper(answer)
            val holder = MacHelperSetupUiState(helper, this)

            holder.check()
            runCurrent()

            assertEquals(listOf("recheck"), helper.calls, "answer $answer")
            assertEquals(MacSetupPresentation(readiness = answer, completedOperations = 1), holder.presentation())
        }
    }

    @Test
    fun `given each helper answer when enabled then only enable runs and the readiness is kept`() = runTest {
        MacHelperReadiness.entries.forEach { answer ->
            val helper = RecordingMacHelper(answer)
            val holder = MacHelperSetupUiState(helper, this)

            holder.enable()
            runCurrent()

            assertEquals(listOf("enable"), helper.calls, "answer $answer")
            assertEquals(answer, holder.readiness)
        }
    }

    @Test
    fun `given a ready helper when checked again then a later approval required or not enabled answer replaces it`() = runTest {
        listOf(MacHelperReadiness.APPROVAL_REQUIRED, MacHelperReadiness.NOT_ENABLED).forEach { later ->
            val helper = RecordingMacHelper(MacHelperReadiness.READY)
            val holder = MacHelperSetupUiState(helper, this)
            holder.check()
            runCurrent()
            assertEquals(MacHelperReadiness.READY, holder.readiness)

            helper.answer = later
            holder.check()
            runCurrent()

            assertEquals(later, holder.readiness, "later $later")
            assertEquals(listOf("recheck", "recheck"), helper.calls)
        }
    }

    @Test
    fun `given a running enable when the call completes later then the readiness is stored`() = runTest {
        val gate = CompletableDeferred<MacHelperReadiness>()
        val helper = RecordingMacHelper(gate = gate)
        val holder = MacHelperSetupUiState(helper, this)

        holder.enable()
        runCurrent()
        assertEquals(MacSetupActivity.ENABLING, holder.activity)

        gate.complete(MacHelperReadiness.RECOVERY_REQUIRED)
        runCurrent()

        assertEquals(null, holder.activity)
        assertEquals(MacHelperReadiness.RECOVERY_REQUIRED, holder.readiness)
        assertEquals(1, holder.presentation().completedOperations)
    }

    @Test
    fun `given a running check when enable is pressed then the second action is ignored and the activity names the check`() = runTest {
        val gate = CompletableDeferred<MacHelperReadiness>()
        val helper = RecordingMacHelper(gate = gate)
        val holder = MacHelperSetupUiState(helper, this)

        holder.check()
        holder.enable()
        runCurrent()

        assertEquals(MacSetupActivity.CHECKING, holder.activity)
        assertEquals(listOf("recheck"), helper.calls)
        gate.complete(MacHelperReadiness.READY)
        runCurrent()

        assertNull(holder.activity)
        assertEquals(MacHelperReadiness.READY, holder.readiness)
    }

    @Test
    fun `given approval required when settings are opened then only the settings call runs`() = runTest {
        val helper = RecordingMacHelper(MacHelperReadiness.APPROVAL_REQUIRED)
        val holder = MacHelperSetupUiState(helper, this)

        holder.openSettings()
        runCurrent()

        assertEquals(listOf("settings"), helper.calls)
        assertNull(holder.readiness)
    }
}

private class RecordingMacHelper(
    var answer: MacHelperReadiness = MacHelperReadiness.UNAVAILABLE,
    private val gate: CompletableDeferred<MacHelperReadiness>? = null,
) : MacHelperPort {
    val calls = mutableListOf<String>()

    override suspend fun enable(): MacHelperReadiness {
        calls.add("enable")
        return gate?.await() ?: answer
    }

    override suspend fun recheck(): MacHelperReadiness {
        calls.add("recheck")
        return gate?.await() ?: answer
    }

    override fun openApprovalSettings() {
        calls.add("settings")
    }
}
