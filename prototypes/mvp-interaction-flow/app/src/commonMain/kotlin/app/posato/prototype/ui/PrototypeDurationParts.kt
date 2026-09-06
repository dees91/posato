package app.posato.prototype.ui

import app.posato.prototype.model.PrototypeClock

internal class PrototypeDurationParts(
    totalMinutes: Int
) {
    val hours: Int = totalMinutes / MINUTES_PER_HOUR
    val minutes: Int = totalMinutes % MINUTES_PER_HOUR
    val hourRange: IntRange = 0..PrototypeClock.MAXIMUM_MINUTES / MINUTES_PER_HOUR
    val minuteRange: IntRange = when (hours) {
        0 -> PrototypeClock.MINIMUM_MINUTES until MINUTES_PER_HOUR
        hourRange.last -> 0..0
        else -> 0 until MINUTES_PER_HOUR
    }

    fun withHours(hours: Int): Int {
        return bounded(hours * MINUTES_PER_HOUR + minutes)
    }

    fun withMinutes(minutes: Int): Int {
        return bounded(hours * MINUTES_PER_HOUR + minutes)
    }

    private fun bounded(minutes: Int): Int {
        return minutes.coerceIn(PrototypeClock.MINIMUM_MINUTES, PrototypeClock.MAXIMUM_MINUTES)
    }
}

private const val MINUTES_PER_HOUR = 60
