package app.posato.feature.targets.domain

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

internal enum class TargetPolicyValidationFailure {
    INVALID_CANONICAL_DOMAIN,
    INVALID_APPLICATION_POLICY_NAME,
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

internal sealed interface TargetPolicyValidationResult {
    data class Success(
        val policy: TargetPolicy,
    ) : TargetPolicyValidationResult

    data class Failure(
        val reason: TargetPolicyValidationFailure,
    ) : TargetPolicyValidationResult
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

internal class TargetPolicy private constructor(
    val domains: List<ExactDomain>,
    val applicationPolicyName: ApplicationPolicyName?,
) {
    override fun equals(other: Any?): Boolean {
        return other is TargetPolicy &&
            domains == other.domains &&
            applicationPolicyName == other.applicationPolicyName
    }

    override fun hashCode(): Int {
        return 31 * domains.hashCode() + applicationPolicyName.hashCode()
    }

    override fun toString(): String {
        return "TargetPolicy(redacted)"
    }

    companion object {
        fun fromStoredValues(
            canonicalDomains: Iterable<String>,
            applicationPolicyName: String?,
        ): TargetPolicyValidationResult {
            val domains = linkedSetOf<ExactDomain>()
            var failure: TargetPolicyValidationFailure? = null
            for (value in canonicalDomains) {
                val valueFailure = domains.addCanonicalValue(value)
                if (valueFailure != null) {
                    failure = valueFailure
                    break
                }
            }
            val restoredApplicationPolicyName = applicationPolicyName?.let(ApplicationPolicyName::restore)
            if (applicationPolicyName != null && restoredApplicationPolicyName == null) {
                failure = TargetPolicyValidationFailure.INVALID_APPLICATION_POLICY_NAME
            }

            return if (failure == null) {
                TargetPolicyValidationResult.Success(
                    TargetPolicy(
                        domains = domains.sortedBy(ExactDomain::canonicalValue),
                        applicationPolicyName = restoredApplicationPolicyName,
                    ),
                )
            } else {
                TargetPolicyValidationResult.Failure(failure)
            }
        }

        fun empty(): TargetPolicy {
            return TargetPolicy(emptyList(), null)
        }
    }
}

private fun MutableSet<ExactDomain>.addCanonicalValue(value: String): TargetPolicyValidationFailure? {
    return when (val domain = ExactDomain.restore(value)) {
        null -> {
            TargetPolicyValidationFailure.INVALID_CANONICAL_DOMAIN
        }

        else -> {
            add(domain)
            if (size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) {
                TargetPolicyValidationFailure.TOO_MANY_DOMAINS
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
            !labels.last().isWhatwgIpv4Number() &&
            labels.all(String::isCanonicalDomainLabel)
    } else {
        false
    }
}

private fun String.isWhatwgIpv4Number(): Boolean {
    val isDecimalNumber = all { character -> character in '0'..'9' }
    val isHexadecimalNumber = startsWith("0x") && drop(2).all(Char::isAsciiHexDigit)

    return isDecimalNumber || isHexadecimalNumber
}

private fun String.isCanonicalDomainLabel(): Boolean {
    val validLength = length in ExactDomainPolicyLimits.MIN_LABEL_LENGTH..ExactDomainPolicyLimits.MAX_LABEL_LENGTH
    val validEdges = validLength && first().isAsciiLetterOrDigit() && last().isAsciiLetterOrDigit()
    val hasReservedHyphens = length >= RESERVED_HYPHEN_MINIMUM_LENGTH &&
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

private fun Char.isAsciiHexDigit(): Boolean {
    return this in '0'..'9' || this in 'a'..'f'
}
