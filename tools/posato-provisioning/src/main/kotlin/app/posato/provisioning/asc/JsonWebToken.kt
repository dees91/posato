package app.posato.provisioning.asc

import app.posato.provisioning.core.ProvisioningJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.util.Base64

private const val AUDIENCE = "appstoreconnect-v1"
private const val ALGORITHM = "ES256"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
private const val TOKEN_LIFETIME_SECONDS = 900L

/** App Store Connect rejects a token that lives longer than twenty minutes. */
private const val MAXIMUM_TOKEN_LIFETIME_SECONDS = 1200L

data class MintedToken(
    val value: String,
    val expiresAt: Instant,
)

/**
 * The signed JSON Web Token every App Store Connect request carries.
 *
 * There is no `scope` claim: it is required only for a key issued to an individual Apple ID, and Posato uses one
 * team key with the Admin role. Adding it would mean enumerating a path list that this tool would then have to keep
 * in step with its own endpoints for no gain.
 */
object JsonWebToken {
    fun mint(
        keyId: String,
        issuerId: String,
        privateKey: PrivateKey,
        clock: Clock
    ): MintedToken {
        require(TOKEN_LIFETIME_SECONDS <= MAXIMUM_TOKEN_LIFETIME_SECONDS) {
            "A token lifetime above $MAXIMUM_TOKEN_LIFETIME_SECONDS s is rejected by App Store Connect."
        }
        val issuedAt = clock.instant().epochSecond
        val expiresAt = issuedAt + TOKEN_LIFETIME_SECONDS
        val header = buildJsonObject {
            put("alg", ALGORITHM)
            put("kid", keyId)
            put("typ", "JWT")
        }
        val claims = buildJsonObject {
            put("iss", issuerId)
            put("iat", issuedAt)
            put("exp", expiresAt)
            put("aud", AUDIENCE)
        }
        val signingInput = "${encode(header)}.${encode(claims)}"
        val signature = EcdsaSignature.derToJose(sign(signingInput, privateKey))
        return MintedToken("$signingInput.${encode(signature)}", Instant.ofEpochSecond(expiresAt))
    }

    private fun sign(
        signingInput: String,
        privateKey: PrivateKey
    ): ByteArray {
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(signingInput.toByteArray(StandardCharsets.US_ASCII))
        return signer.sign()
    }

    private fun encode(json: JsonObject): String = encode(
        ProvisioningJson.compact.encodeToString(JsonObject.serializer(), json).toByteArray(StandardCharsets.UTF_8),
    )

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
