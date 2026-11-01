package app.posato.feature.enforcement

import app.posato.feature.targets.domain.WebsiteWindow

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
 * request that still holds the website or its `www` form is open for it, so both stored forms share one plan.
 */
internal fun EnforcementRequest.unionWindows(other: EnforcementRequest): List<DomainWindowPlan> {
    val requests = listOf(this, other)
    return (domains + other.domains).distinct().sorted().mapNotNull { domain ->
        val website = setOfNotNull(domain, wwwCounterpart(domain))
        val coverages = requests.flatMap { request ->
            request.domains.filter { entry -> entry in website }.distinct().map { entry -> request.coverageOf(entry) }
        }
        combinedPlan(domain, coverages)
    }
}

/** A request's hold on [domain] as segments through its end: its valid plan, then blocked until the end. */
private fun EnforcementRequest.coverageOf(domain: String): List<WindowSegment> {
    val planned = validPlans().firstOrNull { plan -> plan.domain == domain }?.segments.orEmpty()
    val tail = windowSegment(sessionEndEpochMillis, null).takeIf { planned.lastOrNull()?.untilEpochMillis != sessionEndEpochMillis }
    return planned + listOfNotNull(tail)
}
