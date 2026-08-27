package app.posato.policy

import org.dexpace.kuri.idna.Idn
import kotlin.jvm.JvmInline

internal object ExactDomainPolicyLimits {
    const val MAX_RAW_INPUT_LENGTH: Int = 1_024
    const val MIN_DOMAIN_LENGTH: Int = 3
    const val MAX_DOMAIN_COUNT: Int = 1_024
    const val MAX_DOMAIN_LENGTH: Int = 253
    const val MIN_LABEL_LENGTH: Int = 1
    const val MAX_LABEL_LENGTH: Int = 63
}

private const val RESERVED_HYPHEN_FIRST_INDEX: Int = 2
private const val RESERVED_HYPHEN_SECOND_INDEX: Int = 3
private const val RESERVED_HYPHEN_MINIMUM_LENGTH: Int = 4

internal enum class ExactDomainPolicyValidationFailure {
    INVALID_CANONICAL_DOMAIN,
    TOO_MANY_DOMAINS,
}

internal enum class ExactDomainInputFailure {
    EMPTY,
    TOO_LONG,
    INVALID_DOMAIN,
}

internal sealed interface ExactDomainInputResult {
    data class Success(
        val domain: ExactDomain,
    ) : ExactDomainInputResult

    data class Failure(
        val reason: ExactDomainInputFailure,
    ) : ExactDomainInputResult
}

internal sealed interface ExactDomainPolicyValidationResult {
    data class Success(
        val policy: ExactDomainPolicy,
    ) : ExactDomainPolicyValidationResult

    data class Failure(
        val reason: ExactDomainPolicyValidationFailure,
    ) : ExactDomainPolicyValidationResult
}

@JvmInline
internal value class ExactDomain private constructor(
    val canonicalValue: String,
) {
    override fun toString(): String {
        return "ExactDomain(redacted)"
    }

    companion object {
        fun parse(rawInput: String): ExactDomainInputResult {
            return if (rawInput.length > ExactDomainPolicyLimits.MAX_RAW_INPUT_LENGTH) {
                ExactDomainInputResult.Failure(ExactDomainInputFailure.TOO_LONG)
            } else {
                parseBoundedInput(rawInput)
            }
        }

        private fun parseBoundedInput(rawInput: String): ExactDomainInputResult {
            val trimmedInput = rawInput.trim()
            if (trimmedInput.isEmpty()) {
                return ExactDomainInputResult.Failure(ExactDomainInputFailure.EMPTY)
            }
            val domainInput = if (trimmedInput.endsWith('.')) trimmedInput.dropLast(1) else trimmedInput
            val canonicalValue = Idn.toAscii(domainInput).getOrNull()
            val domain = canonicalValue?.let(::restore)

            return if (domain == null) {
                ExactDomainInputResult.Failure(ExactDomainInputFailure.INVALID_DOMAIN)
            } else {
                ExactDomainInputResult.Success(domain)
            }
        }

        fun restore(canonicalValue: String): ExactDomain? {
            if (!canonicalValue.isCanonicalExactDomain()) {
                return null
            }
            return ExactDomain(canonicalValue)
        }
    }
}

internal class ExactDomainPolicy private constructor(
    val domains: List<ExactDomain>,
) {
    override fun equals(other: Any?): Boolean {
        return other is ExactDomainPolicy && domains == other.domains
    }

    override fun hashCode(): Int {
        return domains.hashCode()
    }

    override fun toString(): String {
        return "ExactDomainPolicy(redacted)"
    }

    companion object {
        fun fromCanonicalValues(values: Iterable<String>): ExactDomainPolicyValidationResult {
            val domains = linkedSetOf<ExactDomain>()
            var failure: ExactDomainPolicyValidationFailure? = null
            for (value in values) {
                val valueFailure = domains.addCanonicalValue(value)
                if (valueFailure != null) {
                    failure = valueFailure
                    break
                }
            }

            return if (failure == null) {
                ExactDomainPolicyValidationResult.Success(
                    ExactDomainPolicy(domains.sortedBy(ExactDomain::canonicalValue)),
                )
            } else {
                ExactDomainPolicyValidationResult.Failure(failure)
            }
        }

        fun empty(): ExactDomainPolicy {
            return ExactDomainPolicy(emptyList())
        }
    }
}

private fun MutableSet<ExactDomain>.addCanonicalValue(value: String): ExactDomainPolicyValidationFailure? {
    return when (val domain = ExactDomain.restore(value)) {
        null -> {
            ExactDomainPolicyValidationFailure.INVALID_CANONICAL_DOMAIN
        }

        else -> {
            add(domain)
            if (size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) {
                ExactDomainPolicyValidationFailure.TOO_MANY_DOMAINS
            } else {
                null
            }
        }
    }
}

private fun String.isCanonicalExactDomain(): Boolean {
    val validLength = length in ExactDomainPolicyLimits.MIN_DOMAIN_LENGTH..ExactDomainPolicyLimits.MAX_DOMAIN_LENGTH
    val validCharacters = all(Char::isAsciiDomainCharacter)

    return if (validLength && validCharacters) {
        val labels = split('.')
        labels.size >= 2 &&
            !labels.all { label -> label.all(Char::isDigit) } &&
            labels.all(String::isCanonicalDomainLabel)
    } else {
        false
    }
}

private fun String.isCanonicalDomainLabel(): Boolean {
    val validLength = length in ExactDomainPolicyLimits.MIN_LABEL_LENGTH..ExactDomainPolicyLimits.MAX_LABEL_LENGTH
    val validEdges = validLength && first().isAsciiLetterOrDigit() && last().isAsciiLetterOrDigit()
    val hasReservedHyphens =
        length >= RESERVED_HYPHEN_MINIMUM_LENGTH &&
            this[RESERVED_HYPHEN_FIRST_INDEX] == '-' &&
            this[RESERVED_HYPHEN_SECOND_INDEX] == '-'

    return validEdges &&
        when {
            !hasReservedHyphens -> true
            startsWith("xn--") -> isStrictCanonicalALabel()
            else -> false
        }
}

private fun String.isStrictCanonicalALabel(): Boolean {
    val unicodeValue = Idn.toUnicode(this)
    val hasNonAsciiCharacter = unicodeValue.any { character -> character.code > 0x7F }

    return hasNonAsciiCharacter &&
        unicodeValue != this &&
        Idn.toAscii(unicodeValue).getOrNull() == this
}

private fun Char.isAsciiDomainCharacter(): Boolean {
    return isAsciiLetterOrDigit() || this == '-' || this == '.'
}

private fun Char.isAsciiLetterOrDigit(): Boolean {
    return this in 'a'..'z' || this in '0'..'9'
}
