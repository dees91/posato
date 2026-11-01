package app.posato.feature.enforcement

import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.OffsetScheduleZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.utc
import app.posato.feature.targets.domain.WebsiteWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The window calculator and the per-website segments (ADR 0006, 0004 TARGETS-013 amendments) with an injected
 * clock. A wrong answer here opens a website outside its window or keeps an open one past a part's end.
 */
class WindowPlansTest {
    private val utcZone = OffsetScheduleZone { 0L }
    private val day = ScheduleDate(2026, 11, 2)

    private fun at(
        hour: Int,
        minute: Int,
        second: Int = 0,
        millis: Int = 0,
    ): Long {
        return utc(day, hour * 60 + minute) + second * 1_000L + millis
    }

    private fun rule(
        period: Int,
        length: Int,
    ): WebsiteWindow {
        return checkNotNull(WebsiteWindow.of(period, length))
    }

    @Test
    fun `given each of the nine rules when read across a day then it opens at every aligned time for its length only`() {
        WebsiteWindow.ALL.forEach { window ->
            val p = window.periodMinutes
            val l = window.lengthMinutes
            assertTrue(WindowClock.isOpen(window, at(0, 0), utcZone), "$p/$l at 00:00")
            assertTrue(WindowClock.isOpen(window, at(0, l - 1, 59, 999), utcZone), "$p/$l just before its end")
            assertFalse(WindowClock.isOpen(window, at(0, l), utcZone), "$p/$l at its end")
            assertFalse(WindowClock.isOpen(window, at(0, p - 1, 59, 999), utcZone), "$p/$l just before the next")
            assertTrue(WindowClock.isOpen(window, at(p / 60, p % 60), utcZone), "$p/$l at the next aligned time")
            val openMinutes = (0 until 1_440).count { minute -> WindowClock.isOpen(window, at(minute / 60, minute % 60), utcZone) }
            assertEquals(1_440 / p * l, openMinutes, "$p/$l open minutes per day")
        }
    }

    @Test
    fun `given any two rules when intersected then the result is open exactly when both are`() {
        WebsiteWindow.ALL.forEach { first ->
            WebsiteWindow.ALL.forEach { second ->
                val both = first.intersect(second)
                assertEquals(rule(maxOf(first.periodMinutes, second.periodMinutes), minOf(first.lengthMinutes, second.lengthMinutes)), both)
                (0 until 1_440).forEach { minute ->
                    assertEquals(first.isOpenAt(minute) && second.isOpenAt(minute), both.isOpenAt(minute), "minute $minute")
                }
            }
        }
    }

    @Test
    fun `given instants around an opening when evaluated then it is blocked until the boundary and open from it`() {
        val window = rule(30, 5)

        assertFalse(WindowClock.isOpen(window, at(10, 29, 59), utcZone), "b - 1 s")
        assertFalse(WindowClock.isOpen(window, at(10, 29, 59, 999), utcZone), "b - 1 ms")
        assertTrue(WindowClock.isOpen(window, at(10, 30), utcZone), "b")
        assertTrue(WindowClock.isOpen(window, at(10, 34, 59, 999), utcZone), "end - 1 ms")
        assertFalse(WindowClock.isOpen(window, at(10, 35), utcZone), "end")
        assertEquals(at(10, 30), WindowClock.nextChange(window, at(10, 29, 59, 999), utcZone))
        assertEquals(at(10, 30), WindowClock.nextChange(window, at(10, 5), utcZone))
        assertEquals(at(10, 35), WindowClock.nextChange(window, at(10, 30), utcZone))
        assertEquals(at(11, 0), WindowClock.nextChange(window, at(10, 35), utcZone))
    }

    @Test
    fun `given a skipped local hour when windows fall inside it then they do not occur and the next aligned time applies`() {
        val beforeChange = utc(ScheduleDate(2026, 3, 29), 59) // 01:59 local, the clock jumps to 03:00
        val hourly = rule(60, 5)
        val everyTwoHours = rule(120, 15)

        assertEquals(utc(ScheduleDate(2026, 3, 29), 60), WindowClock.nextChange(hourly, beforeChange, CentralEuropeanZone))
        assertTrue(WindowClock.isOpen(hourly, utc(ScheduleDate(2026, 3, 29), 60), CentralEuropeanZone), "03:00 local")
        // 02:00 does not exist and 03:00 is not an even hour, so the next two-hour window is 04:00 local.
        assertFalse(WindowClock.isOpen(everyTwoHours, utc(ScheduleDate(2026, 3, 29), 60), CentralEuropeanZone), "03:00 local")
        assertEquals(utc(ScheduleDate(2026, 3, 29), 120), WindowClock.nextChange(everyTwoHours, beforeChange, CentralEuropeanZone))
    }

    @Test
    fun `given a repeated local hour when the clock shows a window twice then it opens once for each reading`() {
        val firstTwo = utc(ScheduleDate(2026, 10, 25), 0) // 02:00 local, first time
        val secondTwo = utc(ScheduleDate(2026, 10, 25), 60) // 02:00 local again
        val hourly = rule(60, 5)

        assertTrue(WindowClock.isOpen(hourly, firstTwo + 120_000, CentralEuropeanZone))
        assertTrue(WindowClock.isOpen(hourly, secondTwo + 120_000, CentralEuropeanZone))
        assertEquals(secondTwo, WindowClock.nextChange(hourly, firstTwo + 30 * 60_000, CentralEuropeanZone))
        assertEquals(secondTwo, WindowClock.nextChange(rule(30, 5), firstTwo + 56 * 60_000, CentralEuropeanZone))
    }

    @Test
    fun `given an unruled part ending before a ruled one when composed then the website opens at the next window after that end`() {
        val plans = windowPlans(
            listOf("news.example"),
            listOf(
                WindowPart(at(10, 10), setOf("news.example"), emptyMap()),
                WindowPart(at(11, 0), setOf("news.example"), mapOf("news.example" to rule(30, 5))),
            ),
        )
        val plan = plans.single()

        assertEquals(listOf(windowSegment(at(10, 10), null), windowSegment(at(11, 0), rule(30, 5))), plan.segments)
        assertFalse(plan.isOpenAt(at(10, 0), utcZone), "the 10:00 window is inside the unruled part")
        assertFalse(plan.isOpenAt(at(10, 29, 59, 999), utcZone))
        assertTrue(plan.isOpenAt(at(10, 30), utcZone), "opens with no new request")
        assertFalse(plan.isOpenAt(at(10, 35), utcZone))
        assertFalse(plan.isOpenAt(at(11, 0), utcZone), "blocked after the last covering part ends")
        assertEquals(WindowStatus.OpensAt(at(10, 30)), plan.status(at(10, 0), utcZone))
        assertEquals(WindowStatus.OpenUntil(at(10, 35)), plan.status(at(10, 31), utcZone))
        assertNull(plan.status(at(10, 40), utcZone), "no window is left before the part ends")
    }

    @Test
    fun `given two ruled parts with different rules and ends when composed then the intersection applies and then the surviving rule`() {
        val plan = windowPlans(
            listOf("news.example"),
            listOf(
                WindowPart(at(10, 40), setOf("news.example"), mapOf("news.example" to rule(60, 5))),
                WindowPart(at(11, 30), setOf("news.example"), mapOf("news.example" to rule(30, 15))),
            ),
        ).single()

        assertEquals(listOf(windowSegment(at(10, 40), rule(60, 5)), windowSegment(at(11, 30), rule(30, 15))), plan.segments)
        assertTrue(plan.isOpenAt(at(10, 2), utcZone))
        assertFalse(plan.isOpenAt(at(10, 10), utcZone), "30/15 alone would be open")
        assertFalse(plan.isOpenAt(at(10, 31), utcZone))
        assertTrue(plan.isOpenAt(at(11, 10), utcZone), "the surviving rule")
        assertFalse(plan.isOpenAt(at(11, 30), utcZone))
    }

    @Test
    fun `given parts that end together or never open when composed then equal cuts merge and a blocked website gets no plan`() {
        val plans = windowPlans(
            listOf("news.example", "mail.example"),
            listOf(
                WindowPart(at(11, 0), setOf("news.example", "mail.example"), mapOf("news.example" to rule(30, 5))),
                WindowPart(at(11, 0), setOf("news.example"), mapOf("news.example" to rule(60, 10))),
            ),
        )

        assertEquals(listOf(DomainWindowPlan("news.example", listOf(windowSegment(at(11, 0), rule(60, 5))))), plans)
    }

    @Test
    fun `given a website and its www form when one entry has no rule then both stay blocked, and with two rules both intersect`() {
        val unruled = windowPlans(
            listOf("example.com", "www.example.com"),
            listOf(WindowPart(at(11, 0), setOf("example.com", "www.example.com"), mapOf("example.com" to rule(30, 5)))),
        )
        val ruled = windowPlans(
            listOf("example.com", "www.example.com"),
            listOf(
                WindowPart(at(11, 0), setOf("example.com"), mapOf("example.com" to rule(30, 15))),
                WindowPart(at(11, 0), setOf("www.example.com"), mapOf("www.example.com" to rule(60, 5))),
            ),
        )

        assertEquals(emptyList(), unruled)
        assertEquals(setOf("example.com", "www.example.com"), ruled.map(DomainWindowPlan::domain).toSet())
        ruled.forEach { plan -> assertEquals(listOf(windowSegment(at(11, 0), rule(60, 5))), plan.segments, plan.domain) }
    }

    @Test
    fun `given the most parts that can run at once when composed then the plan stays within the cap and is valid`() {
        val parts = (1..2 * 10 + 1).map { index ->
            WindowPart(at(10, 0) + index * 60_000L, setOf("news.example"), mapOf("news.example" to WebsiteWindow.ALL[index % 9]))
        }
        val plan = windowPlans(listOf("news.example"), parts).single()

        assertTrue(plan.segments.size <= MAX_WINDOW_SEGMENTS)
        assertTrue(plan.isValid(at(12, 0)))
    }

    @Test
    fun `given malformed plans when validated then only invalid data is refused, never a plan that has aged`() {
        val end = at(12, 0)
        val valid = DomainWindowPlan("news.example", listOf(windowSegment(at(10, 0), null), windowSegment(at(11, 0), rule(30, 5))))

        assertTrue(valid.isValid(end))
        assertTrue(DomainWindowPlan("news.example", List(MAX_WINDOW_SEGMENTS) { i -> windowSegment(at(10, i), rule(30, 5)) }).isValid(end))
        listOf(
            DomainWindowPlan("news.example", emptyList()),
            DomainWindowPlan("news.example", List(MAX_WINDOW_SEGMENTS + 1) { i -> windowSegment(at(10, i), rule(30, 5)) }),
            DomainWindowPlan("news.example", listOf(windowSegment(at(11, 0), null), windowSegment(at(11, 0), rule(30, 5)))),
            DomainWindowPlan("news.example", listOf(windowSegment(at(11, 0), null), windowSegment(at(10, 0), rule(30, 5)))),
            DomainWindowPlan("news.example", listOf(windowSegment(end + 1, rule(30, 5)))),
            DomainWindowPlan("news.example", listOf(WindowSegment(at(11, 0), 30, 7))),
            DomainWindowPlan("news.example", listOf(WindowSegment(at(11, 0), 45, 5))),
            DomainWindowPlan("news.example", listOf(WindowSegment(at(11, 0), 0, 5))),
            DomainWindowPlan("news.example", listOf(WindowSegment(at(11, 0), 30, 0))),
        ).forEachIndexed { index, plan -> assertFalse(plan.isValid(end), "plan $index") }
    }

    @Test
    fun `given held websites with plans when evaluated then only a valid open plan unblocks its own held website`() {
        val open = DomainWindowPlan("open.example", listOf(windowSegment(at(11, 0), rule(30, 5))))
        val request = EnforcementRequest(
            domains = listOf("blocked.example", "invalid.example", "open.example", "plain.example"),
            mappingIds = emptyList(),
            sessionId = "session",
            sessionStartEpochMillis = at(9, 0),
            sessionEndEpochMillis = at(11, 0),
            windows = listOf(
                open,
                DomainWindowPlan("blocked.example", listOf(windowSegment(at(11, 0), null))),
                DomainWindowPlan("invalid.example", listOf(WindowSegment(at(11, 0), 30, 7))),
                DomainWindowPlan("stranger.example", listOf(windowSegment(at(11, 0), rule(30, 5)))),
            ),
        )

        assertEquals(listOf("blocked.example", "invalid.example", "plain.example"), request.blockedNow(at(10, 30), utcZone))
        assertEquals(request.domains, request.blockedNow(at(10, 35), utcZone))
        assertEquals(at(10, 35), request.nextWindowBoundary(at(10, 30), utcZone))
        assertEquals(at(11, 0), request.nextWindowBoundary(at(10, 35), utcZone))
    }

    @Test
    fun `given every held website in its window when evaluated then nothing is blocked until the window ends`() {
        val request = EnforcementRequest(
            domains = listOf("news.example"),
            mappingIds = emptyList(),
            sessionId = "session",
            sessionStartEpochMillis = at(9, 0),
            sessionEndEpochMillis = at(11, 0),
            windows = listOf(DomainWindowPlan("news.example", listOf(windowSegment(at(11, 0), rule(30, 5))))),
        )

        assertEquals(emptyList(), request.blockedNow(at(10, 31), utcZone))
        assertEquals(listOf("news.example"), request.blockedNow(at(10, 35), utcZone))
    }

    @Test
    fun `given a manual request and a scheduled one when united then the website follows both until the later end`() {
        val manual = EnforcementRequest(listOf("news.example"), emptyList(), "manual", at(9, 0), at(10, 10))
        val scheduled = EnforcementRequest(
            listOf("news.example", "mail.example"),
            emptyList(),
            "schedule",
            at(9, 30),
            at(11, 0),
            windows = listOf(
                DomainWindowPlan("news.example", listOf(windowSegment(at(11, 0), rule(30, 5)))),
                DomainWindowPlan("mail.example", listOf(windowSegment(at(11, 0), rule(60, 5)))),
            ),
        )

        assertEquals(
            listOf(
                DomainWindowPlan("mail.example", listOf(windowSegment(at(11, 0), rule(60, 5)))),
                DomainWindowPlan("news.example", listOf(windowSegment(at(10, 10), null), windowSegment(at(11, 0), rule(30, 5)))),
            ),
            manual.unionWindows(scheduled).sortedBy(DomainWindowPlan::domain),
        )
        assertEquals(manual.unionWindows(scheduled).toSet(), scheduled.unionWindows(manual).toSet())
        assertEquals(emptyList(), manual.unionWindows(manual))
    }
}
