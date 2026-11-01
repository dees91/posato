package app.posato.windows.helper

import java.net.InetAddress
import java.time.ZoneId

/**
 * What the filters block during one apply (TARGETS-013, ADR 0010 amendment): the addresses each held host resolved
 * to, kept per host, and the union of those of the hosts blocked now.
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

    fun applied(
        next: HelperRequest.Apply,
        resolved: Map<String, Collection<InetAddress>>,
        now: Long,
    ): BlockPlan {
        TODO("TARGETS-013 phase 4")
    }

    fun cleared(): BlockPlan {
        return BlockPlan(null, generation + 1, zone)
    }

    fun ticket(): Ticket {
        return Ticket(generation, 0)
    }

    fun merge(
        ticket: Ticket,
        resolved: Map<String, Collection<InetAddress>>,
        now: Long,
    ): Boolean {
        TODO("TARGETS-013 phase 4")
    }

    fun evaluate(now: Long): Boolean {
        return false
    }

    fun blockedHosts(): List<String> {
        return emptyList()
    }

    fun filters(): Set<InetAddress> {
        return emptySet()
    }

    companion object {
        fun empty(zone: () -> ZoneId): BlockPlan {
            return BlockPlan(null, 0, zone)
        }
    }
}
