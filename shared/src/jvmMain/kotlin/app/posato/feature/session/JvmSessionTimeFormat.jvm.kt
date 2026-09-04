package app.posato.feature.session

import app.posato.feature.session.domain.SessionTimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal class JvmSessionTimeFormat : SessionTimeFormat {
    private val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())

    override fun formatTime(epochMillis: Long): String {
        return formatter.format(Instant.ofEpochMilli(epochMillis))
    }
}
