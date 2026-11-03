package app.posato.desktop.linux

import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.WindowPlanLine

/**
 * The apply lines for the Linux and Windows services, in the order to send them (ADR 0010 TARGETS-013 amendment):
 * with window plans the six-field form first, then the five-field form that a service not restarted since an update
 * still accepts, which blocks every held website.
 */
internal object HelperApplyLines {
    fun lines(
        request: EnforcementRequest,
        sessionId: String,
        executables: List<String>,
    ): List<String> {
        val five = "apply\t$sessionId\t${request.sessionEndEpochMillis}\t${request.domains.joinToString(",")}\t" +
            executables.joinToString("\u001f")
        val plans = WindowPlanLine.encode(request.windows)
        return if (plans.isEmpty() || plans.length > MAX_PLAN_FIELD_BYTES) listOf(five) else listOf("$five\t$plans", five)
    }

    /** The services' limit for the sixth field; longer plans fall back to blocking every held website. */
    private const val MAX_PLAN_FIELD_BYTES = 262_144
}
