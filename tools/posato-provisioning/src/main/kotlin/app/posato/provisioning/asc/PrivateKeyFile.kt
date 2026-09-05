package app.posato.provisioning.asc

import app.posato.provisioning.core.ConfigurationKey
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.readText

private const val BEGIN_MARKER = "-----BEGIN PRIVATE KEY-----"
private const val END_MARKER = "-----END PRIVATE KEY-----"
private const val KEY_ALGORITHM = "EC"

private val REMEDY = "Download the App Store Connect API key once, store the .p8 under ~/Library/Developer/Posato/, " +
    "and point ${ConfigurationKey.ASC_PRIVATE_KEY_PATH.propertyName} at it."

/**
 * The `.p8` App Store Connect issues, read as a PKCS#8 elliptic-curve key.
 *
 * No failure here names the file's path or echoes any of its bytes, because a message that quotes the offending
 * content is exactly how a private key reaches a terminal, a log, or a pull-request comment. Every branch says what
 * kind of thing was wrong and what to do about it.
 */
object PrivateKeyFile {
    fun read(path: Path): PrivateKey {
        ensure(path.exists()) { "does not exist" }
        ensure(!Files.isSymbolicLink(path)) { "is a symbolic link, which this tool refuses to follow" }
        val text = try {
            path.readText()
        } catch (exception: IOException) {
            throw unreadable("could not be read", exception)
        }
        return parse(text)
    }

    /** Whether only the owner can read the key. A group- or world-readable key still works, so this only warns. */
    fun isOwnerOnly(path: Path): Boolean? = try {
        val permissions = Files.getPosixFilePermissions(path)
        permissions.none { permission -> permission in SHARED_PERMISSIONS }
    } catch (_: IOException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }

    private fun parse(text: String): PrivateKey {
        val begin = text.indexOf(BEGIN_MARKER)
        val end = text.indexOf(END_MARKER)
        ensure(begin >= 0 && end > begin) { "is not a PKCS#8 private key in PEM form" }
        val body = text.substring(begin + BEGIN_MARKER.length, end).filterNot { character -> character.isWhitespace() }
        val der = try {
            Base64.getDecoder().decode(body)
        } catch (exception: IllegalArgumentException) {
            throw unreadable("does not contain valid Base64 between its PEM markers", exception)
        }
        return try {
            KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(der))
        } catch (exception: GeneralSecurityException) {
            throw unreadable("is not an elliptic-curve key of the kind App Store Connect issues", exception)
        }
    }

    private fun ensure(
        condition: Boolean,
        reason: () -> String
    ) {
        if (!condition) throw unreadable(reason())
    }

    private fun unreadable(
        reason: String,
        cause: Throwable? = null
    ): ProvisioningException = ProvisioningException(
        ErrorCode.ASC_KEY_UNREADABLE,
        "The configured App Store Connect private key $reason.",
        REMEDY,
        cause,
    )

    private val SHARED_PERMISSIONS = setOf(
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE,
    )
}
