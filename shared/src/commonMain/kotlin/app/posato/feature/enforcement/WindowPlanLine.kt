package app.posato.feature.enforcement

/**
 * The text form of window plans in the Linux and Windows apply line and in Android's saved pause (TARGETS-013,
 * ADR 0010 amendment): `host=until:period:length;...` per plan, plans separated by commas. Decoding checks the
 * grammar only; whether a plan is valid for its request is decided where it is enforced.
 */
public object WindowPlanLine {
    private const val SEPARATORS = "=;:"
    private val segment = Regex("([0-9]{1,15}):([0-9]{1,3}):([0-9]{1,2})")

    public fun encode(plans: List<DomainWindowPlan>): String {
        return plans.joinToString(",") { plan ->
            plan.domain + "=" + plan.segments.joinToString(";") { "${it.untilEpochMillis}:${it.periodMinutes}:${it.lengthMinutes}" }
        }
    }

    /** The plans in [text], or null when any of it is outside the grammar. */
    public fun decode(text: String): List<DomainWindowPlan>? {
        if (text.isEmpty()) return emptyList()
        return text.split(',').map { entry ->
            val domain = entry.substringBefore('=', missingDelimiterValue = "")
            val segments = entry.substringAfter('=', missingDelimiterValue = "").split(';').map { part ->
                val (until, period, length) = segment.matchEntire(part)?.destructured ?: return null
                WindowSegment(until.toLong(), period.toInt(), length.toInt())
            }
            if (domain.isEmpty() || domain.any { it in SEPARATORS }) return null
            DomainWindowPlan(domain, segments)
        }
    }
}
