package app.posato.provisioning.local

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProcessOutput
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.UserPaths
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CertificateCreationTest {
    @Test
    fun `keeps the issued certificate and its key when the keychain import fails`() {
        // App Store Connect has already issued the certificate and consumed a team slot by this point. Deleting the
        // key here would strand that certificate permanently: it would exist in the account with no key anywhere
        // able to sign for it, and the only remedy on offer would request a second one against the same cap.
        val home = temporaryHome()
        val runner = RecordingRunner { command ->
            if (command.contains("import")) ProcessOutput(1, "", "security: user canceled") else write(command)
        }
        val creation = CertificateCreation(runner, UserPaths(home))
        val request = creation.signingRequest()

        val failure = assertFailsWith<ProvisioningException> { creation.install(request, encoded()) }

        assertEquals(ErrorCode.CERTIFICATE_IMPORT_FAILED, failure.code)
        assertTrue(request.key.exists(), "the private key was deleted after a failed import")
        assertTrue(request.directory.resolve("development.cer").exists(), "the issued certificate was deleted")
    }

    @Test
    fun `tells the maintainer where the kept files are without naming a person`() {
        val home = temporaryHome()
        val runner = RecordingRunner { command ->
            if (command.contains("import")) ProcessOutput(1, "", "security: user canceled") else write(command)
        }
        val creation = CertificateCreation(runner, UserPaths(home))

        val failure = assertFailsWith<ProvisioningException> { creation.install(creation.signingRequest(), encoded()) }

        val hint = failure.hint.orEmpty()
        assertTrue(hint.startsWith("The certificate App Store Connect issued and its private key were kept in ~/"))
        assertFalse(hint.contains(home.toString()), "the hint carried an absolute home path")
        // Rerunning with --create would request a second certificate against the team's cap, so the hint must not
        // offer it as the recovery.
        assertTrue(hint.contains("Do not rerun with --create"))
    }

    @Test
    fun `removes the key and the certificate once both imports succeed`() {
        val home = temporaryHome()
        val creation = CertificateCreation(RecordingRunner { command -> write(command) }, UserPaths(home))
        val request = creation.signingRequest()

        creation.install(request, encoded())

        assertFalse(request.key.exists())
        assertFalse(request.directory.exists())
    }

    @Test
    fun `keeps the working directory readable only by its owner`() {
        val home = temporaryHome()
        val creation = CertificateCreation(RecordingRunner { command -> write(command) }, UserPaths(home))

        val request = creation.signingRequest()

        assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(request.directory)))
    }

    /** `openssl` writes the file named after `-out`; the fake does the same so the paths under test are real. */
    private fun write(command: List<String>): ProcessOutput {
        val index = command.indexOf("-out")
        if (index >= 0) Path.of(command[index + 1]).writeText("synthetic ${command[1]}")
        return ProcessOutput(0, "", "")
    }

    private fun encoded(): String = Base64.getEncoder().encodeToString("synthetic certificate".toByteArray())

    private fun temporaryHome(): Path = Files.createTempDirectory("posato-provisioning-home")
}
