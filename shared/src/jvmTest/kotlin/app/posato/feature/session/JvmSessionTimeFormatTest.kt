package app.posato.feature.session

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmSessionTimeFormatTest {
    private val zone = ZoneId.systemDefault()
    private val format = JvmSessionTimeFormat()
    private val timeOnly = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone)
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone)

    @Test
    fun `given the end on the same day when formatting then only the time is shown`() {
        val now = millis(LocalDate.of(2026, JUNE, 15), LocalTime.of(22, 30))
        val end = millis(LocalDate.of(2026, JUNE, 15), LocalTime.of(23, 30))

        assertEquals(timeOnly.format(Instant.ofEpochMilli(end)), format.formatTime(end, now))
    }

    @Test
    fun `given the end past midnight when formatting then the day is included with the time`() {
        val now = millis(LocalDate.of(2026, JUNE, 15), LocalTime.of(23, 30))
        val end = millis(LocalDate.of(2026, JUNE, 16), LocalTime.of(0, 30))

        assertEquals(dateTime.format(Instant.ofEpochMilli(end)), format.formatTime(end, now))
    }

    private fun millis(
        date: LocalDate,
        time: LocalTime,
    ): Long {
        return ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
    }

    private companion object {
        const val JUNE: Int = 6
    }
}
