package app.posato.provisioning.play

import app.posato.provisioning.core.ConfigurationKey
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

private const val SERVICE_ACCOUNT = "service_account"
private const val BEGIN_MARKER = "-----BEGIN PRIVATE KEY-----"
private const val END_MARKER = "-----END PRIVATE KEY-----"

private val REMEDY = "Create a JSON key for the Play publishing service account, store it under ~/Library/Developer/Posato/ " +
    "with `chmod 600`, and point ${ConfigurationKey.PLAY_SERVICE_ACCOUNT_KEY_PATH.propertyName} at it."

private val SHARED_PERMISSIONS = setOf(
    PosixFilePermission.GROUP_READ,
    PosixFilePermission.GROUP_WRITE,
    PosixFilePermission.OTHERS_READ,
    PosixFilePermission.OTHERS_WRITE,
)

@Serializable
private class KeyDocument(
    val type: String? = null,
    @SerialName("client_email") val clientEmail: String? = null,
    @SerialName("private_key_id") val privateKeyId: String? = null,
    @SerialName("private_key") val privateKey: String? = null,
)

/**
 * The Google Cloud service-account JSON key the Play commands sign with.
 *
 * Only the e-mail, the key id, and the RSA key are read. The document's own `token_uri` is ignored: the assertion is
 * always exchanged at Google's fixed token endpoint, so an edited key file cannot send a signed assertion elsewhere.
 * The file must be readable by its owner only, because unlike the App Store Connect key it grants publishing to a
 * live store listing. No message names the path or quotes any of the file.
 */
class ServiceAccountKey(
    val clientEmail: String,
    val privateKeyId: String?,
    val privateKey: PrivateKey,
) {
    companion object {
        fun read(path: Path): ServiceAccountKey {
            ensure(Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "does not exist" }
            ensure(!Files.isSymbolicLink(path)) { "is a symbolic link, which this tool refuses to follow" }
            ensure(ownerOnly(path)) { "can be read or written by other users" }
            val text = try {
                Files.readString(path)
            } catch (exception: IOException) {
                throw unreadable("could not be read", exception)
            }
            return parse(text)
        }

        private fun parse(text: String): ServiceAccountKey {
            val document = decode(text)
            ensure(document.type == SERVICE_ACCOUNT) { "is not a service-account key" }
            val email = document.clientEmail?.takeIf { it.isNotBlank() } ?: refuse("has no client_email")
            val pem = document.privateKey ?: refuse("has no private_key")
            return ServiceAccountKey(email, document.privateKeyId?.takeIf { it.isNotBlank() }, rsaKey(pem))
        }

        private fun decode(text: String): KeyDocument = try {
            PlayJson.json.decodeFromString(KeyDocument.serializer(), text)
        } catch (exception: SerializationException) {
            throw unreadable("is not a JSON document", exception)
        } catch (exception: IllegalArgumentException) {
            throw unreadable("is not a JSON document", exception)
        }

        private fun rsaKey(pem: String): PrivateKey {
            val begin = pem.indexOf(BEGIN_MARKER)
            val end = pem.indexOf(END_MARKER)
            ensure(begin >= 0 && end > begin) { "does not hold a PKCS#8 private key" }
            val body = pem.substring(begin + BEGIN_MARKER.length, end).filterNot { character -> character.isWhitespace() }
            return try {
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
            } catch (exception: IllegalArgumentException) {
                throw unreadable("does not hold valid Base64 in its private key", exception)
            } catch (exception: GeneralSecurityException) {
                throw unreadable("does not hold an RSA private key", exception)
            }
        }

        private fun ownerOnly(path: Path): Boolean = try {
            Files.getPosixFilePermissions(path).none { permission -> permission in SHARED_PERMISSIONS }
        } catch (exception: IOException) {
            throw unreadable("could not be inspected", exception)
        } catch (_: UnsupportedOperationException) {
            true
        }

        private fun ensure(
            condition: Boolean,
            reason: () -> String,
        ) {
            if (!condition) refuse(reason())
        }

        private fun refuse(reason: String): Nothing = throw unreadable(reason)

        private fun unreadable(
            reason: String,
            cause: Throwable? = null,
        ): ProvisioningException = ProvisioningException(
            ErrorCode.PLAY_KEY_UNREADABLE,
            "The configured Google Play service-account key $reason.",
            REMEDY,
            cause,
        )
    }
}
