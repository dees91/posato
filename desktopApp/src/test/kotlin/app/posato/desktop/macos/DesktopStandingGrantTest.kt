package app.posato.desktop.macos

import app.posato.feature.onboarding.MacStandingGrantState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopStandingGrantTest {
    @Test
    fun `given a lost revoke reply when turned off then the switch shows only what status confirms`() = runTest {
        val stillGranted = FakeGrantCommands(revoke = HelperResult.unknownOutcome(), states = listOf(HelperGrantState.On))
        val unreachable = FakeGrantCommands(revoke = HelperResult.unknownOutcome(), states = listOf(HelperGrantState.Unknown))

        assertEquals(MacStandingGrantState.ON, DesktopStandingGrant(stillGranted, Dispatchers.Unconfined).setEnabled(false))
        assertEquals(listOf("revoke", "state"), stillGranted.calls)
        assertEquals(MacStandingGrantState.UNKNOWN, DesktopStandingGrant(unreachable, Dispatchers.Unconfined).setEnabled(false))
    }

    @Test
    fun `given a failed prepare when turned on then no administrator prompt is requested`() = runTest {
        val commands = FakeGrantCommands(prepare = failure(HelperResult.Failure.Integrity), states = listOf(HelperGrantState.Off))

        assertEquals(MacStandingGrantState.OFF, DesktopStandingGrant(commands, Dispatchers.Unconfined).setEnabled(true))
        assertEquals(listOf("prepare", "state"), commands.calls)
    }

    @Test
    fun `given a prepared rule when turned on then the grant is requested and status confirms it`() = runTest {
        val commands = FakeGrantCommands(states = listOf(HelperGrantState.On))

        assertEquals(MacStandingGrantState.ON, DesktopStandingGrant(commands, Dispatchers.Unconfined).setEnabled(true))
        assertEquals(listOf("prepare", "grant", "state"), commands.calls)
    }

    @Test
    fun `given an older daemon when read then the switch is unsupported`() = runTest {
        val commands = FakeGrantCommands(states = listOf(HelperGrantState.Unsupported))

        assertEquals(MacStandingGrantState.UNSUPPORTED, DesktopStandingGrant(commands, Dispatchers.Unconfined).read())
    }
}

private fun success(): HelperResult {
    return HelperResult(
        HelperResult.Outcome.Success,
        HelperResult.State.Ready,
        HelperResult.Phase.Idle,
        HelperResult.RequiredAction.None,
        HelperResult.Failure.None,
    )
}

private fun failure(reason: HelperResult.Failure): HelperResult {
    return success().copy(outcome = HelperResult.Outcome.Failure, failure = reason)
}

private class FakeGrantCommands(
    private val prepare: HelperResult = success(),
    private val grant: HelperResult = success(),
    private val revoke: HelperResult = success(),
    states: List<HelperGrantState>,
) : MacStandingGrantCommands {
    private val remainingStates = ArrayDeque(states)
    val calls = mutableListOf<String>()

    override fun grantState(): HelperGrantState {
        calls += "state"
        return remainingStates.removeFirst()
    }

    override fun prepareGrant(): HelperResult {
        calls += "prepare"
        return prepare
    }

    override fun grant(): HelperResult {
        calls += "grant"
        return grant
    }

    override fun revokeGrant(): HelperResult {
        calls += "revoke"
        return revoke
    }
}
