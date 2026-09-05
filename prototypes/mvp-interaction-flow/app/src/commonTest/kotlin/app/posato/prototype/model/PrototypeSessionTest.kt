package app.posato.prototype.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrototypeSessionTest {
    private fun setup(): PrototypeState {
        return reducePrototype(PrototypeFixtures.ready(PrototypePlatform.Mac), SessionAction.OpenSetup)
    }

    @Test
    fun `given initial duration when reviewed and started then end resolves`() {
        val active = reducePrototype(reducePrototype(setup(), SessionAction.Review), SessionAction.Start)
        assertEquals(25, active.session.durationMinutes)
        assertEquals("18:10", active.session.endsAt)
        assertTrue(active.session.active)
    }

    @Test
    fun `given valid durations when reviewed started and cancellation occurs then resolved end is preserved`() {
        val cases = mapOf(
            5 to "17:50",
            25 to "18:10",
            45 to "18:30",
            60 to "18:45",
            90 to "19:15",
            375 to "00:00 tomorrow",
            1440 to "17:45 tomorrow",
        )
        for ((minutes, end) in cases) {
            val selected = reducePrototype(setup(), SetDuration(minutes.toString()))
            val review = reducePrototype(selected, SessionAction.Review)
            val active = reducePrototype(review, SessionAction.Start)
            val cancelled = reducePrototype(reducePrototype(active, SessionAction.RequestEarlyEnd), SessionAction.CancelEarlyEnd)
            assertEquals(end, review.session.endsAt)
            assertEquals(minutes, cancelled.session.durationMinutes)
            assertEquals(end, cancelled.session.endsAt)
            assertEquals("The session remains active until $end.", cancelled.outcome.message)
        }
    }

    @Test
    fun `given invalid duration when submitted then last valid choice remains`() {
        for (input in listOf("0", "4", "1441", "25.5", "NaN", "Infinity", "invalid")) {
            val before = setup()
            val after = reducePrototype(before, SetDuration(input))
            assertEquals(OutcomeTone.Blocked, after.outcome.tone, input)
            assertEquals(before.session, after.session, input)
        }
    }

    @Test
    fun `given active session when changing duration then session is unchanged`() {
        val before = PrototypeFixtures.active(PrototypePlatform.Mac)
        val after = reducePrototype(before, SetDuration("60"))
        assertEquals(OutcomeTone.Blocked, after.outcome.tone)
        assertEquals(before.session, after.session)
    }

    @Test
    fun `given setup or active session when returning then no session transition occurs`() {
        val cancelled = reducePrototype(setup(), SessionAction.ReturnToSession)
        assertEquals(PrototypeSurface.Home, cancelled.surface)
        assertFalse(cancelled.session.active)
        val active = reducePrototype(PrototypeFixtures.active(PrototypePlatform.Mac), SessionAction.ReturnToSession)
        assertEquals(PrototypeSurface.Active, active.surface)
        assertTrue(active.session.active)
        assertEquals(OutcomeTone.Blocked, reducePrototype(PrototypeState(PrototypePlatform.Mac), SessionAction.ReturnToSession).outcome.tone)
    }

    @Test
    fun `given reviewed custom duration when returning to setup then duration persists`() {
        val selected = reducePrototype(setup(), SetDuration("90"))
        val reviewing = reducePrototype(selected, SessionAction.Review)
        val back = reducePrototype(reviewing, SessionAction.OpenSetup)
        assertEquals(PrototypeSurface.SessionSetup, back.surface)
        assertEquals(90, back.session.durationMinutes)
        assertEquals("19:15", back.session.endsAt)
    }
}
