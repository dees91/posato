package app.posato.provisioning.decide

import app.posato.provisioning.local.KeychainIdentity
import app.posato.provisioning.model.CertificateAttributes
import app.posato.provisioning.model.CertificateResource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val NOW: Instant = Instant.parse("2026-09-05T12:00:00Z")
private val LATER: Instant = NOW.plusSeconds(86_400)
private val EARLIER: Instant = NOW.minusSeconds(86_400)
private const val TEAM = "ABCDE12345"

class CertificateDecisionsTest {
    @Test
    fun `matches a local identity to the account by certificate bytes`() {
        val identity = identity(base64Der = "AAAABBBBCCCC")

        val decision = CertificateDecisions.decide(listOf(identity), listOf(account("CERT", "AAAABBBBCCCC")), TEAM, NOW)

        assertEquals(CertificateOutcome.MATCHED, decision.outcome)
        assertEquals("CERT", decision.certificateId)
        assertEquals(true, decision.usable)
    }

    @Test
    fun `matches through the line wrapping App Store Connect adds`() {
        val decision = CertificateDecisions.decide(
            listOf(identity(base64Der = "AAAABBBBCCCC")),
            listOf(account("CERT", "AAAA\nBBBB\r\nCCCC\n")),
            TEAM,
            NOW,
        )

        assertEquals(CertificateOutcome.MATCHED, decision.outcome)
    }

    @Test
    fun `refuses a certificate this Mac has no private key for rather than using it`() {
        // Picking this account certificate would produce a profile that installs and reports success while nothing
        // on this Mac could sign with it.
        val decision = CertificateDecisions.decide(emptyList(), listOf(account("OTHER", "ZZZZ")), TEAM, NOW)

        assertEquals(CertificateOutcome.ACCOUNT_NOT_ON_THIS_MAC, decision.outcome)
        assertNull(decision.certificateId)
    }

    @Test
    fun `reports a local identity the account no longer lists`() {
        val decision = CertificateDecisions.decide(listOf(identity(base64Der = "AAAA")), emptyList(), TEAM, NOW)

        assertEquals(CertificateOutcome.LOCAL_NOT_IN_ACCOUNT, decision.outcome)
        assertEquals(false, decision.usable)
    }

    @Test
    fun `does not match when the bytes differ by a single character`() {
        val decision = CertificateDecisions.decide(
            listOf(identity(base64Der = "AAAABBBBCCCC")),
            listOf(account("CERT", "AAAABBBBCCCD")),
            TEAM,
            NOW,
        )

        assertEquals(CertificateOutcome.LOCAL_NOT_IN_ACCOUNT, decision.outcome)
    }

    @Test
    fun `selects the one identity of several that the account holds`() {
        val decision = CertificateDecisions.decide(
            listOf(identity(base64Der = "AAAA"), identity(base64Der = "BBBB"), identity(base64Der = "CCCC")),
            listOf(account("CERT", "BBBB")),
            TEAM,
            NOW,
        )

        assertEquals(CertificateOutcome.MATCHED, decision.outcome)
        assertEquals("BBBB", decision.identity?.base64Der)
    }

    @Test
    fun `separates an expired certificate from one belonging to another team`() {
        val expired = CertificateDecisions.decide(listOf(identity(expiresAt = EARLIER)), emptyList(), TEAM, NOW)
        val otherTeam = CertificateDecisions.decide(listOf(identity(team = "ZZZZZ99999")), emptyList(), TEAM, NOW)
        val nothing = CertificateDecisions.decide(emptyList(), emptyList(), TEAM, NOW)

        assertEquals(CertificateOutcome.EXPIRED, expired.outcome)
        assertEquals(CertificateOutcome.WRONG_TEAM, otherTeam.outcome)
        assertEquals(CertificateOutcome.ABSENT, nothing.outcome)
    }

    private fun identity(
        team: String? = TEAM,
        expiresAt: Instant = LATER,
        base64Der: String = "AAAA",
    ): KeychainIdentity = KeychainIdentity("Apple Development: Someone (X)", team, expiresAt, base64Der)

    private fun account(
        id: String,
        content: String
    ): CertificateResource = CertificateResource(id, CertificateAttributes(certificateType = "DEVELOPMENT", certificateContent = content))
}
