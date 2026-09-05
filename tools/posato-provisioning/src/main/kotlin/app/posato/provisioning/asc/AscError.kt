package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val NOT_FOUND = 404
private const val CONFLICT = 409
private const val UNPROCESSABLE = 422
private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR_FLOOR = 500

/**
 * What a failed App Store Connect response means, without repeating what it said.
 *
 * The response document's `title` and `detail` can echo a submitted value, so only the enumerated `code` strings
 * reach an envelope. They are a closed vocabulary such as `ENTITY_ERROR.ATTRIBUTE.INVALID`, which is enough to act
 * on and carries no data. Raw bodies are never stored or printed.
 */
object AscError {
    fun isRetryable(status: Int): Boolean = status == TOO_MANY_REQUESTS || status >= SERVER_ERROR_FLOOR

    fun of(response: AscResponse): ProvisioningException {
        val code = codeFor(response.status)
        val reported = codes(response.body)
        val suffix = if (reported.isEmpty()) "" else " Reported: ${reported.joinToString(", ")}."
        return ProvisioningException(code, "${describe(response.status)}$suffix", hintFor(code))
    }

    fun unreachable(cause: Exception?): ProvisioningException = ProvisioningException(
        ErrorCode.ASC_UNREACHABLE,
        "App Store Connect could not be reached within the request deadline.",
        "Check this Mac's network connection and rerun the command.",
        cause,
    )

    /** The enumerated error codes an App Store Connect error document carries, or nothing when it carries none. */
    fun codes(body: String): List<String> = try {
        ProvisioningJson.lenient.decodeFromString(JsonObject.serializer(), body)["errors"]
            ?.jsonArray
            ?.mapNotNull { entry -> entry.jsonObject["code"]?.jsonPrimitive?.content }
            .orEmpty()
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    private fun codeFor(status: Int): ErrorCode = when {
        status == UNAUTHORIZED -> ErrorCode.ASC_UNAUTHORIZED
        status == FORBIDDEN -> ErrorCode.ASC_FORBIDDEN
        status == NOT_FOUND -> ErrorCode.ASC_NOT_FOUND
        status == CONFLICT -> ErrorCode.ASC_CONFLICT
        status == UNPROCESSABLE -> ErrorCode.ASC_REJECTED
        status == TOO_MANY_REQUESTS -> ErrorCode.ASC_RATE_LIMITED
        status >= SERVER_ERROR_FLOOR -> ErrorCode.ASC_UNAVAILABLE
        else -> ErrorCode.ASC_REJECTED
    }

    private fun describe(status: Int): String = when (codeFor(status)) {
        ErrorCode.ASC_UNAUTHORIZED -> "App Store Connect rejected the key, the issuer, or the signed token (HTTP $status)."
        ErrorCode.ASC_FORBIDDEN -> "The App Store Connect key's role does not permit this operation (HTTP $status)."
        ErrorCode.ASC_NOT_FOUND -> "The requested App Store Connect resource does not exist (HTTP $status)."
        ErrorCode.ASC_CONFLICT -> "App Store Connect reports the resource already exists or is in a conflicting state (HTTP $status)."
        ErrorCode.ASC_RATE_LIMITED -> "App Store Connect rate limited this Mac (HTTP $status)."
        ErrorCode.ASC_UNAVAILABLE -> "App Store Connect is unavailable (HTTP $status)."
        else -> "App Store Connect rejected the request (HTTP $status)."
    }

    private fun hintFor(code: ErrorCode): String = when (code) {
        ErrorCode.ASC_UNAUTHORIZED -> {
            "Confirm the key is still active under Users and Access, and that the issuer belongs to the same team."
        }

        ErrorCode.ASC_FORBIDDEN -> {
            "The key needs the Admin role to create certificates, devices, and profiles."
        }

        ErrorCode.ASC_NOT_FOUND -> {
            "Register the resource in the developer portal, or correct the App ID."
        }

        ErrorCode.ASC_CONFLICT -> {
            "Rerun the command; it reuses whatever already exists."
        }

        ErrorCode.ASC_RATE_LIMITED -> {
            "Wait a few minutes and rerun the command."
        }

        ErrorCode.ASC_UNAVAILABLE -> {
            "Check Apple's system status and rerun the command."
        }

        else -> {
            "Rerun with --verbose for the redacted response category."
        }
    }
}
