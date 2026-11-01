package app.posato.feature.enforcement

import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.targets.domain.WebsiteWindow

/**
 * The most segments a valid plan holds: one cut per running part that can cover a website (a manual session
 * and, per schedule, yesterday's and today's occurrence), plus one (ADR 0004 amendment).
 */
internal const val MAX_WINDOW_SEGMENTS: Int = 2 * ScheduleLimits.MAX_SCHEDULES + 1 + 1

/** The segment's rule, or null when it keeps the website blocked. */
internal fun WindowSegment.rule(): WebsiteWindow? {
    return WebsiteWindow.of(periodMinutes, lengthMinutes)
}

private fun WindowSegment.hasValidCodes(): Boolean {
    return (periodMinutes == 0 && lengthMinutes == 0) || rule() != null
}

/** A segment that holds [rule] until [untilEpochMillis]; null blocks. */
internal fun windowSegment(
    untilEpochMillis: Long,
    rule: WebsiteWindow?,
): WindowSegment {
    return WindowSegment(untilEpochMillis, rule?.periodMinutes ?: 0, rule?.lengthMinutes ?: 0)
}

/**
 * Whether the plan is well formed for a request ending at [requestEndEpochMillis]: at least one and at most
 * [MAX_WINDOW_SEGMENTS] segments, strictly increasing ends, the last no later than the request's end, and every
 * code either a rule from the fixed choices or 0 and 0. A plan that has merely aged is still valid.
 */
internal fun DomainWindowPlan.isValid(requestEndEpochMillis: Long): Boolean {
    val ends = segments.map(WindowSegment::untilEpochMillis)
    return segments.size in 1..MAX_WINDOW_SEGMENTS &&
        ends.zipWithNext().all { (earlier, later) -> earlier < later } &&
        ends.last() <= requestEndEpochMillis &&
        segments.all(WindowSegment::hasValidCodes)
}

/** What Session says about one website with a window in the running pause. */
internal sealed interface WindowStatus {
    /** Blocked now; its next window in this pause opens at [epochMillis]. */
    data class OpensAt(
        val epochMillis: Long,
    ) : WindowStatus

    /** Open now, until [epochMillis]. */
    data class OpenUntil(
        val epochMillis: Long,
    ) : WindowStatus
}
