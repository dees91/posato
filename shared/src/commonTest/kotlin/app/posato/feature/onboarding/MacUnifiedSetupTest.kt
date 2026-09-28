package app.posato.feature.onboarding

import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.ui.SessionUiState
import app.posato.feature.session.ui.showsMacSetup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacUnifiedSetupTest {
    @Test
    fun `given a fresh Mac when set up then blocking then approval then login then password run in order and it completes`() = runTest {
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

class MacUnifiedSetupGuardTest {
    @Test
    fun `given a session that starts during setup then the password step is not requested`() = runTest {
        var busy = false
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), onLoginOn = { busy = true })
        val holder = MacHelperSetupUiState(port, this, sessionBusy = { busy })

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertFalse(port.calls.contains("grant on"))
        assertFalse(holder.presentation().setupComplete)
    }

    @Test
    fun `given setup running then check and enable and remove and the grant switch do nothing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), gate = gate)
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        runCurrent()
        holder.check()
        holder.enable()
        holder.remove(sessionBlocked = false)
        holder.setStandingGrant(enabled = true, sessionBlocked = false)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, port.calls.count { it == "recheck" })
        assertFalse(port.calls.contains("enable") || port.calls.contains("remove"))
    }

    @Test
    fun `given an old daemon or an unknown grant then setup is never complete and no offer shows`() = runTest {
        listOf(MacStandingGrantState.UNSUPPORTED, MacStandingGrantState.UNKNOWN).forEach { grant ->
            val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, grant = grant, grantAfterRequest = grant)
            val holder = MacHelperSetupUiState(port, this)

            holder.setUp(sessionBlocked = false)
            advanceUntilIdle()

            assertFalse(holder.presentation().setupComplete, "grant $grant")
            assertFalse(holder.presentation().offerVisible, "grant $grant")
        }
    }

    @Test
    fun `given a login item that stays off then the login step needs attention`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginSticksOff = true, grant = MacStandingGrantState.ON)
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertEquals(MacSetupStepStatus.NEEDS_ATTENTION, holder.presentation().setup?.login)
        assertFalse(holder.presentation().setupComplete)
    }

    @Test
    fun `given verified completion then the offer is marked as handled`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY))
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertTrue(holder.presentation().setupComplete)
        assertTrue(port.offerDismissed)
    }

    @Test
    fun `given a stored grant when the run has not read it yet then this Mac is not ready`() = runTest {
        val readGate = CompletableDeferred<Unit>()
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), grant = MacStandingGrantState.ON, readGate = readGate)
        val holder = MacHelperSetupUiState(port, this)
        holder.readQuietly()
        advanceUntilIdle()

        holder.setUp(sessionBlocked = false)
        runCurrent()

        assertFalse(holder.presentation().setupComplete)
        readGate.complete(Unit)
        advanceUntilIdle()
        assertTrue(holder.presentation().setupComplete)
    }

    @Test
    fun `given a finished run when Session reads quietly then the run's result stays`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY, MacHelperReadiness.NOT_ENABLED))
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()
        holder.readQuietly()
        advanceUntilIdle()

        assertEquals(1, port.calls.count { it == "recheck" })
        assertTrue(holder.presentation().setupComplete)
    }
}

class MacSetupInterruptionTest {
    @Test
    fun `given a session that starts while blocking is enabled then System Settings is not opened and nothing else runs`() = runTest {
        var busy = false
        val port = SetupPort(
            rechecks = mutableListOf(MacHelperReadiness.NOT_ENABLED),
            enableAnswer = MacHelperReadiness.APPROVAL_REQUIRED,
            onEnable = { busy = true },
        )
        val holder = MacHelperSetupUiState(port, this, sessionBusy = { busy })

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertFalse(port.calls.contains("settings"))
        assertFalse(port.calls.any { it.startsWith("login") || it.startsWith("grant") })
        assertFalse(holder.presentation().setup?.running ?: true)
    }

    @Test
    fun `given setup deferred while waiting for approval then later steps never run`() = runTest {
        val port = SetupPort(
            rechecks = mutableListOf(MacHelperReadiness.NOT_ENABLED),
            enableAnswer = MacHelperReadiness.APPROVAL_REQUIRED,
            steadyRecheck = MacHelperReadiness.APPROVAL_REQUIRED,
        )
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceTimeBy(5_000)
        holder.deferSetup()
        val rechecksAtDeferral = port.calls.count { it == "recheck" }
        advanceUntilIdle()

        assertFalse(holder.presentation().setup?.running ?: true)
        assertFalse(port.calls.any { it.startsWith("login") || it.startsWith("grant") })
        // A recheck can install a missing rule, so none may run after the person deferred.
        assertEquals(rechecksAtDeferral, port.calls.count { it == "recheck" })
        assertEquals(MacSetupStepStatus.WAITING_FOR_APPROVAL, holder.presentation().setup?.blocking)
    }

    @Test
    fun `given opening at login turned off in System Settings when the window becomes active then this Mac is no longer ready`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), grant = MacStandingGrantState.ON)
        val holder = MacHelperSetupUiState(port, this)
        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()
        assertTrue(holder.presentation().setupComplete)

        port.revokeLoginInSystemSettings()
        holder.readQuietly(refresh = true)
        advanceUntilIdle()

        assertFalse(holder.presentation().setupComplete)
    }

    @Test
    fun `given a session starting before the password step then the step stays pending rather than refused`() = runTest {
        var busy = false
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), onLoginOn = { busy = true })
        val holder = MacHelperSetupUiState(port, this, sessionBusy = { busy })

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertEquals(MacSetupStepStatus.PENDING, holder.presentation().setup?.password)
    }

    @Test
    fun `given repeated window activations then state is reread at most once in flight and once per interval`() = runTest {
        var elapsed = 0L
        val gate = CompletableDeferred<Unit>()
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), grant = MacStandingGrantState.ON, gate = gate)
        val holder = MacHelperSetupUiState(port, this, elapsedMillis = { elapsed })

        holder.readQuietly(refresh = true)
        holder.readQuietly(refresh = true)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, port.calls.count { it == "recheck" })

        elapsed = 5_000
        holder.readQuietly(refresh = true)
        holder.readQuietly(refresh = true)
        runCurrent()
        assertEquals(1, port.calls.count { it == "recheck" })

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, port.calls.count { it == "recheck" })
    }

    @Test
    fun `given a quick return after changing a setting then the throttled refresh still reads it when the interval ends`() = runTest {
        var elapsed = 0L
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), grant = MacStandingGrantState.ON, loginOn = true)
        val holder = MacHelperSetupUiState(port, this, elapsedMillis = { elapsed })
        holder.readQuietly(refresh = true)
        runCurrent()
        assertTrue(holder.presentation().setupComplete)

        port.revokeLoginInSystemSettings()
        elapsed = 3_000
        holder.readQuietly(refresh = true)
        runCurrent()
        assertTrue(holder.presentation().setupComplete)

        advanceTimeBy(30_000)
        runCurrent()
        assertFalse(holder.presentation().setupComplete)
    }

    @Test
    fun `given the person opened setup then it stays open after a partial run until they leave it`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, grantAfterRequest = MacStandingGrantState.OFF)
        val holder = MacHelperSetupUiState(port, this)
        val settingUp = SessionUiState(status = LocalSessionStatus.Inactive, isSettingUp = true)

        holder.setupOpen = true
        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertTrue(settingUp.showsMacSetup(holder.presentation()))
        holder.setupOpen = false
        assertFalse(settingUp.showsMacSetup(holder.presentation()))
    }
}

class MacAutomaticStartConsentTest {
    private fun readyPort(grant: MacStandingGrantState = MacStandingGrantState.ON): SetupPort {
        return SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, grant = grant)
    }

    @Test
    fun `given set up is pressed when it completes then the consent is recorded and the Mac is ready for schedules`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY))
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertTrue(port.consent)
        assertTrue(holder.presentation().setupComplete)
        assertTrue(holder.presentation().readyForSchedules)
    }

    @Test
    fun `given a grant turned on without the consent then the Mac is ready for pauses but not for schedules`() = runTest {
        val port = readyPort()
        val holder = MacHelperSetupUiState(port, this)

        holder.readQuietly()
        advanceUntilIdle()

        assertTrue(holder.presentation().setupComplete)
        assertFalse(holder.presentation().readyForSchedules)
        assertTrue(holder.presentation().schedulesNeedConsent)
    }

    @Test
    fun `given the Schedules card when schedules are allowed then the consent is recorded without calling the helper`() = runTest {
        val port = readyPort()
        val holder = MacHelperSetupUiState(port, this)
        holder.readQuietly()
        advanceUntilIdle()
        val before = port.calls.toList()

        holder.consent.allow(holder.presentation())

        assertEquals(before, port.calls)
        assertTrue(port.consent)
        assertTrue(holder.presentation().readyForSchedules)
    }

    @Test
    fun `given setup is not complete when schedules are allowed then nothing is recorded`() = runTest {
        val port = readyPort(grant = MacStandingGrantState.OFF)
        val holder = MacHelperSetupUiState(port, this)
        holder.readQuietly()
        advanceUntilIdle()

        holder.consent.allow(holder.presentation())

        assertFalse(port.consent)
        assertFalse(holder.presentation().readyForSchedules)
    }

    @Test
    fun `given a recorded consent when a read finds the grant off or unknown or unsupported then it is cleared`() = runTest {
        listOf(MacStandingGrantState.OFF, MacStandingGrantState.UNKNOWN, MacStandingGrantState.UNSUPPORTED).forEach { grant ->
            val port = readyPort(grant = grant).apply { consent = true }
            val holder = MacHelperSetupUiState(port, this)

            holder.readQuietly()
            advanceUntilIdle()

            assertFalse(port.consent, "grant $grant")
            assertFalse(holder.presentation().readyForSchedules, "grant $grant")
        }
    }

    @Test
    fun `given a recorded consent when the helper is not ready then the consent is kept but does not count`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.APPROVAL_REQUIRED), steadyRecheck = MacHelperReadiness.APPROVAL_REQUIRED)
            .apply { consent = true }
        val holder = MacHelperSetupUiState(port, this)

        holder.check()
        advanceUntilIdle()

        assertTrue(port.consent)
        assertFalse(holder.presentation().readyForSchedules)
    }

    @Test
    fun `given a recorded consent when the grant is turned off or the helper removed then it is cleared`() = runTest {
        val switched = readyPort().apply { consent = true }
        val switchedHolder = MacHelperSetupUiState(switched, this)
        switchedHolder.readQuietly()
        advanceUntilIdle()
        switchedHolder.setStandingGrant(enabled = false, sessionBlocked = false)
        advanceUntilIdle()
        assertFalse(switched.consent)

        val removed = readyPort().apply { consent = true }
        val removedHolder = MacHelperSetupUiState(removed, this)
        removedHolder.readQuietly()
        advanceUntilIdle()
        removedHolder.remove(sessionBlocked = false)
        advanceUntilIdle()
        assertFalse(removed.consent)
    }

    @Test
    fun `given the offer when it is dismissed then no consent is recorded`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), steadyRecheck = MacHelperReadiness.READY)
        val holder = MacHelperSetupUiState(port, this)
        holder.readQuietly()
        advanceUntilIdle()

        holder.dismissOffer()

        assertFalse(port.consent)
    }

    @Test
    fun `given a quiet read in flight when setup starts then the read's stale grant never clears the new consent`() = runTest {
        val firstRead = CompletableDeferred<Unit>()
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, firstReadGate = firstRead)
        val holder = MacHelperSetupUiState(port, this)

        holder.readQuietly()
        runCurrent()
        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()
        firstRead.complete(Unit)
        advanceUntilIdle()

        assertTrue(port.consent)
        assertTrue(holder.presentation().readyForSchedules)
    }

    @Test
    fun `given a setup run that does not finish then the consent it recorded is withdrawn`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, grantAfterRequest = MacStandingGrantState.OFF)
        val holder = MacHelperSetupUiState(port, this)

        holder.setUp(sessionBlocked = false)
        advanceUntilIdle()

        assertFalse(port.consent)
        assertFalse(holder.presentation().readyForSchedules)
    }

    @Test
    fun `given a setup run cancelled with the window then the consent it recorded is withdrawn`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val port = SetupPort(rechecks = mutableListOf(MacHelperReadiness.READY), loginOn = true, gate = gate)
        val window = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        val holder = MacHelperSetupUiState(port, window)

        holder.setUp(sessionBlocked = false)
        runCurrent()
        assertTrue(port.consent)
        window.cancel()
        runCurrent()

        assertFalse(port.consent)
    }

    @Test
    fun `given a recorded consent when a read finds the helper not enabled then it is cleared`() = runTest {
        val port = SetupPort(rechecks = mutableListOf(), steadyRecheck = MacHelperReadiness.NOT_ENABLED).apply { consent = true }
        val holder = MacHelperSetupUiState(port, this)

        holder.check()
        advanceUntilIdle()

        assertFalse(port.consent)
    }
}

private class SetupPort(
    private val rechecks: MutableList<MacHelperReadiness>,
    private val enableAnswer: MacHelperReadiness = MacHelperReadiness.READY,
    private val steadyRecheck: MacHelperReadiness = MacHelperReadiness.READY,
    loginOn: Boolean = false,
    private var grant: MacStandingGrantState = MacStandingGrantState.OFF,
    private val grantAfterRequest: MacStandingGrantState = MacStandingGrantState.ON,
    private val onLoginOn: () -> Unit = {},
    private val loginSticksOff: Boolean = false,
    private val gate: CompletableDeferred<Unit>? = null,
    private val readGate: CompletableDeferred<Unit>? = null,
    private val onEnable: () -> Unit = {},
    private val firstReadGate: CompletableDeferred<Unit>? = null,
) : MacHelperPort {
    val calls = mutableListOf<String>()
    private var systemLogin: Boolean? = null

    /** Turns opening at login off in System Settings; the app sees it only after a refresh. */
    fun revokeLoginInSystemSettings() {
        systemLogin = false
    }

    var offerDismissed = false

    override val loginItem: MacLoginItem = object : MacLoginItem {
        private val state = MutableStateFlow(loginOn)
        override val enabled: StateFlow<Boolean> = state

        override fun setEnabled(enabled: Boolean) {
            calls += if (enabled) "login on" else "login off"
            state.value = enabled && !loginSticksOff
            if (enabled) onLoginOn()
        }

        override fun refresh() {
            systemLogin?.let { state.value = it }
        }
    }

    override val standingGrant: MacStandingGrant = object : MacStandingGrant {
        override suspend fun read(): MacStandingGrantState {
            calls += "grant read"
            if (calls.count { it == "grant read" } > 1) {
                readGate?.await()
            } else if (firstReadGate != null) {
                // Answers what it saw before waiting, like a reply that arrives late.
                val seen = grant
                firstReadGate.await()
                return seen
            }
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
        onEnable()
        return enableAnswer
    }

    override suspend fun recheck(): MacHelperReadiness {
        calls += "recheck"
        gate?.await()
        return rechecks.removeFirstOrNull() ?: steadyRecheck
    }

    override suspend fun remove(): MacHelperRemoval {
        calls += "remove"
        return MacHelperRemoval.REMOVED
    }

    override fun openApprovalSettings() {
        calls += "settings"
    }

    override fun setupOfferDismissed(): Boolean {
        return offerDismissed
    }

    private val storedConsent = MutableStateFlow(false)
    var consent: Boolean
        get() = storedConsent.value
        set(value) {
            storedConsent.value = value
        }

    override val automaticStartConsent: MacAutomaticStartConsent = object : MacAutomaticStartConsent {
        override val given: StateFlow<Boolean> = storedConsent

        override fun record(given: Boolean) {
            storedConsent.value = given
        }
    }

    override fun dismissSetupOffer() {
        offerDismissed = true
    }
}
