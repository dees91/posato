package app.posato.provisioning.play

import app.posato.provisioning.asc.Sleeper
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Transcript
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration

/** The one Android app every Play command acts on. */
const val PLAY_PACKAGE = "app.posato.android"

private const val HOST = "androidpublisher.googleapis.com"
private const val API_BASE = "https://$HOST/androidpublisher/v3/applications/$PLAY_PACKAGE/"
private const val UPLOAD_BASE = "https://$HOST/upload/androidpublisher/v3/applications/$PLAY_PACKAGE/"
private const val GOOGLE_API_SUFFIX = ".googleapis.com"
private const val HTTPS_PORT = 443
private const val DEFAULT_PORT = -1
private val TOTAL_DEADLINE: Duration = Duration.ofSeconds(60)

/** Uploading and processing a bundle or a large mapping takes minutes; Google suggests raising client timeouts. */
private val UPLOAD_TIMEOUT: Duration = Duration.ofMinutes(10)
private const val MAX_BODY_BYTES = 4 * 1024 * 1024
private const val MAX_GET_ATTEMPTS = 3
private const val SUCCESS_FLOOR = 200
private const val SUCCESS_CEILING = 299
private val FIRST_BACKOFF: Duration = Duration.ofSeconds(1)
private val SEGMENT = Regex("[A-Za-z0-9._:-]+")
private val RELATIVE_SEGMENTS = setOf(".", "..")
private const val JSON = "application/json"

/**
 * Every Android Publisher request, bounded the same way as the App Store Connect client.
 *
 * Requests go only to `androidpublisher.googleapis.com` under this app's package, over paths built from constants and
 * identifiers that must be plain segments. Only `GET` is retried: a write that timed out may already be part of the
 * edit, and since nothing is final before the commit, rerunning the command (with a fresh edit) is the safe recovery.
 */
class PlayHttp(
    private val bearer: BearerSource,
    private val transport: PlayTransport,
    private val clock: Clock,
    private val sleeper: Sleeper,
    private val transcript: Transcript,
) {
    fun json(
        method: WireMethod,
        path: String,
        body: JsonElement? = null,
    ): JsonObject {
        val uri = URI.create(API_BASE + checked(path))
        val bytes = body?.let { PlayJson.json.encodeToString(JsonElement.serializer(), it).toByteArray(StandardCharsets.UTF_8) }
            ?: if (method == WireMethod.POST || method == WireMethod.PUT) ByteArray(0) else null
        val headers = listOfNotNull("Accept" to JSON, bytes?.let { "Content-Type" to JSON })
        val deadline = clock.instant().plus(TOTAL_DEADLINE)
        val attempts = if (method == WireMethod.GET) MAX_GET_ATTEMPTS else 1
        var failure = PlayError.unreachable(null)
        for (index in 0 until attempts) {
            if (index > 0) sleeper.sleep(FIRST_BACKOFF.multipliedBy(1L shl (index - 1)))
            val remaining = Duration.between(clock.instant(), deadline)
            if (remaining.isNegative || remaining.isZero) break
            transcript.record("$method $path")
            val outcome = attempt(WireRequest(method, uri, headers, bytes, remaining))
            if (outcome.response != null) return document(outcome.response)
            if (!outcome.retryable) throw outcome.failure
            failure = outcome.failure
        }
        throw failure
    }

    /** Writes one line to the redacted `--verbose` transcript. */
    fun record(line: String) = transcript.record(line)

    /** A simple media upload (`uploadType=media`): the bytes in one request, attempted once. */
    fun media(
        path: String,
        contentType: String,
        bytes: ByteArray,
    ): JsonObject {
        val uri = URI.create("${UPLOAD_BASE}${checked(path)}?uploadType=media")
        transcript.record("POST upload $path (${bytes.size} bytes)")
        return document(once(WireRequest(WireMethod.POST, uri, listOf("Content-Type" to contentType), bytes, UPLOAD_TIMEOUT)))
    }

    /**
     * A resumable upload (`uploadType=resumable`): open a session, then send the whole file to the session URL in one
     * `PUT`. The session URL is the only service-supplied URL this tool follows; it must be https on a Google API host
     * on the default port without user information, because the bearer token travels with it. It is never printed,
     * since its query carries the upload id.
     */
    fun resumable(
        path: String,
        contentType: String,
        bytes: ByteArray,
    ): JsonObject {
        val start = URI.create("${UPLOAD_BASE}${checked(path)}?uploadType=resumable")
        transcript.record("POST resumable upload session $path")
        val headers = listOf("X-Upload-Content-Type" to contentType, "X-Upload-Content-Length" to bytes.size.toString())
        val opened = once(WireRequest(WireMethod.POST, start, headers, ByteArray(0), TOTAL_DEADLINE))
        val session = SessionUriPolicy.validate(opened.location)
        transcript.record("PUT resumable upload $path (${bytes.size} bytes)")
        return document(once(WireRequest(WireMethod.PUT, session, listOf("Content-Type" to contentType), bytes, UPLOAD_TIMEOUT)))
    }

    private fun once(request: WireRequest): WireResponse {
        val outcome = attempt(request)
        return outcome.response ?: throw outcome.failure
    }

    private fun attempt(request: WireRequest): Outcome {
        val authorized = WireRequest(
            request.method,
            request.uri,
            request.headers + ("Authorization" to "Bearer ${bearer.token()}"),
            request.body,
            request.timeout,
        )
        val response = try {
            transport.send(authorized, MAX_BODY_BYTES)
        } catch (exception: IOException) {
            return Outcome(null, PlayError.unreachable(exception), retryable = true)
        }
        if (response.body.size > MAX_BODY_BYTES) {
            throw ProvisioningException(
                ErrorCode.PLAY_RESPONSE_TOO_LARGE,
                "Google Play returned more than $MAX_BODY_BYTES bytes.",
                "Rerun the command; if it repeats, the response holds more than this tool expects.",
            )
        }
        if (response.status in SUCCESS_FLOOR..SUCCESS_CEILING) return Outcome(response, PlayError.unreachable(null), retryable = false)
        val failure = PlayError.of(response.status, String(response.body, StandardCharsets.UTF_8))
        failure.message?.let { message -> transcript.record("Google Play explained: $message") }
        return Outcome(null, failure.exception, PlayError.isRetryable(response.status))
    }

    private fun document(response: WireResponse): JsonObject {
        val text = String(response.body, StandardCharsets.UTF_8)
        if (text.isBlank()) return JsonObject(emptyMap())
        return try {
            PlayJson.json.decodeFromString(JsonObject.serializer(), text)
        } catch (exception: SerializationException) {
            throw unreadable(exception)
        } catch (exception: IllegalArgumentException) {
            throw unreadable(exception)
        }
    }

    private fun unreadable(cause: Exception): ProvisioningException = ProvisioningException(
        ErrorCode.COMMAND_FAILED,
        "A Google Play response could not be read.",
        "Rerun with --verbose for the redacted request transcript.",
        cause,
    )

    private fun checked(path: String): String {
        val segments = path.split("/")
        val plain = segments.all { segment -> segment.matches(SEGMENT) && segment !in RELATIVE_SEGMENTS }
        if (!plain) {
            throw ProvisioningException(
                ErrorCode.COMMAND_FAILED,
                "A Google Play path this tool built is not a plain resource path.",
                "This is a defect in the tool or an unexpected identifier from Google Play.",
            )
        }
        return path
    }

    private class Outcome(
        val response: WireResponse?,
        val failure: ProvisioningException,
        val retryable: Boolean,
    )
}

/**
 * The resumable session URL is the only service-supplied URL this tool follows, and the bearer token travels with it:
 * https on a Google API host on the default port, without user information. No refusal repeats it.
 */
private object SessionUriPolicy {
    fun validate(location: String?): URI {
        val uri = try {
            URI(location ?: refuse("it supplied no session URL"))
        } catch (exception: URISyntaxException) {
            throw refusal("its session URL cannot be parsed", exception)
        }
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme != "https") refuse("its session URL is not https")
        if (host != HOST && !host.endsWith(GOOGLE_API_SUFFIX)) refuse("its session URL is not on a Google API host")
        if (uri.rawUserInfo != null) refuse("its session URL carries user information")
        if (uri.port != DEFAULT_PORT && uri.port != HTTPS_PORT) refuse("its session URL names a port other than 443")
        return uri
    }

    private fun refuse(reason: String): Nothing = throw refusal(reason, null)

    private fun refusal(
        reason: String,
        cause: Throwable?,
    ): ProvisioningException = ProvisioningException(
        ErrorCode.PLAY_UPLOAD_REFUSED,
        "Google Play answered a resumable upload in a way this tool refuses: $reason.",
        "Nothing was uploaded and the edit is discarded. Rerun the command; if it repeats, the upload contract changed.",
        cause,
    )
}
