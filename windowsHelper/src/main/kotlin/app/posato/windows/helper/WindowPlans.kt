package app.posato.windows.helper

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

/** The optional sixth apply field and the evaluation of its plans on the wall clock. */
internal object WindowPlans {
    fun parse(field: String): List<HostWindowPlan>? {
        TODO("TARGETS-013 phase 4")
    }

    fun encode(plans: List<HostWindowPlan>): String {
        TODO("TARGETS-013 phase 4")
    }

    fun blockedNow(
        hosts: List<String>,
        plans: List<HostWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): List<String> {
        return hosts
    }
}
