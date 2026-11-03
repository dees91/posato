package app.posato.windows.helper

import java.net.InetAddress
import java.time.ZoneId

/**
 * What the filters block during one apply (TARGETS-013, ADR 0010 amendment, gate G15 and G16). The addresses each
 * held host resolved to are kept per host, and the filters are the union of those of the hosts blocked now, so an
 * address a blocked host shares with a host open in its window stays filtered. The address limit is reserved over
 * every held host, open or not; a ruled host whose addresses cannot all be reserved stays blocked for the rest of the
 * request. A resolution counts only when neither the apply ([generation]) nor the blocked hosts ([epoch]) changed
 * while it ran, and then only for the hosts blocked now. Not thread-safe; the service holds its lock.
 */
internal class BlockPlan private constructor(
    val request: HelperRequest.Apply?,
    private val generation: Long,
    private val zone: () -> ZoneId,
) {
    /** The plan generation and the boundary epoch a resolution started under. */
    data class Ticket(
        val generation: Long,
        val epoch: Long,
    )

    private val owned = LinkedHashMap<String, MutableSet<InetAddress>>()
    private val reserved = HashSet<InetAddress>()
    private val unreservable = HashSet<String>()
    private var blocked: List<String> = request?.hosts.orEmpty()
    private var epoch = 0L

    /** The plan for [next], with the addresses [resolved] per held host before it was applied. */
    fun applied(
        next: HelperRequest.Apply,
        resolved: Map<String, Collection<InetAddress>>,
        now: Long,
    ): BlockPlan {
        val plan = BlockPlan(next, generation + 1, zone)
        plan.reserve(resolved, next.hosts)
        plan.blocked = plan.blockedNow(now)
        return plan
    }

    fun cleared(): BlockPlan {
        return BlockPlan(null, generation + 1, zone)
    }

    fun ticket(): Ticket {
        return Ticket(generation, epoch)
    }

    /** Adds [resolved] to the hosts blocked now, unless the apply or the blocked hosts changed since [ticket]. */
    fun merge(
        ticket: Ticket,
        resolved: Map<String, Collection<InetAddress>>,
        now: Long,
    ): Boolean {
        if (request == null || ticket != ticket() || now >= request.endEpochMillis) return false
        reserve(resolved, blocked)
        return true
    }

    /** Follows the plans to [now]; true when the hosts blocked now changed, which starts a new boundary epoch. */
    fun evaluate(now: Long): Boolean {
        val next = blockedNow(now)
        if (next == blocked) return false
        blocked = next
        epoch += 1
        return true
    }

    fun blockedHosts(): List<String> {
        return blocked
    }

    fun filters(): Set<InetAddress> {
        return blocked.flatMapTo(LinkedHashSet()) { host -> owned[host].orEmpty() }
    }

    private fun blockedNow(now: Long): List<String> {
        val applied = request ?: return emptyList()
        val plans = applied.windows.filter { plan -> plan.host !in unreservable }
        return WindowPlans.blockedNow(applied.hosts, plans, applied.endEpochMillis, now, zone())
    }

    /**
     * Gives each of [hosts] its blockable resolved addresses while the held union stays within the limit, hosts with no
     * plan first: they are blocked for the whole request, and a ruled host that misses room only loses its windows.
     */
    private fun reserve(
        resolved: Map<String, Collection<InetAddress>>,
        hosts: List<String>,
    ) {
        val ruled = request?.windows.orEmpty().mapTo(HashSet(), HostWindowPlan::host)
        for (host in hosts.sortedBy { it in ruled }) {
            val addresses = FilterPlan.blockable(resolved[host].orEmpty())
            val mine = owned.getOrPut(host) { LinkedHashSet() }
            for (address in addresses) {
                if (address in reserved || reserved.size < FilterPlan.MAX_ADDRESSES) {
                    reserved += address
                    mine += address
                } else {
                    unreservable += host
                }
            }
        }
    }

    companion object {
        fun empty(zone: () -> ZoneId): BlockPlan {
            return BlockPlan(null, 0, zone)
        }
    }
}
