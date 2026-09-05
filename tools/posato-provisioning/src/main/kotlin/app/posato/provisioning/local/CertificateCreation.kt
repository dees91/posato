package app.posato.provisioning.local

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Subprocess
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
 * write to the login keychain at all; it is deleted as soon as the import completes and never leaves this Mac.
 *
 * `security import` may raise a keychain prompt, and this tool deliberately does not run `set-key-partition-list`
 * to silence later ones, because that needs the keychain password. Whether the identity actually appeared is only
 * knowable by asking the keychain again, so that is what the command reports on.
 */
class CertificateCreation(
    private val subprocess: Subprocess,
    private val workingRoot: Path,
) {
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
        try {
            Files.write(certificate, Base64.getMimeDecoder().decode(certificateContent))
            importIntoKeychain(certificate)
            importIntoKeychain(request.key)
        } finally {
            certificate.deleteIfExists()
            request.key.deleteIfExists()
            request.directory.resolve("development.csr").deleteIfExists()
            request.directory.deleteIfExists()
        }
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
                "Importing the development certificate into the login keychain",
                "Approve the keychain prompt if one appeared, then rerun the command.",
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
