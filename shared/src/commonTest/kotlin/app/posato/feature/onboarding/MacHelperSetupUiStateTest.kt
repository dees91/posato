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
    fun `given the same unresolved answer twice when checked again then the repeat is reported`() = runTest {
        val holder = MacHelperSetupUiState(RecordingMacHelper(MacHelperReadiness.RECOVERY_REQUIRED), this)

        holder.check()
        runCurrent()
        assertEquals(false, holder.presentation().repeatedResult)

        holder.check()
        runCurrent()

        assertEquals(true, holder.presentation().repeatedResult)
    }

    @Test
    fun `given a failing enable and a check reporting not enabled then the returning answer is a repeat`() = runTest {
        val helper = RecordingMacHelper(MacHelperReadiness.UNAVAILABLE)
        val holder = MacHelperSetupUiState(helper, this)

        holder.enable()
        runCurrent()
        helper.answer = MacHelperReadiness.NOT_ENABLED
        holder.check()
        runCurrent()
        assertEquals(false, holder.presentation().repeatedResult)

        helper.answer = MacHelperReadiness.UNAVAILABLE
        holder.enable()
        runCurrent()

        assertEquals(true, holder.presentation().repeatedResult)
    }

    @Test
    fun `given a ready answer after repeated failures then the repeat memory is cleared`() = runTest {
        val helper = RecordingMacHelper(MacHelperReadiness.RECOVERY_REQUIRED)
        val holder = MacHelperSetupUiState(helper, this)

        holder.check()
        runCurrent()
        holder.check()
        runCurrent()
        helper.answer = MacHelperReadiness.READY
        holder.check()
        runCurrent()
        assertEquals(false, holder.presentation().repeatedResult)

        helper.answer = MacHelperReadiness.RECOVERY_REQUIRED
        holder.check()
        runCurrent()

        assertEquals(false, holder.presentation().repeatedResult)
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
    fun `given a session that blocks removal when remove is pressed then the port is never called`() = runTest {
        val helper = RecordingMacHelper(MacHelperReadiness.READY)
        val holder = MacHelperSetupUiState(helper, this)

        holder.remove(sessionBlocked = true)
        runCurrent()

        assertTrue(helper.calls.isEmpty())
        assertNull(holder.activity)
        assertNull(holder.removal)
    }

    @Test
    fun `given a verified removal when removed then not enabled is offered and the removal is kept`() = runTest {
        val helper = RecordingMacHelper(MacHelperReadiness.READY)
        val holder = MacHelperSetupUiState(helper, this)
        holder.check()
        runCurrent()

        holder.remove(sessionBlocked = false)
        runCurrent()

        assertEquals(listOf("recheck", "remove"), helper.calls)
        assertEquals(MacHelperRemoval.REMOVED, holder.removal)
        assertEquals(MacHelperReadiness.NOT_ENABLED, holder.readiness)
        assertEquals(2, holder.presentation().completedOperations)
    }

    @Test
    fun `given each removal result when removed then the readiness follows the named next action`() = runTest {
        val expected = mapOf(
            MacHelperRemoval.REMOVED to MacHelperReadiness.NOT_ENABLED,
            MacHelperRemoval.NOT_ENABLED to MacHelperReadiness.NOT_ENABLED,
            MacHelperRemoval.APPROVAL_REQUIRED to MacHelperReadiness.APPROVAL_REQUIRED,
            MacHelperRemoval.CANNOT_START to MacHelperReadiness.RECOVERY_REQUIRED,
            MacHelperRemoval.REMOVE_AGAIN to MacHelperReadiness.READY,
            MacHelperRemoval.UNCERTAIN to MacHelperReadiness.READY,
            MacHelperRemoval.CHECK_AGAIN to MacHelperReadiness.READY,
            MacHelperRemoval.PROXY_ATTENTION to MacHelperReadiness.READY,
        )
        MacHelperRemoval.entries.forEach { result ->
            val helper = RecordingMacHelper(MacHelperReadiness.READY, removalAnswer = result)
            val holder = MacHelperSetupUiState(helper, this)
            holder.check()
            runCurrent()

            holder.remove(sessionBlocked = false)
            runCurrent()

            assertEquals(expected.getValue(result), holder.readiness, "result $result")
            assertEquals(result, holder.removal)
            assertEquals(listOf("recheck", "remove"), helper.calls, "no follow-up call may re-enable after $result")
        }
    }

    @Test
    fun `given a removal result when checked again then the removal result is cleared`() = runTest {
        val helper = RecordingMacHelper(MacHelperReadiness.READY, removalAnswer = MacHelperRemoval.REMOVE_AGAIN)
        val holder = MacHelperSetupUiState(helper, this)
        holder.remove(sessionBlocked = false)
        runCurrent()
        assertEquals(MacHelperRemoval.REMOVE_AGAIN, holder.removal)

        holder.check()
        runCurrent()

        assertNull(holder.removal)
    }

    @Test
    fun `given a running check when remove is pressed then the removal is ignored`() = runTest {
        val gate = CompletableDeferred<MacHelperReadiness>()
        val helper = RecordingMacHelper(gate = gate)
        val holder = MacHelperSetupUiState(helper, this)

        holder.check()
        holder.remove(sessionBlocked = false)
        runCurrent()

        assertEquals(MacSetupActivity.CHECKING, holder.activity)
        assertEquals(listOf("recheck"), helper.calls)
        gate.complete(MacHelperReadiness.READY)
        runCurrent()
        assertNull(holder.removal)
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
    private val removalAnswer: MacHelperRemoval = MacHelperRemoval.REMOVED,
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

    override suspend fun remove(): MacHelperRemoval {
        calls.add("remove")
        return removalAnswer
    }

    override fun openApprovalSettings() {
        calls.add("settings")
    }
}
