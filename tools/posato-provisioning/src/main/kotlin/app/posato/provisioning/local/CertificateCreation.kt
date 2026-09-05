package app.posato.provisioning.local

import app.posato.provisioning.core.CommandRunner
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.UserPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

private const val KEY_BITS = "2048"
private const val SUBJECT = "/CN=Posato Development/C=US"
private const val OWNER_ONLY_DIRECTORY = "rwx------"
private const val OWNER_ONLY_FILE = "rw-------"

/**
 * Generates a key pair and a signing request, then imports the issued certificate into the login keychain.
 *
 * The key is RSA because Apple Development certificates reject an elliptic-curve signing request. It is written to
 * an owner-only directory rather than kept in memory, because `security import` reads a file and the JDK cannot
 * write to the login keychain at all; it is deleted once the import has succeeded and never leaves this Mac. A
 * failed import keeps it, because by then the certificate exists in the account and a key deleted here could not be
 * recreated for it.
 *
 * `security import` may raise a keychain prompt, and this tool deliberately does not run `set-key-partition-list`
 * to silence later ones, because that needs the keychain password. Whether the identity actually appeared is only
 * knowable by asking the keychain again, so that is what the command reports on.
 */
class CertificateCreation(
    private val subprocess: CommandRunner,
    private val userPaths: UserPaths,
) {
    private val workingRoot: Path = userPaths.posatoDeveloperDirectory

    fun signingRequest(): SigningRequest {
        val directory = Files.createDirectories(workingRoot).resolve(".csr-${System.nanoTime()}")
        Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY_DIRECTORY)))
        val key = directory.resolve("development.key")
        val request = directory.resolve("development.csr")
        subprocess.run(listOf("/usr/bin/openssl", "genrsa", "-out", key.toString(), KEY_BITS))
            .requireSuccess(ErrorCode.CERTIFICATE_IMPORT_FAILED, "Generating a development key", RETRY_HINT)
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString(OWNER_ONLY_FILE))
        subprocess.run(
            listOf("/usr/bin/openssl", "req", "-new", "-key", key.toString(), "-subj", SUBJECT, "-out", request.toString()),
        ).requireSuccess(ErrorCode.CERTIFICATE_IMPORT_FAILED, "Building a certificate signing request", RETRY_HINT)
        return SigningRequest(directory, key, request.readText())
    }

    fun install(
        request: SigningRequest,
        certificateContent: String
    ) {
        val certificate = request.directory.resolve("development.cer")
        Files.write(certificate, Base64.getMimeDecoder().decode(certificateContent))
        Files.setPosixFilePermissions(certificate, PosixFilePermissions.fromString(OWNER_ONLY_FILE))
        // Nothing is deleted until both imports succeed. App Store Connect has already issued the certificate and
        // consumed one of the team's slots by this point, so deleting the private key on a failed import would
        // strand that certificate permanently: it would exist in the account with no key anywhere able to sign for
        // it, and the only offered remedy would be to request another one against the same cap.
        importIntoKeychain(certificate)
        importIntoKeychain(request.key)
        certificate.deleteIfExists()
        discard(request)
    }

    fun discard(request: SigningRequest) {
        request.key.deleteIfExists()
        request.directory.resolve("development.csr").deleteIfExists()
        request.directory.deleteIfExists()
    }

    private fun importIntoKeychain(file: Path) {
        subprocess.run(listOf("/usr/bin/security", "import", file.toString(), "-T", "/usr/bin/codesign"))
            .requireSuccess(
                ErrorCode.CERTIFICATE_IMPORT_FAILED,
                "Importing the issued certificate into the login keychain",
                "The certificate App Store Connect issued and its private key were kept in " +
                    "${userPaths.display(file.parent)}. Approve the keychain prompt if one appeared, then import " +
                    "both by hand with `security import <file> -T /usr/bin/codesign`. Do not rerun with --create: " +
                    "that requests a second certificate against the team's cap and this one would stay unusable.",
            )
    }

    data class SigningRequest(
        val directory: Path,
        val key: Path,
        val pem: String,
    )

    private companion object {
        const val RETRY_HINT = "Confirm /usr/bin/openssl is available, then rerun the command."
    }
}
