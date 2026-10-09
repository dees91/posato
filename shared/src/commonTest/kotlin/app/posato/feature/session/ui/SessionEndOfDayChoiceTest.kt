package app.posato.feature.session.ui

import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.utc
import app.posato.feature.session.domain.SessionSetupFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SessionEndOfDayChoiceTest {
    @Test
    fun `given the end of day chosen when it is no longer offered then setup shows the default length`() {
        val times = sessionSetupTimes(chosenAt2352, MIDNIGHT - 4 * MINUTE, FakeSessionTimeFormat(), CentralEuropeanZone)

        assertFalse(times.untilEndOfDay)
        assertEquals(DEFAULT_SETUP_MINUTES, times.durationMinutes)
    }

    @Test
    fun `given the end of day chosen when setup stays open past midnight then it does not move to the next midnight`() {
        val times = sessionSetupTimes(chosenAt2352, MIDNIGHT + 2 * MINUTE, FakeSessionTimeFormat(), CentralEuropeanZone)

        assertFalse(times.untilEndOfDay)
        assertEquals(DEFAULT_SETUP_MINUTES, times.durationMinutes)
    }

    @Test
    fun `given the end of day chosen when it is no longer offered at review then it is cleared and refused`() {
        val draft = chosenAt2352.withoutStaleEndOfDay(MIDNIGHT - 4 * MINUTE, CentralEuropeanZone)

        assertNull(draft.chosenEndOfDay)
        assertEquals(DEFAULT_SETUP_MINUTES, draft.durationMinutes)
        assertEquals(SessionSetupFailure.TOO_SHORT, draft.failure)
    }

    @Test
    fun `given a reviewed end of day when start is refused then setup returns to the default length`() {
        val reviewed = chosenAt2352.copy(isReviewing = true, resolvedReviewEnd = MIDNIGHT)

        val refused = reviewed.refused(SessionSetupFailure.TOO_SHORT)

        assertNull(refused.chosenEndOfDay)
        assertFalse(refused.isReviewing)
        assertEquals(DEFAULT_SETUP_MINUTES, refused.durationMinutes)
        assertEquals(SessionSetupFailure.TOO_SHORT, refused.failure)
    }

    private companion object {
        const val MINUTE: Long = 60_000L
        val MIDNIGHT: Long = utc(ScheduleDate(2026, 10, 9), 22 * 60)
        val chosenAt2352 = SessionSetupDraft(isSettingUp = true, durationMinutes = 8, chosenEndOfDay = MIDNIGHT)
    }
}
