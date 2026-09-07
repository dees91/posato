package app.posato.feature.session.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionDurationPartsTest {
    @Test
    fun `given less than one hour then minutes start at the minimum duration`() {
        val duration = SessionDurationParts(25)

        assertEquals(0, duration.hours)
        assertEquals(25, duration.minutes)
        assertEquals(5..59, duration.minuteRange)
        assertEquals(5, duration.withMinutes(0))
    }

    @Test
    fun `given a whole hour when hours reach zero then duration stays valid`() {
        val duration = SessionDurationParts(60)

        assertEquals(0..59, duration.minuteRange)
        assertEquals(5, duration.withHours(0))
        assertEquals(65, duration.withMinutes(5))
    }

    @Test
    fun `given maximum hours then minutes cannot extend beyond one day`() {
        val duration = SessionDurationParts(1440)

        assertEquals(0..24, duration.hourRange)
        assertEquals(0..0, duration.minuteRange)
        assertEquals(1440, duration.withMinutes(1))
        assertEquals(1380, duration.withHours(23))
        assertEquals(1440, SessionDurationParts(1439).withHours(24))
    }
}
