package app.posato.feature.session.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.data.KeptApplication

internal const val PART_SESSION: String = "session"
internal const val PART_OCCURRENCE: String = "occurrence"

/** One running part's key: a session identifier, or an occurrence's schedule identifier and local date. */
internal class RetainedPart(
    val kind: String,
    val id: ByteArray,
    val year: Long = 0,
    val month: Long = 0,
    val day: Long = 0,
) {
    override fun toString(): String {
        return "RetainedPart(redacted)"
    }
}

/**
 * Keeps retention to its invariant: rows exist only for the active local session or an occurrence that still
 * has its pin. Every session and schedule transaction ends with it, so no stop reason can leave a stale row.
 */
internal suspend fun PosatoDatabase.sweepRetention() {
    localSessionQueries.deleteStaleRetainedDomains()
    localSessionQueries.deleteStaleRetainedApplications()
}

internal suspend fun PosatoDatabase.retain(
    part: RetainedPart,
    domains: List<String>,
    applications: List<KeptApplication> = emptyList(),
) {
    domains.forEach { domain ->
        localSessionQueries.insertRetainedDomain(part.kind, part.id, part.year, part.month, part.day, domain)
    }
    applications.forEach { application ->
        localSessionQueries.insertRetainedApplication(
            part.kind,
            part.id,
            part.year,
            part.month,
            part.day,
            application.mappingId,
            application.displayName,
            application.designatedRequirement,
        )
    }
}
