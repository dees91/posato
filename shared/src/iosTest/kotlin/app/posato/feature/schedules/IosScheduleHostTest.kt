package app.posato.feature.schedules

import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementOutcome
import app.posato.feature.enforcement.IosEnforcementProvider
import app.posato.feature.enforcement.IosEnforcementRequest
import app.posato.feature.schedules.host.StartGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class ScheduleStoreProvider(
    var status: IosEnforcementOutcome = IosEnforcementOutcome.CLEARED,
) : IosEnforcementProvider {
    val calls = mutableListOf<String>()

    override fun apply(
        request: IosEnforcementRequest,
        completion: (IosEnforcementOutcome) -> Unit,
    ) {
        calls += "apply"
        status = IosEnforcementOutcome.APPLIED
        completion(IosEnforcementOutcome.APPLIED)
    }

    override fun clear(handler: (IosEnforcementOutcome) -> Unit) {
        calls += "clear"
        status = IosEnforcementOutcome.CLEARED
        handler(IosEnforcementOutcome.CLEARED)
    }

    override fun status(handler: (IosEnforcementOutcome) -> Unit) {
        handler(status)
    }
}

class IosScheduleHostTest {
    @Test
    fun givenScreenTimeStatesThenOnlyAnApprovedPhoneMayStartAndAFailureIsRetried() = runTest {
        val cases = mapOf(
            IosEnforcementOutcome.CLEARED to StartGate.READY,
            IosEnforcementOutcome.APPLIED to StartGate.READY,
            IosEnforcementOutcome.AUTHORIZATION_REQUIRED to StartGate.SETUP_REQUIRED,
            IosEnforcementOutcome.AUTHORIZATION_DENIED to StartGate.SETUP_REQUIRED,
            IosEnforcementOutcome.RESTRICTED to StartGate.SETUP_REQUIRED,
            IosEnforcementOutcome.UNAVAILABLE to StartGate.SETUP_REQUIRED,
            IosEnforcementOutcome.PLATFORM_FAILURE to StartGate.TRANSIENT,
        )

        cases.forEach { (status, expected) ->
            assertEquals(expected, IosScheduleStartGate(IosEnforcement(ScheduleStoreProvider(status))).check(), "status $status")
        }
    }

    @Test
    fun givenTheScheduleStoreWhenClaimedAndReleasedThenOnlyAnAppliedStoreIsCleared() = runTest {
        val provider = ScheduleStoreProvider()
        val claims = IosScheduleClaims(IosEnforcement(provider))

        assertEquals(EnforcementOutcome.CLEARED, claims.releaseSchedule())
        assertEquals(
            EnforcementOutcome.APPLIED,
            claims.claimSchedule(EnforcementRequest(listOf("example.com"), emptyList(), "schedule", 0L, 1L)).outcome,
        )
        assertEquals(EnforcementOutcome.APPLIED, claims.scheduleStatus())
        assertEquals(EnforcementOutcome.CLEARED, claims.releaseSchedule())

        assertEquals(listOf("apply", "clear"), provider.calls)
    }
}
