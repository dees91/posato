package app.posato.provisioning.local

import app.posato.provisioning.core.CommandRunner
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64

private const val DEVELOPMENT_PREFIX = "Apple Development:"
private const val BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----"
private const val END_CERTIFICATE = "-----END CERTIFICATE-----"

/** `security find-identity` prints `  1) <hash> "<common name>"`; only the quoted name matters here. */
private val IDENTITY_LINE = Regex("""^\s*\d+\)\s+[0-9A-Fa-f]+\s+"([^"]+)"""", RegexOption.MULTILINE)

/** A team identifier is ten characters in the subject's organizational unit, whatever renders the subject. */
private val ORGANIZATIONAL_UNIT = Regex("""(?:^|,)\s*OU=([A-Za-z0-9]{10})\s*(?:,|$)""")

/** A distinguished name escapes a comma inside a value, so the common name cannot be read by splitting on commas. */
private val COMMON_NAME = Regex("""(?:^|,)\s*CN=((?:\\.|[^,\\])*)""")

/**
 * One Apple Development certificate this Mac can actually sign with.
 *
 * `base64Der` is what correlates it with the account. App Store Connect returns the same bytes as
 * `certificateContent`, so comparing them is an exact identity check rather than a guess from a name or a serial
 * number, both of which can repeat across a team.
 */
data class KeychainIdentity(
    val commonName: String,
    val team: String?,
    val expiresAt: Instant,
    val base64Der: String,
)

/**
 * The certificates in the login keychain, read as X.509 rather than as text.
 *
 * Parsing the certificate itself avoids depending on how a particular `openssl` build renders a subject line, which
 * differs between the LibreSSL macOS ships and OpenSSL 3, and gives the expiry and the exact bytes at the same time.
 */
object KeychainIdentities {
    fun parseIdentityNames(output: String): List<String> = IDENTITY_LINE
        .findAll(output)
        .map { match -> match.groupValues[1] }
        .filter { name -> name.startsWith(DEVELOPMENT_PREFIX) }
        .distinct()
        .toList()

    fun parseCertificates(pem: String): List<KeychainIdentity> = blocks(pem).mapNotNull { block -> toIdentity(block) }

    private fun blocks(pem: String): List<String> = pem
        .split(BEGIN_CERTIFICATE)
        .drop(1)
        .mapNotNull { part -> part.substringBefore(END_CERTIFICATE).takeIf { it.isNotBlank() } }

    private fun toIdentity(base64Body: String): KeychainIdentity? {
        val cleaned = base64Body.filterNot { character -> character.isWhitespace() }
        val der = try {
            Base64.getDecoder().decode(cleaned)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val certificate = decode(der) ?: return null
        val subject = certificate.subjectX500Principal.name
        val commonName = COMMON_NAME.find(subject)?.groupValues?.get(1)?.replace("\\", "") ?: return null
        return KeychainIdentity(
            commonName = commonName,
            team = ORGANIZATIONAL_UNIT.find(subject)?.groupValues?.get(1),
            expiresAt = certificate.notAfter.toInstant(),
            base64Der = cleaned,
        )
    }

    private fun decode(der: ByteArray): X509Certificate? = try {
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as? X509Certificate
    } catch (_: CertificateException) {
        null
    }
}

/** Runs the two keychain queries and keeps only certificates this Mac holds a private key for. */
class KeychainReader(
    private val subprocess: CommandRunner,
    private val register: (String) -> Unit,
) {
    fun developmentIdentities(): List<KeychainIdentity> {
        val identities = subprocess.run(listOf("/usr/bin/security", "find-identity", "-v", "-p", "codesigning"))
        val signable = KeychainIdentities.parseIdentityNames(identities.stdout)
        signable.forEach(register)
        if (signable.isEmpty()) return emptyList()
        val certificates = subprocess.run(listOf("/usr/bin/security", "find-certificate", "-a", "-p", "-c", DEVELOPMENT_PREFIX))
        // Only a certificate whose private key is in the keychain can sign, so anything find-identity did not list
        // is dropped even when the certificate itself is present.
        return KeychainIdentities.parseCertificates(certificates.stdout).filter { identity -> identity.commonName in signable }
    }
}
