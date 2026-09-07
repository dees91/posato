package app.posato.prototype.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrototypeDurationPartsTest {
    @Test
    fun `given any allowed duration then both wheel values stay in range and round trip exactly`() {
        for (total in 5..1440) {
            val parts = PrototypeDurationParts(total)
            assertTrue(parts.hours in parts.hourRange)
            assertTrue(parts.minutes in parts.minuteRange)
            assertEquals(total, parts.withHours(parts.hours))
            assertEquals(total, parts.withMinutes(parts.minutes))
        }
    }

    @Test
    fun `given wheel changes at boundaries then five minutes and twenty four hours are respected`() {
        assertEquals(5, PrototypeDurationParts(60).withHours(0))
        assertEquals(1440, PrototypeDurationParts(1439).withHours(24))
        assertEquals(77, PrototypeDurationParts(17).withHours(1))
        assertEquals(0..0, PrototypeDurationParts(1440).minuteRange)
        assertEquals(5..59, PrototypeDurationParts(5).minuteRange)
        assertEquals(1380, PrototypeDurationParts(1440).withHours(23))
    }
}
