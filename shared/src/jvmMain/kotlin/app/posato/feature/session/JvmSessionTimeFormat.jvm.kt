package app.posato.feature.session

import app.posato.feature.session.domain.SessionTimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal class JvmSessionTimeFormat : SessionTimeFormat {
    private val zone = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone)
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone)

    override fun formatTime(
        epochMillis: Long,
        nowEpochMillis: Long,
    ): String {
        val end = Instant.ofEpochMilli(epochMillis)
        return if (end.atZone(zone).toLocalDate() == Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()) {
            timeFormatter.format(end)
        } else {
            dateTimeFormatter.format(end)
        }
    }
}
