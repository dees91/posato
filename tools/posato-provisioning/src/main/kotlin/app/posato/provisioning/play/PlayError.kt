package app.posato.provisioning.play

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val NOT_FOUND = 404
private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR_FLOOR = 500

/** Google's status names and reasons are closed vocabularies of this shape; anything else is not repeated. */
private val ENUMERATED = Regex("[A-Za-z_]{1,64}")

@Serializable
private class ErrorDocument(
    val error: ErrorBody? = null,
)

@Serializable
private class ErrorBody(
    val status: String? = null,
    val message: String? = null,
    val errors: List<ErrorItem> = emptyList(),
)

@Serializable
private class ErrorItem(
    val reason: String? = null,
)

/** A failed Android Publisher response, the free-text part of which goes only to the redacted transcript. */
class PlayFailure(
    val exception: ProvisioningException,
    val message: String?,
)

/**
 * What a failed Android Publisher response means.
 *
 * The envelope carries the HTTP status, Google's `status` name, and the `reason` codes. The `message` often explains
 * the refusal well ("Version code 1060101 has already been used") but can quote submitted values or the service
 * account's address, so it reaches only the `--verbose` transcript, which the session redacts.
 */
object PlayError {
    fun isRetryable(status: Int): Boolean = status == TOO_MANY_REQUESTS || status >= SERVER_ERROR_FLOOR

    fun of(
        status: Int,
        body: String,
    ): PlayFailure {
        val document = try {
            PlayJson.json.decodeFromString(ErrorDocument.serializer(), body).error
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val names = listOfNotNull(document?.status) + document?.errors.orEmpty().mapNotNull { it.reason }
        val reported = names.filter { it.matches(ENUMERATED) }.distinct()
        val suffix = if (reported.isEmpty()) "" else ", ${reported.joinToString(", ")}"
        val code = codeFor(status)
        val exception = ProvisioningException(code, "${describe(code)} (HTTP $status$suffix).", hintFor(code))
        return PlayFailure(exception, document?.message)
    }

    fun unreachable(cause: Exception?): ProvisioningException = ProvisioningException(
        ErrorCode.PLAY_UNREACHABLE,
        "The Google Play Developer API could not be reached within the request deadline.",
        "Check this Mac's network connection and rerun the command.",
        cause,
    )

    private fun codeFor(status: Int): ErrorCode = when {
        status == UNAUTHORIZED -> ErrorCode.PLAY_UNAUTHORIZED
        status == FORBIDDEN -> ErrorCode.PLAY_FORBIDDEN
        status == NOT_FOUND -> ErrorCode.PLAY_NOT_FOUND
        status == TOO_MANY_REQUESTS -> ErrorCode.PLAY_RATE_LIMITED
        status >= SERVER_ERROR_FLOOR -> ErrorCode.PLAY_UNAVAILABLE
        else -> ErrorCode.PLAY_REJECTED
    }

    private fun describe(code: ErrorCode): String = when (code) {
        ErrorCode.PLAY_UNAUTHORIZED -> "Google rejected the access token"
        ErrorCode.PLAY_FORBIDDEN -> "The service account may not perform this operation on app.posato.android"
        ErrorCode.PLAY_NOT_FOUND -> "Google Play has no such app, edit, or resource"
        ErrorCode.PLAY_RATE_LIMITED -> "Google Play rate limited this Mac"
        ErrorCode.PLAY_UNAVAILABLE -> "The Google Play Developer API is unavailable"
        else -> "Google Play rejected the request"
    }

    private fun hintFor(code: ErrorCode): String = when (code) {
        ErrorCode.PLAY_UNAUTHORIZED -> {
            "Rerun the command; if it repeats, confirm the service-account key has not been deleted in Google Cloud."
        }

        ErrorCode.PLAY_FORBIDDEN -> {
            "Invite the service account in Play Console > Users and permissions with release and store-listing rights for the app."
        }

        ErrorCode.PLAY_NOT_FOUND -> {
            "The API acts only on an app whose first build was uploaded by hand in Play Console; check that it exists."
        }

        ErrorCode.PLAY_RATE_LIMITED, ErrorCode.PLAY_UNAVAILABLE -> {
            "Wait a few minutes and rerun the command."
        }

        else -> {
            "Rerun with --verbose for Google's redacted explanation."
        }
    }
}
