package app.posato.feature.session

import app.posato.feature.schedules.currentSystemZone
import app.posato.feature.session.domain.SessionTimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Reads the zone on every call, because the JVM caches its default zone at the first use. */
internal class JvmSessionTimeFormat(
    private val currentZone: () -> ZoneId = { currentSystemZone() },
) : SessionTimeFormat {
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

    override fun formatTime(
        epochMillis: Long,
        nowEpochMillis: Long,
    ): String {
        val zone = currentZone()
        val end = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return if (end.toLocalDate() == Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()) {
            timeFormatter.format(end)
        } else {
            dateTimeFormatter.format(end)
        }
    }
}
