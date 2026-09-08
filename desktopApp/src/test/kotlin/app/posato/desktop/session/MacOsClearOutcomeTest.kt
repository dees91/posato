package app.posato.desktop.session

import app.posato.desktop.macos.HelperResult
import app.posato.feature.enforcement.EnforcementOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class MacOsClearOutcomeTest {
    @Test
    fun `given a successful restore when mapping then the outcome is cleared`() {
        assertEquals(EnforcementOutcome.CLEARED, helperResult(HelperResult.Outcome.Success).toClearOutcome())
    }

    @Test
    fun `given an unregistered helper when mapping then the outcome is unavailable`() {
        assertEquals(
            EnforcementOutcome.UNAVAILABLE,
            helperResult(HelperResult.Outcome.Failure, HelperResult.State.NotRegistered).toClearOutcome(),
        )
    }

    @Test
    fun `given an unapproved helper when mapping then the outcome is unavailable`() {
        assertEquals(
            EnforcementOutcome.UNAVAILABLE,
            helperResult(HelperResult.Outcome.Failure, HelperResult.State.ApprovalRequired).toClearOutcome(),
        )
    }

    @Test
    fun `given an incompatible helper when mapping then the outcome is unavailable`() {
        assertEquals(
            EnforcementOutcome.UNAVAILABLE,
            helperResult(HelperResult.Outcome.Failure, HelperResult.State.UnavailableOrIncompatible).toClearOutcome(),
        )
    }

    @Test
    fun `given a ready helper failure when mapping then the outcome stays failed`() {
        assertEquals(
            EnforcementOutcome.FAILED,
            helperResult(HelperResult.Outcome.Failure, HelperResult.State.Ready).toClearOutcome(),
        )
    }

    private fun helperResult(
        outcome: HelperResult.Outcome,
        serviceState: HelperResult.State = HelperResult.State.Ready,
    ): HelperResult {
        return HelperResult(
            outcome = outcome,
            serviceState = serviceState,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.None,
        )
    }
}
