package app.posato.feature.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacUnifiedSetupTest {
    @Test
    fun `given a fresh Mac when set up then blocking, approval, login and password run in order and it completes`() = runTest {
        val port = SetupPort(
            rechecks = mutableListOf(MacHelperReadiness.NOT_ENABLED, MacHelperReadiness.APPROVAL_REQUIRED, MacHelperReadiness.READY),
            enableAnswer = MacHelperReadiness.APPROVAL_REQUIRED,
        )
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertEquals(listOf("recheck", "enable", "settings", "recheck", "recheck", "login on", "grant read", "grant on"), port.calls)
        assertTrue(holder.presentation().setupComplete)
    }

    @Test
    fun `given finished steps when set up again then nothing is repeated`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, grant = MacStandingGrantState.ON)
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertEquals(listOf("recheck", "grant read"), port.calls)
        assertTrue(holder.presentation().setupComplete)
    }

    @Test
    fun `given approval that never comes then blocking keeps waiting for a bounded time and nothing else runs`() = runTest {
        val port = SetupPort(
            rechecks = mutableListOf(MacHelperReadiness.NOT_ENABLED),
            enableAnswer = MacHelperReadiness.APPROVAL_REQUIRED,
            steadyRecheck = MacHelperReadiness.APPROVAL_REQUIRED,
        )
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        val setup = holder.presentation().setup
        assertEquals(MacSetupStepStatus.WAITING_FOR_APPROVAL, setup?.blocking)
        assertFalse(setup?.running ?: true)
        assertFalse(port.calls.any { it.startsWith("login") || it.startsWith("grant") })
        assertTrue(port.calls.count { it == "recheck" } <= 100)
        assertFalse(holder.presentation().setupComplete)
    }

    @Test
    fun `given a session when set up is pressed then nothing runs`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY))
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = true)
        advanceUntilIdle()

        assertEquals(emptyList(), port.calls)
    }

    @Test
    fun `given a cancelled password then the password step needs attention and setup is incomplete`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, grantAfterRequest = MacStandingGrantState.OFF)
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertEquals(MacSetupStepStatus.NEEDS_ATTENTION, holder.presentation().setup?.password)
        assertFalse(holder.presentation().setupComplete)
    }

    @Test
    fun `given a ready helper with setup incomplete then the offer shows until it is dismissed`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), steadyRecheck = MacHelperReadiness.READY)
        val holder = MacHelperSetupUiState(port, this)

        holder.readQuietly()
        advanceUntilIdle()
        assertTrue(holder.presentation().offerVisible)

        holder.dismissOffer()

        assertFalse(holder.presentation().offerVisible)
        assertTrue(port.offerDismissed)
        val later = MacHelperSetupUiState(port, this)
        later.readQuietly()
        advanceUntilIdle()
        assertFalse(later.presentation().offerVisible)
    }
}

private class SetupPort(
    private val rechecks: MutableList<MacHelperReadiness>,
    private val enableAnswer: MacHelperReadiness = MacHelperReadiness.READY,
    private val steadyRecheck: MacHelperReadiness = MacHelperReadiness.READY,
    loginOn: Boolean = false,
    private var grant: MacStandingGrantState = MacStandingGrantState.OFF,
    private val grantAfterRequest: MacStandingGrantState = MacStandingGrantState.ON,
) : MacHelperPort {
    val calls = mutableListOf<String>()
    var offerDismissed = false

    override val loginItem: MacLoginItem = object : MacLoginItem {
        private val state = MutableStateFlow(loginOn)
        override val enabled: StateFlow<Boolean> = state

        override fun setEnabled(enabled: Boolean) {
            calls += if (enabled) "login on" else "login off"
            state.value = enabled
        }

        override fun refresh() = Unit
    }

    override val standingGrant: MacStandingGrant = object : MacStandingGrant {
        override suspend fun read(): MacStandingGrantState {
            calls += "grant read"
            return grant
        }

        override suspend fun setEnabled(enabled: Boolean): MacStandingGrantState {
            calls += if (enabled) "grant on" else "grant off"
            grant = grantAfterRequest
            return grant
        }
    }

    override suspend fun enable(): MacHelperReadiness {
        calls += "enable"
        return enableAnswer
    }

    override suspend fun recheck(): MacHelperReadiness {
        calls += "recheck"
        return rechecks.removeFirstOrNull() ?: steadyRecheck
    }

    override suspend fun remove(): MacHelperRemoval {
        return MacHelperRemoval.REMOVED
    }

    override fun openApprovalSettings() {
        calls += "settings"
    }

    override fun setupOfferDismissed(): Boolean {
        return offerDismissed
    }

    override fun dismissSetupOffer() {
        offerDismissed = true
    }
}
