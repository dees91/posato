package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateKeyFileTest {
    @Test
    fun `reads a PKCS8 elliptic-curve key in PEM form`() {
        val pair = TestKeys.generate()
        val file = temporaryFile("key.p8", TestKeys.toPem(pair))

        assertEquals(pair.private, PrivateKeyFile.read(file))
    }

    @Test
    fun `fails closed on every unusable file without naming its path or content`() {
        val secret = "SUPERSECRETBODY"
        val cases = mapOf(
            "missing" to temporaryDirectory().resolve("absent.p8"),
            "not a key" to temporaryFile("plain.p8", "$secret is not a key"),
            "bad base64" to temporaryFile("bad.p8", "-----BEGIN PRIVATE KEY-----\n$secret!!\n-----END PRIVATE KEY-----\n"),
            "not elliptic curve" to temporaryFile("wrong.p8", "-----BEGIN PRIVATE KEY-----\nQUJD\n-----END PRIVATE KEY-----\n"),
        )

        cases.forEach { (name, file) ->
            val failure = assertFailsWith<ProvisioningException>(name) { PrivateKeyFile.read(file) }
            assertEquals(ErrorCode.ASC_KEY_UNREADABLE, failure.code, name)
            assertFalse(failure.message.orEmpty().contains(secret), name)
            assertFalse(failure.message.orEmpty().contains(file.toString()), name)
        }
    }

    @Test
    fun `refuses a symbolic link rather than following it`() {
        val directory = temporaryDirectory()
        val real = temporaryFile("real.p8", TestKeys.toPem(TestKeys.generate()))
        val link = directory.resolve("link.p8")
        Files.createSymbolicLink(link, real)

        val failure = assertFailsWith<ProvisioningException> { PrivateKeyFile.read(link) }

        assertEquals(ErrorCode.ASC_KEY_UNREADABLE, failure.code)
        assertTrue(failure.message.orEmpty().contains("symbolic link"))
    }

    @Test
    fun `reports whether the key is readable by anyone but its owner`() {
        val file = temporaryFile("permissions.p8", TestKeys.toPem(TestKeys.generate()))

        Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        assertEquals(true, PrivateKeyFile.isOwnerOnly(file))

        Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"))
        assertEquals(false, PrivateKeyFile.isOwnerOnly(file))
    }

    private fun temporaryDirectory(): Path = Files.createTempDirectory("posato-provisioning-key")

    private fun temporaryFile(
        name: String,
        content: String
    ): Path = temporaryDirectory().resolve(name).also { path -> path.writeText(content) }
}
