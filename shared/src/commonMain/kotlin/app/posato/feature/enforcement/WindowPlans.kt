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
        return rule.isOpenAt(wallMinute(epochMillis, zone))
    }

    /**
     * The first instant after [epochMillis] at which [rule]'s openness differs from its openness at [epochMillis].
     * The reading changes only at whole minutes, so without a clock change in between the answer is the next
     * aligned start or end; across a daylight saving change the minutes are read one by one, so a skipped window
     * never occurs and a repeated one opens again.
     */
    fun nextChange(
        rule: WebsiteWindow,
        epochMillis: Long,
        zone: ScheduleZone,
    ): Long {
        val minuteStart = epochMillis - epochMillis.mod(MINUTE_MILLIS)
        val open = isOpen(rule, epochMillis, zone)
        val phase = wallMinute(epochMillis, zone) % rule.periodMinutes
        val minutes = if (open) rule.lengthMinutes - phase else rule.periodMinutes - phase
        val candidate = minuteStart + minutes * MINUTE_MILLIS
        if (offsetMinutes(minuteStart, zone) == offsetMinutes(candidate, zone) && isOpen(rule, candidate, zone) != open) {
            return candidate
        }
        var probe = minuteStart + MINUTE_MILLIS
        repeat(MAX_SCAN_MINUTES) {
            if (isOpen(rule, probe, zone) != open) {
                return probe
            }
            probe += MINUTE_MILLIS
        }
        return probe
    }

    private fun offsetMinutes(
        epochMillis: Long,
        zone: ScheduleZone,
    ): Long {
        val local = zone.localAt(epochMillis)
        return local.date.epochDay * MINUTES_PER_DAY + local.minuteOfDay - epochMillis.floorDiv(MINUTE_MILLIS)
    }
}

private const val MINUTE_MILLIS: Long = 60_000L
private const val MINUTES_PER_DAY: Long = 1_440L

/** Two days of minutes: further than any clock change can push the next change of a rule. */
private const val MAX_SCAN_MINUTES: Int = 2 * 1_440

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
    return domains.distinct().sorted().mapNotNull { domain ->
        val website = setOfNotNull(domain, wwwCounterpart(domain))
        val coverages = parts.mapNotNull { part ->
            val entries = website intersect part.held
            if (entries.isEmpty()) {
                null
            } else {
                val rules = entries.map { entry -> part.rules[entry] }
                val rule = if (rules.any { it == null }) null else rules.filterNotNull().reduce(WebsiteWindow::intersect)
                listOf(windowSegment(part.endEpochMillis, rule))
            }
        }
        combinedPlan(domain, coverages)
    }
}

/**
 * One plan from several coverages, each a list of segments through its own end: cut at every end, in each cut
 * the intersection of the coverages still running, blocked when any of them blocks. Equal neighbours merge,
 * blocked segments after the last opening are dropped (a website after its plan is blocked), and a plan that
 * never opens is no plan.
 */
private fun combinedPlan(
    domain: String,
    coverages: List<List<WindowSegment>>,
): DomainWindowPlan? {
    val cuts = coverages.flatMap { coverage -> coverage.map(WindowSegment::untilEpochMillis) }.distinct().sorted()
    val segments = cuts.map { cut ->
        val running = coverages.mapNotNull { coverage -> coverage.firstOrNull { segment -> segment.untilEpochMillis >= cut } }
        val rules = running.map(WindowSegment::rule)
        val rule = if (rules.isEmpty() || rules.any { it == null }) null else rules.filterNotNull().reduce(WebsiteWindow::intersect)
        windowSegment(cut, rule)
    }
    val merged = segments.fold(mutableListOf<WindowSegment>()) { kept, segment ->
        if (kept.isNotEmpty() && kept.last().rule() == segment.rule()) kept[kept.lastIndex] = segment else kept += segment
        kept
    }
    val trimmed = merged.dropLastWhile { segment -> segment.rule() == null }
    return trimmed.takeIf { it.isNotEmpty() }?.let { DomainWindowPlan(domain, it) }
}

/**
 * Both requests' plans for every website either holds, as one plan through the later end: open only while each
 * request that still holds the website is open for it.
 */
internal fun EnforcementRequest.unionWindows(other: EnforcementRequest): List<DomainWindowPlan> {
    val requests = listOf(this, other)
    return (domains + other.domains).distinct().sorted().mapNotNull { domain ->
        combinedPlan(domain, requests.filter { request -> domain in request.domains }.map { request -> request.coverageOf(domain) })
    }
}

/** A request's hold on [domain] as segments through its end: its valid plan, then blocked until the end. */
private fun EnforcementRequest.coverageOf(domain: String): List<WindowSegment> {
    val planned = validPlans().firstOrNull { plan -> plan.domain == domain }?.segments.orEmpty()
    val tail = windowSegment(sessionEndEpochMillis, null).takeIf { planned.lastOrNull()?.untilEpochMillis != sessionEndEpochMillis }
    return planned + listOfNotNull(tail)
}
