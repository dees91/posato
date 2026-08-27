package app.posato.policy

import kotlin.jvm.JvmInline

internal object ExactDomainPolicyLimits {
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
    return validEdges && !hasReservedHyphens
}

private fun Char.isAsciiDomainCharacter(): Boolean {
    return isAsciiLetterOrDigit() || this == '-' || this == '.'
}

private fun Char.isAsciiLetterOrDigit(): Boolean {
    return this in 'a'..'z' || this in '0'..'9'
}
