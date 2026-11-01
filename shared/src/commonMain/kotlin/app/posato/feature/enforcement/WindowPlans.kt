package app.posato.feature.enforcement

import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.targets.domain.WebsiteWindow

/**
 * The most segments a valid plan holds: one cut per running part that can cover a website (a manual session
 * and, per schedule, yesterday's and today's occurrence), plus one (ADR 0004 amendment).
 */
internal const val MAX_WINDOW_SEGMENTS: Int = 2 * ScheduleLimits.MAX_SCHEDULES + 1 + 1

/** The segment's rule, or null when it keeps the website blocked. */
internal fun WindowSegment.rule(): WebsiteWindow? {
    return null
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
    return false
}

/** The device's wall clock as windows read it: hour * 60 + minute in the current zone, never minutes since midnight. */
internal object WindowClock {
    fun wallMinute(
        epochMillis: Long,
        zone: ScheduleZone,
    ): Int {
        return zone.localAt(epochMillis).minuteOfDay
    }

    fun isOpen(
        rule: WebsiteWindow,
        epochMillis: Long,
        zone: ScheduleZone,
    ): Boolean {
        return false
    }

    /** The first instant after [epochMillis] at which [rule]'s openness differs from its openness at [epochMillis]. */
    fun nextChange(
        rule: WebsiteWindow,
        epochMillis: Long,
        zone: ScheduleZone,
    ): Long {
        return epochMillis + 1
    }
}

/** Whether the plan leaves its website open at [epochMillis]: inside a ruled segment and inside that rule's window. */
internal fun DomainWindowPlan.isOpenAt(
    epochMillis: Long,
    zone: ScheduleZone,
): Boolean {
    return false
}

/** The next instant after [epochMillis] at which the plan's openness can change; null when no segment is left. */
internal fun DomainWindowPlan.nextBoundary(
    epochMillis: Long,
    zone: ScheduleZone,
): Long? {
    return null
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

/** Open until its next close, or blocked until its next opening in the plan; null when no window is left. */
internal fun DomainWindowPlan.status(
    epochMillis: Long,
    zone: ScheduleZone,
): WindowStatus? {
    return null
}

/**
 * The held websites a host blocks at [epochMillis]: every held website except those whose valid plan is open.
 * Retention and the limits never read this; an open window frees nothing.
 */
internal fun EnforcementRequest.blockedNow(
    epochMillis: Long,
    zone: ScheduleZone,
): List<String> {
    return domains
}

/** The next instant after [epochMillis] at which any held website's window opens, closes, or its segment ends. */
internal fun EnforcementRequest.nextWindowBoundary(
    epochMillis: Long,
    zone: ScheduleZone,
): Long? {
    return null
}

/**
 * One running part as windows see it: the websites it holds, the rules its set has for those it holds through
 * the set now (an item kept only by retention has none), and its end.
 */
internal class WindowPart(
    val endEpochMillis: Long,
    val held: Set<String>,
    val rules: Map<String, WebsiteWindow>,
) {
    override fun toString(): String {
        return "WindowPart(redacted)"
    }
}

/**
 * The plans for [domains] across [parts]: for each website and its `www` form, segments cut at every covering
 * part's end; in each, the intersection of the covering parts' rules, blocked when any covering part has none.
 * A website that never opens gets no plan.
 */
internal fun windowPlans(
    domains: Collection<String>,
    parts: List<WindowPart>,
): List<DomainWindowPlan> {
    return emptyList()
}

/**
 * Both requests' plans for every website either holds, as one plan through the later end: open only while each
 * request that still holds the website is open for it.
 */
internal fun EnforcementRequest.unionWindows(other: EnforcementRequest): List<DomainWindowPlan> {
    return emptyList()
}
