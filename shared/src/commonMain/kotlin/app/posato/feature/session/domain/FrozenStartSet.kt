package app.posato.feature.session.domain

import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

internal data class FrozenStartSet(
    val domains: PersistentList<String>,
    val applicationCount: Int?,
) {
    override fun toString(): String {
        return "FrozenStartSet(redacted)"
    }

    fun toStorageValue(): String {
        return domains.joinToString(STORAGE_SEPARATOR)
    }

    companion object {
        fun parseStored(
            domainsValue: String?,
            applicationCount: Long?,
        ): FrozenStartSet? {
            if (domainsValue == null && applicationCount == null) {
                return null
            }
            val domains = if (domainsValue.isNullOrEmpty()) {
                emptyList()
            } else {
                domainsValue.split(STORAGE_SEPARATOR)
            }
            require(domains.size <= ExactDomainPolicyLimits.MAX_DOMAIN_COUNT)
            require(domains.all { domain -> domain.length in ExactDomainPolicyLimits.MIN_DOMAIN_LENGTH..ExactDomainPolicyLimits.MAX_DOMAIN_LENGTH })
            val count = applicationCount?.let { value ->
                require(value >= 0 && value <= Int.MAX_VALUE)
                value.toInt()
            }

            return FrozenStartSet(domains.toPersistentList(), count)
        }
    }
}

private const val STORAGE_SEPARATOR: String = "\n"
