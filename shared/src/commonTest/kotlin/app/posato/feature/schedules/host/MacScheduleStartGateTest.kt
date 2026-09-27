package app.posato.feature.schedules.host

import app.posato.feature.onboarding.MacAutomaticStartConsent
import app.posato.feature.onboarding.MacHelperOperations
import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.MacHelperReadiness
import app.posato.feature.onboarding.MacHelperRemoval
import app.posato.feature.onboarding.MacLoginItem
import app.posato.feature.onboarding.MacStandingGrant
import app.posato.feature.onboarding.MacStandingGrantState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class GateMac(
    var readiness: MacHelperReadiness = MacHelperReadiness.READY,
    var grant: MacStandingGrantState = MacStandingGrantState.ON,
    consent: Boolean = true,
    login: Boolean = true,
) : MacHelperPort {
    val calls = mutableListOf<String>()
    private val stored = MutableStateFlow(consent)
    override val operations: MacHelperOperations = MacHelperOperations()

    override val automaticStartConsent: MacAutomaticStartConsent = object : MacAutomaticStartConsent {
        override val given: StateFlow<Boolean> = stored

        override fun record(given: Boolean) {
            stored.value = given
        }
    }

    override val loginItem: MacLoginItem = object : MacLoginItem {
        override val enabled: StateFlow<Boolean> = MutableStateFlow(login)

        override fun setEnabled(enabled: Boolean) = Unit

        override fun refresh() = Unit
    }

    override val standingGrant: MacStandingGrant = object : MacStandingGrant {
        override suspend fun read(): MacStandingGrantState {
            calls += "grant read"
            return grant
        }

        override suspend fun setEnabled(enabled: Boolean): MacStandingGrantState {
            return grant
        }
    }

    val consent: Boolean
        get() = stored.value

    override suspend fun status(): MacHelperReadiness {
        calls += "status"
        return readiness
    }

    override suspend fun enable(): MacHelperReadiness = readiness

    override suspend fun recheck(): MacHelperReadiness = readiness

    override suspend fun remove(): MacHelperRemoval = MacHelperRemoval.REMOVED

    override fun openApprovalSettings() = Unit
}

class MacScheduleStartGateTest {
    @Test
    fun `given a ready Mac with the consent then a start may run`() = runTest {
        assertEquals(StartGate.READY, MacScheduleStartGate(GateMac()) { true }.check())
    }

    @Test
    fun `given another account at the console then the start waits and nothing is read`() = runTest {
        val mac = GateMac()

        assertEquals(StartGate.OTHER_ACCOUNT, MacScheduleStartGate(mac) { false }.check())
        assertTrue(mac.calls.isEmpty())
        assertTrue(mac.consent)
    }

    @Test
    fun `given an unreadable console then the start proceeds`() = runTest {
        assertEquals(StartGate.READY, MacScheduleStartGate(GateMac()) { null }.check())
    }

    @Test
    fun `given no consent then setup is required without asking the helper`() = runTest {
        val mac = GateMac(consent = false)

        assertEquals(StartGate.SETUP_REQUIRED, MacScheduleStartGate(mac) { true }.check())
        assertTrue(mac.calls.isEmpty())
    }

    @Test
    fun `given the grant read as off or unsupported then setup is required and the consent is withdrawn`() = runTest {
        listOf(MacStandingGrantState.OFF, MacStandingGrantState.UNSUPPORTED).forEach { grant ->
            val mac = GateMac(grant = grant)

            assertEquals(StartGate.SETUP_REQUIRED, MacScheduleStartGate(mac) { true }.check(), "grant $grant")
            assertFalse(mac.consent, "grant $grant")
        }
    }

    @Test
    fun `given a grant that could not be read or a helper not answering then it is transient and the consent stays`() = runTest {
        val unknown = GateMac(grant = MacStandingGrantState.UNKNOWN)
        assertEquals(StartGate.TRANSIENT, MacScheduleStartGate(unknown) { true }.check())
        assertTrue(unknown.consent)

        listOf(MacHelperReadiness.UNAVAILABLE, MacHelperReadiness.UNCERTAIN).forEach { readiness ->
            val silent = GateMac(readiness = readiness)
            assertEquals(StartGate.TRANSIENT, MacScheduleStartGate(silent) { true }.check())
            assertFalse(silent.calls.contains("grant read"))
            assertTrue(silent.consent)
        }
    }

    @Test
    fun `given the helper not enabled then setup is required and the consent is withdrawn`() = runTest {
        val mac = GateMac(readiness = MacHelperReadiness.NOT_ENABLED)

        assertEquals(StartGate.SETUP_REQUIRED, MacScheduleStartGate(mac) { true }.check())
        assertFalse(mac.consent)
    }

    @Test
    fun `given a helper operation the person started then the start waits for it`() = runTest {
        val mac = GateMac()
        val release = CompletableDeferred<Unit>()
        val running = launch { mac.operations.track { release.await() } }
        runCurrent()

        assertEquals(StartGate.TRANSIENT, MacScheduleStartGate(mac) { true }.check())
        release.complete(Unit)
        running.join()
        assertEquals(StartGate.READY, MacScheduleStartGate(mac) { true }.check())
    }
}
