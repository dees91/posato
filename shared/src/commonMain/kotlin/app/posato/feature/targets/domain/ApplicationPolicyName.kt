package app.posato.feature.targets.domain

import kotlin.jvm.JvmInline

internal object ApplicationPolicyNameLimits {
    const val MAX_RAW_INPUT_LENGTH: Int = 1_024
    const val MAX_UTF8_LENGTH: Int = 80
}

internal enum class ApplicationPolicyNameFailure {
    EMPTY,
    TOO_LONG,
    INVALID_CHARACTERS,
}

internal sealed interface ApplicationPolicyNameResult {
    data class Success(
        val name: ApplicationPolicyName,
    ) : ApplicationPolicyNameResult

    data class Failure(
        val reason: ApplicationPolicyNameFailure,
    ) : ApplicationPolicyNameResult
}

@JvmInline
internal value class ApplicationPolicyName private constructor(
    val canonicalValue: String,
) {
    override fun toString(): String {
        return "ApplicationPolicyName(redacted)"
    }

    companion object {
        fun parse(rawInput: String): ApplicationPolicyNameResult {
            val rawFailure = when {
                rawInput.length > ApplicationPolicyNameLimits.MAX_RAW_INPUT_LENGTH -> ApplicationPolicyNameFailure.TOO_LONG
                rawInput.any(Char::isApplicationPolicyControlCharacter) -> ApplicationPolicyNameFailure.INVALID_CHARACTERS
                else -> null
            }
            if (rawFailure != null) {
                return ApplicationPolicyNameResult.Failure(rawFailure)
            }
            val canonicalValue = normalizeApplicationPolicyNameNfc(rawInput.trim())

            return create(canonicalValue)
        }

        fun restore(canonicalValue: String): ApplicationPolicyName? {
            val isCanonical = canonicalValue == canonicalValue.trim() &&
                normalizeApplicationPolicyNameNfc(canonicalValue) == canonicalValue
            if (!isCanonical) {
                return null
            }

            return (create(canonicalValue) as? ApplicationPolicyNameResult.Success)?.name
        }

        private fun create(canonicalValue: String): ApplicationPolicyNameResult {
            val failure = canonicalValue.validationFailure()

            return if (failure == null) {
                ApplicationPolicyNameResult.Success(ApplicationPolicyName(canonicalValue))
            } else {
                ApplicationPolicyNameResult.Failure(failure)
            }
        }
    }
}

internal expect fun normalizeApplicationPolicyNameNfc(value: String): String

private fun Char.isApplicationPolicyControlCharacter(): Boolean {
    return this in '\u0000'..'\u001F' || this in '\u007F'..'\u009F'
}

private fun String.validationFailure(): ApplicationPolicyNameFailure? {
    val utf8Length = try {
        encodeToByteArray(throwOnInvalidSequence = true).size
    } catch (_: Exception) {
        return ApplicationPolicyNameFailure.INVALID_CHARACTERS
    }

    return when {
        isEmpty() -> ApplicationPolicyNameFailure.EMPTY
        any(Char::isApplicationPolicyControlCharacter) -> ApplicationPolicyNameFailure.INVALID_CHARACTERS
        utf8Length > ApplicationPolicyNameLimits.MAX_UTF8_LENGTH -> ApplicationPolicyNameFailure.TOO_LONG
        else -> null
    }
}
