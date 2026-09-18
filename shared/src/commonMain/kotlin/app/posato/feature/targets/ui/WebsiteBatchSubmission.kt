package app.posato.feature.targets.ui

import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainInputResult
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import org.dexpace.kuri.Url

internal const val MAX_WEBSITE_BATCH_LENGTH: Int = 65_536

internal sealed interface WebsiteBatchSubmission {
    data object TooLong : WebsiteBatchSubmission

    class Ready(
        val canonicalDomains: List<String>,
        val addedCount: Int,
        val duplicateCount: Int,
        val rejectedIndices: List<Int>,
    ) : WebsiteBatchSubmission {
        override fun toString(): String {
            return "WebsiteBatchSubmission.Ready(redacted)"
        }
    }
}

internal fun websiteBatchEntries(input: String): List<String> {
    return input.split(',', '\n', '\r').map(String::trim).filter(String::isNotEmpty)
}

internal fun createWebsiteBatchSubmission(
    input: String,
    existingDomains: List<String>
): WebsiteBatchSubmission {
    if (input.length > MAX_WEBSITE_BATCH_LENGTH) {
        return WebsiteBatchSubmission.TooLong
    }
    val domains = existingDomains.toMutableSet()
    val rejectedIndices = mutableListOf<Int>()
    var duplicateCount = 0
    var addedCount = 0
    websiteBatchEntries(input).forEachIndexed { index, entry ->
        val domain = parseWebsiteEntry(entry)
        when {
            domain == null -> {
                rejectedIndices.add(index)
            }

            domain in domains -> {
                duplicateCount++
            }

            domains.size >= ExactDomainPolicyLimits.MAX_DOMAIN_COUNT -> {
                rejectedIndices.add(index)
            }

            else -> {
                domains.add(domain)
                addedCount++
                val counterpart = ExactDomain.restore(domain)?.wwwCounterpart()?.canonicalValue
                if (counterpart != null && counterpart !in domains && domains.size < ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) {
                    domains.add(counterpart)
                    addedCount++
                }
            }
        }
    }
    return WebsiteBatchSubmission.Ready(
        canonicalDomains = domains.sorted(),
        addedCount = addedCount,
        duplicateCount = duplicateCount,
        rejectedIndices = rejectedIndices.toList(),
    )
}

private fun parseWebsiteEntry(input: String): String? {
    if (input.length > ExactDomainPolicyLimits.MAX_RAW_INPUT_LENGTH) {
        return null
    }
    val domainInput = when {
        input.startsWith("https://", ignoreCase = true) || input.startsWith("http://", ignoreCase = true) -> {
            websiteUrlHost(input) ?: return null
        }

        else -> {
            input
        }
    }
    return when (val result = ExactDomain.parse(domainInput)) {
        is ExactDomainInputResult.Success -> result.domain.canonicalValue
        is ExactDomainInputResult.Failure -> null
    }
}

private fun websiteUrlHost(input: String): String? {
    val authority = input.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isEmpty() || '@' in authority) {
        return null
    }
    if ('\\' in input || input.any(Char::isISOControl)) {
        return null
    }
    val url = Url.parse(input).getOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
        return null
    }
    return url.hostName
}
