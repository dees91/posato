package app.posato.provisioning.decide

import app.posato.provisioning.model.ProfileResource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

enum class ProfileAction {
    /** The account profile is current and already grants what this Mac needs. */
    REUSE,

    /** No profile of this name exists yet. */
    CREATE,

    /** The profile is expired or invalid, so it cannot be reused and its name cannot be claimed until it is gone. */
    REPLACE,

    /** The profile is still valid but no longer covers what this Mac needs; replacing it is the maintainer's call. */
    STALE,
}

data class ProfileDecision(
    val action: ProfileAction,
    val reason: String,
)

/**
 * What to do with the account profile of a given name.
 *
 * An expired or invalid profile is deleted without asking. That is not a general licence to delete: App Store
 * Connect requires profile names to be unique within a team, so the dead profile physically blocks the name its
 * replacement must use, and a dead profile grants nothing that could be lost.
 *
 * A profile that is still valid but missing a newly registered device is a different case. Something else may be
 * relying on it, so the command stops and says to pass `--replace` rather than deciding on the maintainer's behalf.
 */
object ProfileDecisions {
    fun decide(
        existing: ProfileResource?,
        certificateId: String,
        requiredDeviceIds: Set<String>,
        now: Instant,
        replaceRequested: Boolean,
    ): ProfileDecision {
        if (existing == null) return ProfileDecision(ProfileAction.CREATE, "no profile of this name exists")
        val expired = existing.attributes.expirationDate?.let { value -> parse(value)?.isBefore(now) } ?: false
        val certificates = existing.relationships.certificates.data.map { reference -> reference.id }.toSet()
        val devices = existing.relationships.devices.data.map { reference -> reference.id }.toSet()
        return when {
            !existing.active -> ProfileDecision(ProfileAction.REPLACE, "the profile is not active")
            expired -> ProfileDecision(ProfileAction.REPLACE, "the profile has expired")
            certificateId !in certificates -> stale("it does not reference this Mac's certificate", replaceRequested)
            !devices.containsAll(requiredDeviceIds) -> stale("it does not cover every registered device", replaceRequested)
            else -> ProfileDecision(ProfileAction.REUSE, "the profile is current")
        }
    }

    private fun stale(
        reason: String,
        replaceRequested: Boolean
    ): ProfileDecision = if (replaceRequested) {
        ProfileDecision(ProfileAction.REPLACE, reason)
    } else {
        ProfileDecision(ProfileAction.STALE, reason)
    }

    private fun parse(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, APPLE_FORMAT).toInstant() }.getOrNull()

    /** App Store Connect renders an expiry as `2027-01-01T00:00:00.000+0000`, which is not an ISO instant. */
    private val APPLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]Z")
}
