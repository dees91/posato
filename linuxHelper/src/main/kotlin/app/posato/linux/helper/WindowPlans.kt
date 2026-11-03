package app.posato.linux.helper

import java.time.Instant
import java.time.ZoneId

/** One stretch of a host's window plan until [untilEpochMillis] (exclusive); 0 and 0 keeps the host blocked. */
internal data class WindowSegment(
    val untilEpochMillis: Long,
    val periodMinutes: Int,
    val lengthMinutes: Int,
) {
    override fun toString(): String {
        return "WindowSegment(redacted)"
    }
}

/** When one held host may be left out of the block (TARGETS-013, ADR 0010 amendment). */
internal data class HostWindowPlan(
    val host: String,
    val segments: List<WindowSegment>,
) {
    override fun toString(): String {
        return "HostWindowPlan(redacted)"
    }
}

/**
 * The optional sixth apply field, `host=until:period:length;...` per plan with plans separated by commas, and the
 * evaluation of its plans on the wall clock. A field outside the grammar refuses the request; a plan that is well
 * formed but invalid is ignored, so its host stays blocked for the whole request.
 */
internal object WindowPlans {
    /** The ADR 0004 amendment's cap: one cut per part that can run at once (2 x 10 schedules + 1 manual), plus one. */
    const val MAX_SEGMENTS = 22

    /** Far above any real plan list, well inside the one-megabyte request. */
    const val MAX_FIELD_BYTES = 262_144

    private val PERIODS = setOf(30, 60, 120)
    private val LENGTHS = setOf(5, 10, 15)
    private val label = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
    private val segment = Regex("([0-9]{1,15}):([0-9]{1,3}):([0-9]{1,2})")
    private const val MAX_HOST_LENGTH = 253
    private const val MAX_PLANS = 4_096

    /** The grammar's bound; a plan over [MAX_SEGMENTS] but within it is well formed and ignored as invalid. */
    private const val MAX_PARSED_SEGMENTS = 2 * MAX_SEGMENTS
    private const val MINUTES_PER_HOUR = 60

    fun parse(field: String): List<HostWindowPlan>? {
        if (field.isEmpty()) return emptyList()
        val entries = field.split(',')
        if (field.length > MAX_FIELD_BYTES || entries.size > MAX_PLANS) return null
        val plans = entries.map { entry -> plan(entry) ?: return null }
        return plans
    }

    private fun plan(entry: String): HostWindowPlan? {
        val host = entry.substringBefore('=', missingDelimiterValue = "")
        val segmentTexts = entry.substringAfter('=', missingDelimiterValue = "").split(';')
        if (!isHost(host) || segmentTexts.size > MAX_PARSED_SEGMENTS) return null
        val segments = segmentTexts.map { text ->
            val match = segment.matchEntire(text) ?: return null
            val (until, period, length) = match.destructured
            WindowSegment(until.toLong(), period.toInt(), length.toInt())
        }
        return HostWindowPlan(host, segments)
    }

    fun encode(plans: List<HostWindowPlan>): String {
        return plans.joinToString(",") { plan ->
            plan.host + "=" + plan.segments.joinToString(";") { "${it.untilEpochMillis}:${it.periodMinutes}:${it.lengthMinutes}" }
        }
    }

    /**
     * The held [hosts] blocked at [epochMillis]: all but those whose one valid plan has a rule in its segment at that
     * instant and whose wall clock (`hour * 60 + minute` in [zone]) is inside that rule's window.
     */
    fun blockedNow(
        hosts: List<String>,
        plans: List<HostWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): List<String> {
        val open = validPlans(hosts, plans, endEpochMillis).filter { plan -> isOpen(plan, epochMillis, zone) }.map(HostWindowPlan::host).toSet()
        return hosts.filter { host -> host !in open }
    }

    /** One plan per held host; a host with a duplicate or invalid plan has none, so it stays blocked. */
    fun validPlans(
        hosts: List<String>,
        plans: List<HostWindowPlan>,
        endEpochMillis: Long,
    ): List<HostWindowPlan> {
        val held = hosts.toSet()
        return plans.groupBy(HostWindowPlan::host)
            .filter { (host, same) -> host in held && same.size == 1 && isValid(same.single(), endEpochMillis) }
            .map { (_, same) -> same.single() }
    }

    private fun isValid(
        plan: HostWindowPlan,
        endEpochMillis: Long,
    ): Boolean {
        val ends = plan.segments.map(WindowSegment::untilEpochMillis)
        return plan.segments.size in 1..MAX_SEGMENTS &&
            ends.zipWithNext().all { (earlier, later) -> earlier < later } &&
            ends.last() <= endEpochMillis &&
            plan.segments.all { it.isBlocking() || it.hasRule() }
    }

    private fun isOpen(
        plan: HostWindowPlan,
        epochMillis: Long,
        zone: ZoneId,
    ): Boolean {
        val segment = plan.segments.firstOrNull { it.untilEpochMillis > epochMillis }?.takeIf { it.hasRule() } ?: return false
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val wall = local.hour * MINUTES_PER_HOUR + local.minute
        return wall % segment.periodMinutes < segment.lengthMinutes
    }

    private fun WindowSegment.hasRule(): Boolean = periodMinutes in PERIODS && lengthMinutes in LENGTHS

    private fun WindowSegment.isBlocking(): Boolean = periodMinutes == 0 && lengthMinutes == 0

    private fun isHost(host: String): Boolean {
        return host.length <= MAX_HOST_LENGTH && host.split('.').let { labels -> labels.size >= 2 && labels.all(label::matches) }
    }
}

/** The held hosts that this apply's plans leave blocked at [epochMillis], read in [zone]. */
internal fun HelperRequest.Apply.blockedAt(
    epochMillis: Long,
    zone: ZoneId,
): List<String> {
    return WindowPlans.blockedNow(hosts, windows, endEpochMillis, epochMillis, zone)
}
