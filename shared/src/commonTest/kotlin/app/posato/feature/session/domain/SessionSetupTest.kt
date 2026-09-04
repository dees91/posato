package app.posato.feature.session.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionSetupTest {
    @Test
    fun `given five minutes when validated then the end resolves after now`() {
        val result = SessionSetup.validateDuration(5, NOW)

        assertEquals(SessionSetupResult.Valid(NOW + 5 * 60_000L), result)
    }

    @Test
    fun `given twenty four hours when validated then the end resolves after now`() {
        val result = SessionSetup.validateDuration(1_440, NOW)

        assertEquals(SessionSetupResult.Valid(NOW + 1_440 * 60_000L), result)
    }

    @Test
    fun `given zero minutes when validated then the input is too short`() {
        val result = SessionSetup.validateDuration(0, NOW)

        assertEquals(SessionSetupResult.Invalid(SessionSetupFailure.TOO_SHORT), result)
    }

    @Test
    fun `given four minutes when validated then the input is too short`() {
        val result = SessionSetup.validateDuration(4, NOW)

        assertEquals(SessionSetupResult.Invalid(SessionSetupFailure.TOO_SHORT), result)
    }

    @Test
    fun `given more than twenty four hours when validated then the input is too long`() {
        val result = SessionSetup.validateDuration(1_441, NOW)

        assertEquals(SessionSetupResult.Invalid(SessionSetupFailure.TOO_LONG), result)
    }

    @Test
    fun `given an end time within bounds when validated then it resolves unchanged`() {
        val result = SessionSetup.validateEndTime(NOW + 30 * 60_000L, NOW)

        assertEquals(SessionSetupResult.Valid(NOW + 30 * 60_000L), result)
    }

    @Test
    fun `given a past end time when validated then it is not in the future`() {
        val result = SessionSetup.validateEndTime(NOW - 1_000L, NOW)

        assertEquals(SessionSetupResult.Invalid(SessionSetupFailure.NOT_IN_FUTURE), result)
    }

    @Test
    fun `given the current instant as the end time when validated then it is not in the future`() {
        val result = SessionSetup.validateEndTime(NOW, NOW)

        assertEquals(SessionSetupResult.Invalid(SessionSetupFailure.NOT_IN_FUTURE), result)
    }

    @Test
    fun `given an end time beyond twenty four hours when validated then it is too long`() {
        val result = SessionSetup.validateEndTime(NOW + SessionLimits.MAX_DURATION_MILLIS + 1L, NOW)

        assertEquals(SessionSetupResult.Invalid(SessionSetupFailure.TOO_LONG), result)
    }

    @Test
    fun `given an end time below five minutes out when validated then it is too short`() {
        val result = SessionSetup.validateEndTime(NOW + SessionLimits.MIN_DURATION_MILLIS - 1L, NOW)

        assertEquals(SessionSetupResult.Invalid(SessionSetupFailure.TOO_SHORT), result)
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
    }
}
