package app.posato.feature.session.ui

import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionSetup
import app.posato.feature.session.domain.SessionTimeFormat

internal sealed interface SessionDurationChoice {
    data class Length(
        val minutes: Int,
    ) : SessionDurationChoice

    data object EndOfDay : SessionDurationChoice
}

internal data class SessionSetupTimes(
    val durationMinutes: Int,
    val untilEndOfDay: Boolean,
    val formattedEndOfDay: String?,
    val formattedPreviewEnd: String?,
    val formattedReviewEnd: String?,
)

internal fun sessionSetupTimes(
    draft: SessionSetupDraft,
    nowMillis: Long,
    timeFormat: SessionTimeFormat,
    zone: ScheduleZone?,
): SessionSetupTimes {
    val choosing = draft.isSettingUp && !draft.isReviewing
    val endOfDay = zone?.takeIf { choosing }?.let { SessionSetup.endOfDay(nowMillis, it) }
    val chosenEnd = endOfDay?.takeIf { draft.untilEndOfDay }
    val durationMinutes = chosenEnd?.let { end -> minutesUntil(end, nowMillis) } ?: draft.durationMinutes
    val previewEnd = when {
        !choosing -> null
        chosenEnd != null -> timeFormat.formatClockTime(chosenEnd)
        else -> timeFormat.formatTime(nowMillis + durationMinutes * MILLIS_PER_MINUTE, nowMillis)
    }
    val reviewEnd = draft.resolvedReviewEnd?.let { end ->
        if (draft.untilEndOfDay) timeFormat.formatClockTime(end) else timeFormat.formatTime(end, nowMillis)
    }
    return SessionSetupTimes(
        durationMinutes = durationMinutes,
        untilEndOfDay = if (choosing) chosenEnd != null else draft.untilEndOfDay,
        formattedEndOfDay = endOfDay?.let(timeFormat::formatClockTime),
        formattedPreviewEnd = previewEnd,
        formattedReviewEnd = reviewEnd,
    )
}

internal fun minutesUntil(
    endEpochMillis: Long,
    nowEpochMillis: Long,
): Int {
    return ((endEpochMillis - nowEpochMillis) / MILLIS_PER_MINUTE).toInt()
        .coerceIn(SessionLimits.MIN_DURATION_MINUTES, SessionLimits.MAX_DURATION_MINUTES)
}

private fun SessionTimeFormat.formatClockTime(epochMillis: Long): String {
    return formatTime(epochMillis, epochMillis)
}

private const val MILLIS_PER_MINUTE: Long = 60_000L
