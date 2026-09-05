package app.posato.provisioning.model

import kotlinx.serialization.Serializable

enum class Severity { ERROR, WARN, INFO }

/**
 * What a check observed. `state` and `severity` are independent: `state` says what was seen, `severity` says what it
 * costs. Only a `MISSING` condition is ever an error, so an `UNKNOWN` one is visible without blocking a run.
 */
enum class CheckState { OK, MISSING, UNKNOWN }

@Serializable
data class DoctorCheck(
    val id: String,
    val ok: Boolean,
    val state: String,
    val severity: String,
    val detail: String,
    val hint: String? = null,
) {
    companion object {
        fun pass(
            id: String,
            detail: String
        ): DoctorCheck = DoctorCheck(id, true, CheckState.OK.id, Severity.INFO.id, detail)

        fun fail(
            id: String,
            detail: String,
            hint: String,
            severity: Severity = Severity.ERROR
        ): DoctorCheck = DoctorCheck(id, false, CheckState.MISSING.id, severity.id, detail, hint)

        /** A condition this Mac cannot answer right now, such as an absent iPhone. It never claims readiness. */
        fun unknown(
            id: String,
            detail: String,
            hint: String
        ): DoctorCheck = DoctorCheck(id, false, CheckState.UNKNOWN.id, Severity.WARN.id, detail, hint)
    }
}

@Serializable
data class DoctorReport(
    val ok: Boolean,
    val checks: List<DoctorCheck>,
)

internal val Severity.id: String get() = name.lowercase()

internal val CheckState.id: String get() = name.lowercase()
