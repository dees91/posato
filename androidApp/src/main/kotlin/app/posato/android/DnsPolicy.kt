package app.posato.android

import app.posato.feature.enforcement.WebsiteWindowGate
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

/**
 * Decided at query time from the saved pause, so a window opens or closes, and a part's end changes the segment,
 * with no app request: a held name is blocked unless its valid plan is open now, and an open answer may be cached no
 * longer than the window's rest (at least [MIN_TTL_SECONDS]) so caches expire near the close.
 */
internal object DnsPolicy {
    private const val WWW = "www."
    private const val MIN_TTL_SECONDS = 30
    private const val MILLIS_PER_SECOND = 1_000L

    fun answer(
        name: String,
        pause: AppliedPause?,
        epochMillis: Long,
        zone: ZoneId,
    ): DnsAnswer {
        val domains = pause?.domains.orEmpty()
        val held = listOfNotNull(name, name.removePrefix(WWW).takeIf { name.startsWith(WWW) }).filter { it in domains }
        if (pause == null || held.isEmpty()) return DnsAnswer.Forward(null)
        val blocked = WebsiteWindowGate.blockedNow(domains, pause.windows, pause.endEpochMillis, epochMillis, zone)
        if (held.any { it in blocked }) return DnsAnswer.Blocked
        val closes = held.mapNotNull { domain ->
            WebsiteWindowGate.openUntil(domain, domains, pause.windows, pause.endEpochMillis, epochMillis, zone)
        }.minOrNull() ?: return DnsAnswer.Blocked
        val rest = ((closes - epochMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
        return DnsAnswer.Forward(maxOf(rest, MIN_TTL_SECONDS))
    }
}
