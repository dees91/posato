package app.posato.windows.helper

import java.net.InetAddress
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-host address ownership and stale resolution on Windows (TARGETS-013, ADR 0010 amendment, gate G15 and G16):
 * an address stays filtered while any blocked host owns it, a resolution that completes after a window boundary or
 * a new apply is discarded, a refresh adds addresses only to hosts blocked now, the limit is reserved over every held
 * host, and the plans of the last apply are followed at a part end with no new request.
 */
class BlockPlanTest {
    private val utc = ZoneId.of("UTC")
    private val ruled = "ruled.example"
    private val unruled = "unruled.example"
    private val shared = address("93.184.216.34")
    private val ruledOnly = address("93.184.216.35")
    private val unruledOnly = address("93.184.216.36")

    /** The ruled host opens 10:30 to 10:35 (after an unruled part ends at 10:10); the unruled host never opens. */
    private fun request(session: String = "s1") = HelperRequest.Apply(
        session,
        AT_1100,
        listOf(ruled, unruled),
        emptyList(),
        listOf(HostWindowPlan(ruled, listOf(WindowSegment(AT_1010, 0, 0), WindowSegment(AT_1100, 30, 5)))),
    )

    private fun applied(now: Long = AT_1000): BlockPlan {
        val resolved = mapOf(ruled to listOf(shared, ruledOnly), unruled to listOf(shared, unruledOnly))
        return BlockPlan.empty { utc }.applied(request(), resolved, now)
    }

    @Test
    fun `given a ruled and an unruled host sharing an address when the window opens then the shared address stays filtered`() {
        val plan = applied()
        assertEquals(setOf(shared, ruledOnly, unruledOnly), plan.filters())

        assertTrue(plan.evaluate(AT_1030))

        assertEquals(setOf(shared, unruledOnly), plan.filters())
    }

    @Test
    fun `given an open window when it closes then the ruled host's own addresses are filtered again`() {
        val plan = applied()
        plan.evaluate(AT_1030)

        assertTrue(plan.evaluate(AT_1030 + 5 * MINUTE))

        assertEquals(setOf(shared, ruledOnly, unruledOnly), plan.filters())
    }

    @Test
    fun `given a part end with no new request when the instants around the opening are evaluated then it opens at b only`() {
        val plan = applied()

        assertFalse(plan.evaluate(AT_1010))
        assertFalse(plan.evaluate(AT_1030 - SECOND))
        assertFalse(plan.evaluate(AT_1030 - 1))
        assertEquals(setOf(shared, ruledOnly, unruledOnly), plan.filters())
        assertTrue(plan.evaluate(AT_1030))
        assertEquals(setOf(shared, unruledOnly), plan.filters())
    }

    @Test
    fun `given a resolution started before a window boundary when it completes after it then it is discarded`() {
        val plan = applied()
        val ticket = plan.ticket()
        plan.evaluate(AT_1030)

        val late = address("93.184.216.99")
        assertFalse(plan.merge(ticket, mapOf(ruled to listOf(late), unruled to listOf(late)), AT_1030 + SECOND))

        assertEquals(setOf(shared, unruledOnly), plan.filters())
        assertTrue(plan.merge(plan.ticket(), mapOf(unruled to listOf(late)), AT_1030 + SECOND))
        assertEquals(setOf(shared, unruledOnly, late), plan.filters())
    }

    @Test
    fun `given a resolution started under an earlier apply when it completes then it is discarded`() {
        val first = applied()
        val ticket = first.ticket()
        val second = first.applied(request("s2"), mapOf(unruled to listOf(unruledOnly)), AT_1000)

        assertFalse(second.merge(ticket, mapOf(unruled to listOf(address("93.184.216.99"))), AT_1000 + SECOND))
        assertEquals(setOf(unruledOnly), second.filters())
    }

    @Test
    fun `given a refresh during an open window when it completes then the open host gains no addresses`() {
        val plan = applied()
        plan.evaluate(AT_1030)
        val fresh = address("93.184.216.77")

        assertTrue(plan.merge(plan.ticket(), mapOf(ruled to listOf(fresh), unruled to emptyList()), AT_1030 + SECOND))
        assertTrue(plan.evaluate(AT_1030 + 5 * MINUTE))

        assertEquals(setOf(shared, ruledOnly, unruledOnly), plan.filters())
    }

    @Test
    fun `given an apply inside the window when applied then only the hosts blocked now are filtered`() {
        val plan = applied(AT_1030 + MINUTE)

        assertEquals(setOf(shared, unruledOnly), plan.filters())
    }

    @Test
    fun `given held hosts that fill the limit when a ruled host cannot reserve its addresses then it stays blocked`() {
        val many = (0 until FilterPlan.MAX_ADDRESSES).map { index -> address("11.${index shr 16 and 255}.${index shr 8 and 255}.${index and 255}") }
        val plan = BlockPlan.empty { utc }.applied(request(), mapOf(unruled to many, ruled to listOf(ruledOnly)), AT_1000)

        plan.evaluate(AT_1030)

        assertEquals(FilterPlan.MAX_ADDRESSES, plan.filters().size)
        assertFalse(ruledOnly in plan.filters())
        assertEquals(listOf(ruled, unruled), plan.blockedHosts())
    }

    @Test
    fun `given an open window when a refresh would grow the held union past the limit then the open host's addresses still count`() {
        val plan = applied()
        plan.evaluate(AT_1030)
        val filler = (0 until FilterPlan.MAX_ADDRESSES).map { index -> address("12.${index shr 16 and 255}.${index shr 8 and 255}.${index and 255}") }

        plan.merge(plan.ticket(), mapOf(unruled to filler), AT_1030 + SECOND)
        plan.evaluate(AT_1030 + 5 * MINUTE)

        assertEquals(FilterPlan.MAX_ADDRESSES, plan.filters().size)
        assertTrue(ruledOnly in plan.filters())
    }

    private fun address(literal: String): InetAddress = InetAddress.getByName(literal)

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60_000L
        const val AT_1000 = 1_793_613_600_000L
        const val AT_1010 = AT_1000 + 10 * MINUTE
        const val AT_1030 = AT_1000 + 30 * MINUTE
        const val AT_1100 = AT_1000 + 60 * MINUTE
    }
}
