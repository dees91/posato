package app.posato.control.model

import kotlinx.serialization.Serializable

enum class Severity { ERROR, WARN, INFO }

@Serializable
data class DoctorCheck(
    val id: String,
    val ok: Boolean,
    val severity: String,
    val detail: String,
    val hint: String? = null,
) {
    companion object {
        fun pass(
            id: String,
            detail: String
        ): DoctorCheck = DoctorCheck(id, true, Severity.INFO.name.lowercase(), detail)

        fun fail(
            id: String,
            detail: String,
            hint: String? = null,
            severity: Severity = Severity.ERROR
        ): DoctorCheck = DoctorCheck(id, false, severity.name.lowercase(), detail, hint)
    }
}

@Serializable
data class DoctorReport(
    val ok: Boolean,
    val checks: List<DoctorCheck>,
)
