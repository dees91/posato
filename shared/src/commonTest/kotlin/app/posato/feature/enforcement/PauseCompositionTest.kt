package app.posato.feature.enforcement

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How running parts compose into one pause: the union of their sets and what each already holds, the latest
 * end, and the device limits, where a new part takes what still fits in alphabetical order and an edit pauses
 * none of its new items until all of them fit. The iPhone vectors at the end are repeated in Swift.
 */
class PauseCompositionTest {
    private val small = PauseLimits(maxDomainCost = 3, maxApps = 2) { domains -> domains.size }

    @Test
    fun `given a session and an occurrence on different sets when composed then both sets are paused until the latest end`() {
        val plan = planPause(
            listOf(
                part("session", start = 10, end = 50, current = items("a.example"), retained = items("a.example")),
                part("evening", start = 20, end = 90, current = items("b.example"), retained = items("b.example")),
            ),
            PauseLimits.MAC,
        )

        assertEquals(setOf("a.example", "b.example"), plan.items.domains)
        assertEquals("session", plan.partId)
        assertEquals(10L, plan.startEpochMillis)
        assertEquals(90L, plan.endEpochMillis)
    }

    @Test
    fun `given one part ended when the rest are composed then only items no remaining part needs are released`() {
        val plan = planPause(
            listOf(
                part(
                    "evening",
                    start = 20,
                    end = 90,
                    current = items("b.example", "shared.example"),
                    retained = items("b.example", "shared.example"),
                ),
            ),
            PauseLimits.MAC,
        )

        assertEquals(setOf("b.example", "shared.example"), plan.items.domains)
    }

    @Test
    fun `given an item removed from a running part's set when composed then the part keeps pausing it`() {
        val plan = planPause(listOf(part("session", current = items(), retained = items("kept.example"))), PauseLimits.MAC)

        assertEquals(setOf("kept.example"), plan.items.domains)
        assertEquals(setOf("kept.example"), plan.held.getValue("session").domains)
    }

    @Test
    fun `given an item added to a running part's set when composed then it is paused at once and held by the part`() {
        val plan = planPause(listOf(part("session", current = items("a.example", "new.example"), retained = items("a.example"))), PauseLimits.MAC)

        assertEquals(setOf("a.example", "new.example"), plan.items.domains)
        assertEquals(setOf("a.example", "new.example"), plan.held.getValue("session").domains)
    }

    @Test
    fun `given a new part over the limit when composed then it pauses what fits in alphabetical order and counts the rest`() {
        val plan = planPause(
            listOf(part("session", current = items("d.example", "b.example", "a.example", "c.example"), retained = items())),
            small,
        )

        assertEquals(setOf("a.example", "b.example", "c.example"), plan.items.domains)
        assertEquals(1, plan.deferred.getValue("session"))
    }

    @Test
    fun `given an edit that would pass the limit when composed then none of its new items are paused until all fit`() {
        val parts = listOf(
            part("session", start = 10, current = items("a.example", "x.example", "y.example"), retained = items("a.example")),
            part("evening", start = 20, current = items("b.example"), retained = items("b.example")),
        )

        val crowded = planPause(parts, small)
        val roomy = planPause(listOf(parts.first()), small)

        assertEquals(setOf("a.example", "b.example"), crowded.items.domains)
        assertEquals(2, crowded.deferred.getValue("session"))
        assertEquals(setOf("a.example", "x.example", "y.example"), roomy.items.domains)
        assertEquals(0, roomy.deferred.getValue("session"))
    }

    @Test
    fun `given an item another part already pauses when composed then the joining part holds it too`() {
        val plan = planPause(
            listOf(
                part("session", start = 10, current = items("shared.example"), retained = items("shared.example")),
                part("evening", start = 20, current = items("shared.example"), retained = items()),
            ),
            small,
        )

        assertEquals(setOf("shared.example"), plan.held.getValue("evening").domains)
    }

    @Test
    fun `given a part whose set is gone when composed then it still pauses what it holds`() {
        val plan = planPause(listOf(part("evening", current = items(), retained = items("held.example"))), small)

        assertEquals(setOf("held.example"), plan.items.domains)
    }

    @Test
    fun `given apps over the limit for a new part when composed then the app limit applies on its own`() {
        val plan = planPause(listOf(part("session", current = PauseItems(appIds = setOf("c", "a", "b")), retained = items())), small)

        assertEquals(setOf("a", "b"), plan.items.appIds)
        assertEquals(1, plan.deferred.getValue("session"))
    }

    @Test
    fun `iphone vector given hosts and their www forms when composed then each host costs its pair once`() {
        val hosts = numbered("site", 1..26) + "www.site01.example"
        val plan = planPause(listOf(part("session", current = PauseItems(hosts), retained = items())), PauseLimits.IPHONE)

        assertEquals(numbered("site", 1..25) + "www.site01.example", plan.items.domains)
        assertEquals(1, plan.deferred.getValue("session"))
    }

    @Test
    fun `iphone vector given the other store holds websites when composed then they take their share of the 50`() {
        val occupied = PauseItems(numbered("manual", 1..20))
        val hosts = numbered("sched", 1..10)
        val plan = planPause(listOf(part("evening", current = PauseItems(hosts), retained = items())), PauseLimits.IPHONE, occupied)

        assertEquals(numbered("sched", 1..5), plan.items.domains)
        assertEquals(5, plan.deferred.getValue("evening"))
    }

    private fun numbered(
        prefix: String,
        range: IntRange,
    ): Set<String> {
        return range.mapTo(mutableSetOf()) { index -> "$prefix${index.toString().padStart(2, '0')}.example" }
    }

    private fun items(vararg domains: String): PauseItems {
        return PauseItems(domains.toSet())
    }

    private fun part(
        id: String,
        start: Long = 10,
        end: Long = 90,
        current: PauseItems,
        retained: PauseItems,
    ): RunningPartItems {
        return RunningPartItems(id, start, end, current, retained)
    }
}
