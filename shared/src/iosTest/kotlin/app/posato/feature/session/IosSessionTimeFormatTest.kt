package app.posato.feature.session

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.posix.time
import kotlin.test.Test
import kotlin.test.assertEquals

class IosSessionTimeFormatTest {
    private val format = IosSessionTimeFormat()
    private val timeOnly = NSDateFormatter().apply { timeStyle = NSDateFormatterShortStyle }
    private val dateTime = NSDateFormatter().apply {
        dateStyle = NSDateFormatterShortStyle
        timeStyle = NSDateFormatterShortStyle
    }

    @Test
    fun `given the end on the same day when formatting then only the time is shown`() {
        val now = time(null) * MILLIS_PER_SECOND

        assertEquals(timePart(now), format.formatTime(now, now))
    }

    @Test
    fun `given the end past midnight when formatting then the day is included with the time`() {
        val now = time(null) * MILLIS_PER_SECOND
        val end = now + NEXT_DAY_MILLIS

        assertEquals(datePart(end), format.formatTime(end, now))
    }

    private fun timePart(epochMillis: Long): String {
        return checkNotNull(timeOnly.stringFromDate(dateOf(epochMillis)))
    }

    private fun datePart(epochMillis: Long): String {
        return checkNotNull(dateTime.stringFromDate(dateOf(epochMillis)))
    }

    private fun dateOf(epochMillis: Long): NSDate {
        return NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND_DOUBLE)
    }

    private companion object {
        const val MILLIS_PER_SECOND: Long = 1_000
        const val MILLIS_PER_SECOND_DOUBLE: Double = 1_000.0
        const val NEXT_DAY_MILLIS: Long = 26 * 3_600_000L
    }
}
