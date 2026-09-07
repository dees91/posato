package app.posato.prototype.model

import org.dexpace.kuri.Url

internal sealed interface PrototypeDomainResult {
    data class Valid(
        val domain: String
    ) : PrototypeDomainResult

    data class Invalid(
        val message: String
    ) : PrototypeDomainResult
}

private const val MAXIMUM_DOMAIN_LENGTH: Int = 253
private const val MAXIMUM_LABEL_LENGTH: Int = 63
private val DomainLabel = Regex("[a-z0-9-]+")
private val Ipv4Address = Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")

internal fun parsePrototypeDomain(raw: String): PrototypeDomainResult {
    val input = raw.trim()
    if (input.isEmpty()) return PrototypeDomainResult.Invalid("Enter a domain or website URL.")
    val url = Url.parseOrNull(if ("://" in input) input else "https://$input")
        ?: return PrototypeDomainResult.Invalid("Use a valid domain such as news.example.")
    if (url.scheme !in setOf("http", "https") || url.username.isNotEmpty() || url.password.isNotEmpty()) {
        return PrototypeDomainResult.Invalid("Use a public website domain without credentials.")
    }
    val domain = url.hostName?.lowercase()?.removeSuffix(".").orEmpty()
    val labels = domain.split('.')
    val validLabels = labels.size > 1 && labels.all { label ->
        label.length in 1..MAXIMUM_LABEL_LENGTH && DomainLabel.matches(label) && !label.startsWith('-') && !label.endsWith('-')
    }

    return if (validLabels && domain.length <= MAXIMUM_DOMAIN_LENGTH && !Ipv4Address.matches(domain)) {
        PrototypeDomainResult.Valid(domain)
    } else {
        PrototypeDomainResult.Invalid("Use a valid domain such as news.example.")
    }
}
