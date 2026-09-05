package app.posato.provisioning.decide

import app.posato.provisioning.model.ProfileAttributes
import app.posato.provisioning.model.ProfileRelationships
import app.posato.provisioning.model.ProfileResource
import app.posato.provisioning.model.RelationshipRef
import app.posato.provisioning.model.ToMany
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private val NOW: Instant = Instant.parse("2026-09-05T12:00:00Z")
private const val CERT = "CERT"

class ProfileDecisionsTest {
    @Test
    fun `reuses a current profile that already covers this Mac`() {
        val decision = ProfileDecisions.decide(profile(devices = listOf("MAC")), CERT, setOf("MAC"), NOW, replaceRequested = false)

        assertEquals(ProfileAction.REUSE, decision.action)
    }

    @Test
    fun `creates when no profile of the name exists`() {
        val decision = ProfileDecisions.decide(null, CERT, setOf("MAC"), NOW, replaceRequested = false)

        assertEquals(ProfileAction.CREATE, decision.action)
    }

    @Test
    fun `replaces an expired or explicitly dead profile without being asked`() {
        // App Store Connect keeps profile names unique per team, so the dead profile blocks the name its replacement
        // needs. It grants nothing, so removing it loses nothing.
        val expired = ProfileDecisions.decide(
            profile(expiration = "2026-01-01T00:00:00.000+0000"),
            CERT,
            setOf("MAC"),
            NOW,
            replaceRequested = false,
        )

        assertEquals(ProfileAction.REPLACE, expired.action)
        listOf("INVALID", "EXPIRED").forEach { state ->
            assertEquals(ProfileAction.REPLACE, ProfileDecisions.decide(profile(state = state), CERT, setOf("MAC"), NOW, false).action, state)
        }
    }

    @Test
    fun `never deletes a profile whose state it does not recognise`() {
        // Only a state App Store Connect uses to say the profile is finished authorises a delete. A state this tool
        // has not seen, or a response that omits it, may still describe a working profile that something else relies
        // on, and deleting it is not recoverable by rerunning.
        listOf("PENDING", "SOMETHING_NEW", null).forEach { state ->
            val decision = ProfileDecisions.decide(profile(state = state), CERT, setOf("MAC"), NOW, replaceRequested = false)

            assertEquals(ProfileAction.STALE, decision.action, "state=$state")
        }
    }

    @Test
    fun `asks rather than reusing a profile whose expiry it cannot read`() {
        // Treating an unreadable expiry as "not expired" would reuse the profile, and the download would then be
        // rejected as expired with a hint naming --replace that this branch never honoured.
        val decision = ProfileDecisions.decide(profile(expiration = "next Tuesday"), CERT, setOf("MAC"), NOW, replaceRequested = false)

        assertEquals(ProfileAction.STALE, decision.action)
        assertEquals("its expiry could not be read", decision.reason)

        val replacing = ProfileDecisions.decide(profile(expiration = "next Tuesday"), CERT, setOf("MAC"), NOW, replaceRequested = true)
        assertEquals(ProfileAction.REPLACE, replacing.action)
    }

    @Test
    fun `stops rather than replacing a valid profile that misses a newly registered device`() {
        val decision = ProfileDecisions.decide(profile(devices = listOf("MAC")), CERT, setOf("MAC", "PHONE"), NOW, replaceRequested = false)

        assertEquals(ProfileAction.STALE, decision.action)
        assertEquals("it does not cover every registered device", decision.reason)
    }

    @Test
    fun `stops rather than replacing a valid profile built around another certificate`() {
        val decision = ProfileDecisions.decide(profile(certificates = listOf("OTHER")), CERT, setOf("MAC"), NOW, replaceRequested = false)

        assertEquals(ProfileAction.STALE, decision.action)
    }

    @Test
    fun `replaces a stale profile once the maintainer asks for it`() {
        val decision = ProfileDecisions.decide(profile(devices = listOf("MAC")), CERT, setOf("MAC", "PHONE"), NOW, replaceRequested = true)

        assertEquals(ProfileAction.REPLACE, decision.action)
    }

    @Test
    fun `reads the expiry format App Store Connect actually returns`() {
        val current = ProfileDecisions.decide(
            profile(expiration = "2027-01-01T00:00:00.000+0000"),
            CERT,
            setOf("MAC"),
            NOW,
            replaceRequested = false,
        )

        assertEquals(ProfileAction.REUSE, current.action)
    }

    private fun profile(
        state: String? = "ACTIVE",
        expiration: String = "2027-01-01T00:00:00.000+0000",
        certificates: List<String> = listOf(CERT),
        devices: List<String> = listOf("MAC"),
    ): ProfileResource = ProfileResource(
        "PROF",
        ProfileAttributes(name = "Posato_macOS_Sync_Development", profileState = state, expirationDate = expiration),
        ProfileRelationships(
            certificates = ToMany(certificates.map { id -> RelationshipRef(id, "certificates") }),
            devices = ToMany(devices.map { id -> RelationshipRef(id, "devices") }),
        ),
    )
}
