package app.posato.android

import java.time.ZoneId

/** How the VPN answers one name during a pause (ADR 0010 and its TARGETS-013 amendment). */
internal sealed interface DnsAnswer {
    /** No reachable address. */
    data object Blocked : DnsAnswer

    /** Asked of the network underneath; a held website open in its window keeps its answer at most [maxTtlSeconds]. */
    data class Forward(
        val maxTtlSeconds: Int?,
    ) : DnsAnswer
}

internal object DnsPolicy {
    fun answer(
        name: String,
        pause: AppliedPause?,
        epochMillis: Long,
        zone: ZoneId,
    ): DnsAnswer {
        val domains = pause?.domains.orEmpty()
        val held = name in domains || (name.startsWith("www.") && name.removePrefix("www.") in domains)
        return if (held) DnsAnswer.Blocked else DnsAnswer.Forward(null)
    }
}
