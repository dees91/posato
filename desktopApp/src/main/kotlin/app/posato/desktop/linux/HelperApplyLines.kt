package app.posato.desktop.linux

import app.posato.feature.enforcement.EnforcementRequest

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
        return listOf(five)
    }
}
