package app.posato.feature.notifications

import app.posato.feature.notifications.SessionNotificationAction.AnnounceStartedElsewhere
import app.posato.feature.notifications.SessionNotificationAction.AskPermission
import app.posato.feature.notifications.SessionNotificationAction.CancelEnd
import app.posato.feature.notifications.SessionNotificationAction.ScheduleEnd
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionNotificationPlannerTest {
    private val start = 1_790_000_000_000L
    private val end = start + 25 * 60_000L

    private fun record(
        seed: Int = 1,
        endAt: Long = end,
    ): SessionRecord {
        return SessionRecord(SessionId(testIdentifier(seed)), start, endAt)
    }

    private fun active(
        origin: SessionOrigin,
        seed: Int = 1,
        endAt: Long = end,
    ): LocalSessionStatus.Active {
        return LocalSessionStatus.Active(record(seed, endAt), endAt - start, origin = origin)
    }

    @Test
    fun `given a pause started on this device then its end is scheduled and permission may be asked`() {
        val actions = SessionNotificationPlanner.plan(LocalSessionStatus.Inactive, active(SessionOrigin.LOCAL), firstObservation = false)

        assertEquals(listOf(ScheduleEnd(end), AskPermission), actions)
    }

    @Test
    fun `given a pause adopted from another device then it is announced and its end is scheduled`() {
        val fromInactive = SessionNotificationPlanner.plan(LocalSessionStatus.Inactive, active(SessionOrigin.ADOPTED), firstObservation = false)
        val replacing = SessionNotificationPlanner.plan(
            active(SessionOrigin.LOCAL),
            active(SessionOrigin.ADOPTED, seed = 2),
            firstObservation = false,
        )

        assertEquals(listOf(ScheduleEnd(end), AnnounceStartedElsewhere), fromInactive)
        assertEquals(listOf(ScheduleEnd(end), AnnounceStartedElsewhere), replacing)
    }

    @Test
    fun `given the first status after launch then nothing is announced and stale notices are settled`() {
        assertEquals(listOf(ScheduleEnd(end)), SessionNotificationPlanner.plan(null, active(SessionOrigin.ADOPTED), firstObservation = true))
        assertEquals(listOf(CancelEnd), SessionNotificationPlanner.plan(null, LocalSessionStatus.Inactive, firstObservation = true))
    }

    @Test
    fun `given a pause that ends early then its end notice is cancelled but an expiry keeps it`() {
        val early = LocalSessionStatus.Ended(record(), SessionEndKind.ENDED_EARLY, SessionOrigin.LOCAL)
        val expired = LocalSessionStatus.Ended(record(), SessionEndKind.EXPIRED, SessionOrigin.LOCAL)

        assertEquals(listOf(CancelEnd), SessionNotificationPlanner.plan(active(SessionOrigin.LOCAL), early, firstObservation = false))
        assertEquals(emptyList(), SessionNotificationPlanner.plan(active(SessionOrigin.LOCAL), expired, firstObservation = false))
    }

    @Test
    fun `given the same pause with a moved end then only the end notice moves`() {
        val later = end + 10 * 60_000L

        assertEquals(
            listOf(ScheduleEnd(later)),
            SessionNotificationPlanner.plan(active(SessionOrigin.ADOPTED), active(SessionOrigin.ADOPTED, endAt = later), firstObservation = false),
        )
        assertEquals(
            emptyList(),
            SessionNotificationPlanner.plan(active(SessionOrigin.ADOPTED), active(SessionOrigin.ADOPTED), firstObservation = false),
        )
    }
}
