package app.posato.provisioning.play

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.core.Transcript
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Google's OAuth 2.0 token endpoint; fixed, never read from the key file. */
internal val TOKEN_URI: URI = URI.create("https://oauth2.googleapis.com/token")
internal const val PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"

/** Google accepts an assertion that lives at most one hour; it is used once, within seconds, so ten minutes is ample. */
private const val ASSERTION_LIFETIME_SECONDS = 600L
private val TOKEN_TIMEOUT: Duration = Duration.ofSeconds(30)
private val REFRESH_MARGIN: Duration = Duration.ofSeconds(60)

/** Google always states `expires_in`; without it the token is treated as short-lived rather than reused for an hour. */
private const val FALLBACK_LIFETIME_SECONDS = 300L
private const val MAX_TOKEN_BYTES = 64 * 1024
private const val SUCCESS_FLOOR = 200
private const val SUCCESS_CEILING = 299

/** The JSON settings for Play documents: unknown fields ignored, absent optional fields left out of a request. */
object PlayJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

/**
 * The signed assertion of the OAuth 2.0 service-account flow: RS256 over the header and a claim set naming the
 * service account, the Android Publisher scope, and Google's token endpoint as audience.
 */
object GoogleJwt {
    fun assertion(
        key: ServiceAccountKey,
        clock: Clock,
    ): String {
        val issuedAt = clock.instant().epochSecond
        val header = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
            key.privateKeyId?.let { id -> put("kid", id) }
        }
        val claims = buildJsonObject {
            put("iss", key.clientEmail)
            put("scope", PLAY_SCOPE)
            put("aud", TOKEN_URI.toString())
            put("iat", issuedAt)
            put("exp", issuedAt + ASSERTION_LIFETIME_SECONDS)
        }
        val signingInput = "${encode(header)}.${encode(claims)}"
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(key.privateKey)
        signer.update(signingInput.toByteArray(StandardCharsets.US_ASCII))
        return "$signingInput.${encode(signer.sign())}"
    }

    private fun encode(json: JsonObject): String = encode(
        ProvisioningJson.compact.encodeToString(JsonObject.serializer(), json).toByteArray(StandardCharsets.UTF_8),
    )

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Supplies the bearer token each Android Publisher request carries. */
fun interface BearerSource {
    fun token(): String
}

@Serializable
private class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
private class TokenError(
    val error: String? = null,
)

/**
 * One access token per process, exchanged on first use and replaced shortly before it expires. Nothing is written to
 * disk. A refused exchange reports Google's enumerated `error` code only; `error_description` can name the account.
 */
class AccessTokenSource(
    private val key: ServiceAccountKey,
    private val transport: PlayTransport,
    private val clock: Clock,
    private val transcript: Transcript,
) : BearerSource {
    private var cached: Pair<String, Instant>? = null

    override fun token(): String {
        cached?.let { (value, expiresAt) -> if (clock.instant().isBefore(expiresAt.minus(REFRESH_MARGIN))) return value }
        val exchanged = exchange()
        cached = exchanged
        return exchanged.first
    }

    private fun exchange(): Pair<String, Instant> {
        val form = "grant_type=${encode(GRANT_TYPE)}&assertion=${encode(GoogleJwt.assertion(key, clock))}"
        val request = WireRequest(
            WireMethod.POST,
            TOKEN_URI,
            listOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to "application/json"),
            form.toByteArray(StandardCharsets.US_ASCII),
            TOKEN_TIMEOUT,
        )
        transcript.record("POST oauth2 token exchange")
        val response = try {
            transport.send(request, MAX_TOKEN_BYTES)
        } catch (exception: IOException) {
            throw failed("Google's token endpoint could not be reached.", exception)
        }
        val body = String(response.body, StandardCharsets.UTF_8)
        if (response.status !in SUCCESS_FLOOR..SUCCESS_CEILING) {
            val code = decode(body, TokenError.serializer())?.error?.takeIf { it.matches(ERROR_CODE) }
            throw failed("Google refused the service-account assertion (HTTP ${response.status}${code?.let { ", $it" }.orEmpty()}).", null)
        }
        val token = decode(body, TokenResponse.serializer())
        val value = token?.accessToken?.takeIf { it.isNotBlank() } ?: refuse("Google's token response carried no access token.")
        return value to clock.instant().plusSeconds(token.expiresIn ?: FALLBACK_LIFETIME_SECONDS)
    }

    private fun <T> decode(
        body: String,
        serializer: KSerializer<T>,
    ): T? = try {
        PlayJson.json.decodeFromString(serializer, body)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun refuse(message: String): Nothing = throw failed(message, null)

    private fun failed(
        message: String,
        cause: Throwable?,
    ): ProvisioningException = ProvisioningException(
        ErrorCode.PLAY_TOKEN_FAILED,
        message,
        "Confirm the service-account key is current in Google Cloud and the Google Play Android Developer API is enabled for its project.",
        cause,
    )

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        val ERROR_CODE = Regex("[a-z_]{1,64}")
    }
}
