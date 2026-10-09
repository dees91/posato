package app.posato.feature.session.ui

import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionSetup
import app.posato.feature.session.domain.SessionSetupFailure
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
    val chosenEnd = draft.chosenEndOfDay?.takeIf { it == endOfDay }
    val durationMinutes = when {
        chosenEnd != null -> minutesUntil(chosenEnd, nowMillis)
        choosing && draft.chosenEndOfDay != null -> DEFAULT_SETUP_MINUTES
        else -> draft.durationMinutes
    }
    val previewEnd = chosenEnd ?: (nowMillis + durationMinutes * MILLIS_PER_MINUTE)
    return SessionSetupTimes(
        durationMinutes = durationMinutes,
        untilEndOfDay = if (choosing) chosenEnd != null else draft.chosenEndOfDay != null,
        formattedEndOfDay = endOfDay?.let(timeFormat::formatClockTime),
        formattedPreviewEnd = previewEnd.takeIf { choosing }?.let { end -> timeFormat.formatTime(end, nowMillis) },
        formattedReviewEnd = draft.resolvedReviewEnd?.let { end -> timeFormat.formatTime(end, nowMillis) },
    )
}

/** Clears an end-of-day choice that is no longer offered, such as after 23:55 or past midnight, so it never becomes a short or next-day pause. */
internal fun SessionSetupDraft.withoutStaleEndOfDay(
    nowMillis: Long,
    zone: ScheduleZone,
): SessionSetupDraft {
    val chosen = chosenEndOfDay ?: return this
    if (chosen == SessionSetup.endOfDay(nowMillis, zone)) return this
    return copy(chosenEndOfDay = null, durationMinutes = DEFAULT_SETUP_MINUTES, failure = SessionSetupFailure.TOO_SHORT)
}

internal fun SessionSetupDraft.refused(failure: SessionSetupFailure): SessionSetupDraft {
    val length = if (chosenEndOfDay != null) DEFAULT_SETUP_MINUTES else durationMinutes
    return copy(isReviewing = false, resolvedReviewEnd = null, failure = failure, chosenEndOfDay = null, durationMinutes = length)
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
