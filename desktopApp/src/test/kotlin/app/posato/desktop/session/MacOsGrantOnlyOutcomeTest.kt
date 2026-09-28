package app.posato.desktop.session

import app.posato.desktop.macos.HelperResult
import app.posato.feature.enforcement.EnforcementOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class MacOsGrantOnlyOutcomeTest {
    @Test
    fun `given each scheduled start failure then only an unusable grant means setup and a helper that is not ready is retried`() {
        assertEquals(EnforcementOutcome.AUTHORIZATION_REQUIRED, failure(HelperResult.Failure.StandingGrantUnavailable).grantOnlyOutcome())
        assertEquals(
            EnforcementOutcome.UNAVAILABLE,
            failure(HelperResult.Failure.None, HelperResult.State.NotRegistered).grantOnlyOutcome(),
        )
        assertEquals(EnforcementOutcome.FAILED, failure(HelperResult.Failure.InvalidInput).grantOnlyOutcome())
    }

    private fun failure(
        failure: HelperResult.Failure,
        serviceState: HelperResult.State = HelperResult.State.Ready,
    ): HelperResult {
        return HelperResult(
            outcome = HelperResult.Outcome.Failure,
            serviceState = serviceState,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = failure,
        )
    }
}
