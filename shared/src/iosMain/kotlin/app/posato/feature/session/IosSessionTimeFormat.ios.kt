package app.posato.feature.session

import app.posato.feature.session.domain.SessionTimeFormat
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

internal class IosSessionTimeFormat : SessionTimeFormat {
    private val formatter = NSDateFormatter().apply {
        timeStyle = NSDateFormatterShortStyle
    }

    override fun formatTime(epochMillis: Long): String {
        return checkNotNull(formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND_DOUBLE)))
    }
}

private const val MILLIS_PER_SECOND_DOUBLE: Double = 1_000.0
