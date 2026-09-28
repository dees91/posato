package app.posato.feature.notifications

import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.host.ScheduledPause
import app.posato.feature.schedules.host.ScheduledPauseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val key = OccurrenceKey(ScheduleId("000000000000400080000000000000a1"), ScheduleDate(2026, 9, 28))

private fun scheduled(
    end: Long,
    state: ScheduledPauseState = ScheduledPauseState.APPLIED,
): ScheduledPause {
    return ScheduledPause("Focus", 0L, end, setOf(key), state)
}

class CombinedPauseEndsTest {
    private var now = 0L
    private val ends = CombinedPauseEnds({ now })

    @Test
    fun `given a manual pause ending inside a schedule then the end notice moves to the schedule's end and is never withdrawn`() {
        ends.onScheduled(scheduled(end = 200))
        ends.manualEnd = 100
        assertEquals(SessionNotificationAction.ScheduleEnd(200), ends.onManual(SessionNotificationAction.ScheduleEnd(100)))

        now = 100
        ends.manualEnd = null
        assertEquals(SessionNotificationAction.ScheduleEnd(200), ends.onManual(SessionNotificationAction.CancelEnd))
    }

    @Test
    fun `given a schedule starting inside a longer manual pause then the manual end stays`() {
        ends.manualEnd = 300
        ends.onManual(SessionNotificationAction.ScheduleEnd(300))

        assertNull(ends.onScheduled(scheduled(end = 200)))
    }

    @Test
    fun `given a schedule outlasting a manual pause then the end notice is moved to the later end`() {
        ends.manualEnd = 100
        ends.onManual(SessionNotificationAction.ScheduleEnd(100))

        assertEquals(SessionNotificationAction.ScheduleEnd(200), ends.onScheduled(scheduled(end = 200)))
        assertNull(ends.onScheduled(scheduled(end = 200)))
    }

    @Test
    fun `given a scheduled pause ended early then its notice is withdrawn and and one that reached its end keeps it`() {
        ends.onScheduled(scheduled(end = 200_000))
        now = 50_000
        assertEquals(SessionNotificationAction.CancelEnd, ends.onScheduled(null))

        ends.onScheduled(scheduled(end = 300_000))
        now = 300_000
        assertNull(ends.onScheduled(null))
    }

    @Test
    fun `given a scheduled pause that could not start then nothing is scheduled for it`() {
        assertNull(ends.onScheduled(scheduled(end = 200, state = ScheduledPauseState.SETUP_REQUIRED)))
    }

    @Test
    fun `given the extension announces schedules when a manual end falls inside the schedule then the app withdraws its own notice`() {
        val iphone = CombinedPauseEnds({ now }, appOwnsScheduled = false)
        iphone.manualEnd = 100
        assertEquals(SessionNotificationAction.ScheduleEnd(100), iphone.onManual(SessionNotificationAction.ScheduleEnd(100)))

        assertEquals(SessionNotificationAction.CancelEnd, iphone.onScheduled(scheduled(end = 200)))
        assertNull(iphone.effectiveEnd)
    }

    @Test
    fun `given the extension announces schedules when the app reopens during the overlap then it plans no earlier end`() {
        val iphone = CombinedPauseEnds({ now }, appOwnsScheduled = false)
        assertNull(iphone.onScheduled(scheduled(end = 200)))

        iphone.manualEnd = 100
        assertEquals(SessionNotificationAction.CancelEnd, iphone.onManual(SessionNotificationAction.ScheduleEnd(100)))
        assertNull(iphone.effectiveEnd)
    }

    @Test
    fun `given the extension announces schedules when the manual end outlasts the schedule then the app announces the manual end`() {
        val iphone = CombinedPauseEnds({ now }, appOwnsScheduled = false)
        iphone.onScheduled(scheduled(end = 200))
        iphone.manualEnd = 300

        assertEquals(SessionNotificationAction.ScheduleEnd(300), iphone.onManual(SessionNotificationAction.ScheduleEnd(300)))
        assertNull(iphone.onScheduled(null))
    }
}
