package app.posato.feature.session

import app.posato.feature.session.domain.SessionTimeFormat
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

internal class IosSessionTimeFormat : SessionTimeFormat {
    private val calendar = NSCalendar.currentCalendar
    private val timeFormatter = NSDateFormatter().apply {
        timeStyle = NSDateFormatterShortStyle
    }
    private val dateTimeFormatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterShortStyle
        timeStyle = NSDateFormatterShortStyle
    }

    override fun formatTime(
        epochMillis: Long,
        nowEpochMillis: Long,
    ): String {
        val end = NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND_DOUBLE)
        val now = NSDate.dateWithTimeIntervalSince1970(nowEpochMillis / MILLIS_PER_SECOND_DOUBLE)
        val formatter = if (calendar.isDate(end, inSameDayAsDate = now)) timeFormatter else dateTimeFormatter
        return checkNotNull(formatter.stringFromDate(end))
    }
}

private const val MILLIS_PER_SECOND_DOUBLE: Double = 1_000.0
