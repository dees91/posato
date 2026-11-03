package app.posato.feature.enforcement

import app.posato.feature.schedules.domain.ScheduleZone

/** More steps than any valid plan has boundaries: 22 segments, each changing at most every five minutes for a day. */
private const val MAX_STATUS_STEPS: Int = 1_024

private fun DomainWindowPlan.segmentAt(epochMillis: Long): WindowSegment? {
    return segments.firstOrNull { segment -> segment.untilEpochMillis > epochMillis }
}

/** Whether the plan leaves its website open at [epochMillis]: inside a ruled segment and inside that rule's window. */
internal fun DomainWindowPlan.isOpenAt(
    epochMillis: Long,
    zone: ScheduleZone,
): Boolean {
    val rule = segmentAt(epochMillis)?.rule() ?: return false
    return WindowClock.isOpen(rule, epochMillis, zone)
}

/** The next instant after [epochMillis] at which the plan's openness can change; null when no segment is left. */
internal fun DomainWindowPlan.nextBoundary(
    epochMillis: Long,
    zone: ScheduleZone,
): Long? {
    val segment = segmentAt(epochMillis) ?: return null
    val rule = segment.rule() ?: return segment.untilEpochMillis
    return minOf(segment.untilEpochMillis, WindowClock.nextChange(rule, epochMillis, zone))
}

/** Open until its next close, or blocked until its next opening in the plan; null when no window is left. */
internal fun DomainWindowPlan.status(
    epochMillis: Long,
    zone: ScheduleZone,
): WindowStatus? {
    val open = isOpenAt(epochMillis, zone)
    var boundary = nextBoundary(epochMillis, zone)
    var steps = 0
    while (boundary != null && isOpenAt(boundary, zone) == open && steps < MAX_STATUS_STEPS) {
        boundary = nextBoundary(boundary, zone)
        steps += 1
    }
    return when {
        boundary == null || steps == MAX_STATUS_STEPS -> null
        open -> WindowStatus.OpenUntil(boundary)
        else -> WindowStatus.OpensAt(boundary)
    }
}

/**
 * The held websites a host blocks at [epochMillis]: every held website except those whose valid plan is open.
 * Retention and the limits never read this; an open window frees nothing.
 */
internal fun EnforcementRequest.blockedNow(
    epochMillis: Long,
    zone: ScheduleZone,
): List<String> {
    val open = validPlans().filterTo(mutableSetOf()) { plan -> plan.isOpenAt(epochMillis, zone) }.map(DomainWindowPlan::domain)
    return domains.filter { domain -> domain !in open }
}

/** The plans a host follows: one valid plan per held website. A website with a malformed or duplicate plan stays blocked. */
internal fun EnforcementRequest.validPlans(): List<DomainWindowPlan> {
    val held = domains.toSet()
    return windows.groupBy(DomainWindowPlan::domain)
        .filter { (domain, plans) -> domain in held && plans.size == 1 && plans.single().isValid(sessionEndEpochMillis) }
        .map { (_, plans) -> plans.single() }
}

/** The next instant after [epochMillis] at which any held website's window opens, closes, or its segment ends. */
internal fun EnforcementRequest.nextWindowBoundary(
    epochMillis: Long,
    zone: ScheduleZone,
): Long? {
    return validPlans().mapNotNull { plan -> plan.nextBoundary(epochMillis, zone) }.minOrNull()
}
