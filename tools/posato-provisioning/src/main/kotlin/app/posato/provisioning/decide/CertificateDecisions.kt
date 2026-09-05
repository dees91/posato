package app.posato.provisioning.decide

import app.posato.provisioning.local.KeychainIdentity
import app.posato.provisioning.model.CertificateResource
import java.time.Instant

enum class CertificateOutcome {
    /** A certificate this Mac can sign with, and the account holds the same bytes. */
    MATCHED,

    /** This Mac holds a usable certificate that the account no longer lists, which is what a revocation looks like. */
    LOCAL_NOT_IN_ACCOUNT,

    /** The account holds development certificates, but this Mac has the private key for none of them. */
    ACCOUNT_NOT_ON_THIS_MAC,

    /** The only usable certificates belong to another team. */
    WRONG_TEAM,

    /** The only certificates for this team have expired. */
    EXPIRED,

    /** Neither this Mac nor the account has a development certificate. */
    ABSENT,
}

data class CertificateDecision(
    val outcome: CertificateOutcome,
    val identity: KeychainIdentity? = null,
    val certificateId: String? = null,
) {
    val usable: Boolean get() = outcome == CertificateOutcome.MATCHED
}

/**
 * Which certificate a profile should be built around.
 *
 * A profile names an App Store Connect certificate, so a local identity is not enough on its own: if the tool picked
 * an account certificate this Mac has no private key for, it would create a profile that looks correct, install it,
 * and report success, while nothing on this Mac could sign with it. The match is therefore made on the certificate
 * bytes, which App Store Connect returns verbatim, rather than on a display name or a serial number that can repeat
 * across a team. An unmatched local identity is reported, never quietly replaced by a different account certificate.
 */
object CertificateDecisions {
    fun decide(
        identities: List<KeychainIdentity>,
        accountCertificates: List<CertificateResource>,
        team: String?,
        now: Instant,
    ): CertificateDecision {
        val current = identities.filter { identity -> identity.expiresAt.isAfter(now) }
        val forTeam = current.filter { identity -> team == null || identity.team == null || identity.team == team }
        val accountByBytes = accountCertificates.associateBy { certificate -> normalize(certificate.attributes.certificateContent) }
        val matched = forTeam.firstNotNullOfOrNull { identity ->
            accountByBytes[identity.base64Der]?.let { certificate -> identity to certificate }
        }
        return when {
            matched != null -> CertificateDecision(CertificateOutcome.MATCHED, matched.first, matched.second.id)
            forTeam.isNotEmpty() -> CertificateDecision(CertificateOutcome.LOCAL_NOT_IN_ACCOUNT, forTeam.first())
            current.isNotEmpty() -> CertificateDecision(CertificateOutcome.WRONG_TEAM, current.first())
            identities.isNotEmpty() -> CertificateDecision(CertificateOutcome.EXPIRED, identities.first())
            accountCertificates.isNotEmpty() -> CertificateDecision(CertificateOutcome.ACCOUNT_NOT_ON_THIS_MAC)
            else -> CertificateDecision(CertificateOutcome.ABSENT)
        }
    }

    /** App Store Connect wraps the certificate bytes across lines; the bytes are what must match, not the layout. */
    private fun normalize(content: String?): String = content.orEmpty().filterNot { character -> character.isWhitespace() }
}
