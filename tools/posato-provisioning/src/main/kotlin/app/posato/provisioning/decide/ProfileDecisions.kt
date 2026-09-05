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
        val declaredExpiry = existing.attributes.expirationDate
        val expiry = declaredExpiry?.let { value -> parse(value) }
        val certificates = existing.relationships.certificates.data.map { reference -> reference.id }.toSet()
        val devices = existing.relationships.devices.data.map { reference -> reference.id }.toSet()
        return when {
            existing.dead -> ProfileDecision(ProfileAction.REPLACE, "the profile is ${existing.attributes.profileState?.lowercase()}")

            expiry != null && expiry.isBefore(now) -> ProfileDecision(ProfileAction.REPLACE, "the profile has expired")

            // Everything below is a condition this tool cannot confirm, so it asks rather than deleting. An expiry
            // it cannot parse and a state it does not recognise both mean the profile might still be working.
            declaredExpiry != null && expiry == null -> stale("its expiry could not be read", replaceRequested)

            !existing.active -> stale("its state is not one this tool recognises", replaceRequested)

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
