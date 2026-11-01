package app.posato.android

import app.posato.feature.enforcement.DomainWindowPlan
import app.posato.feature.enforcement.WindowSegment
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The VPN decides each name at query time from the saved plans (TARGETS-013, ADR 0010 amendment): the ruled website
 * opens at its boundary and never a millisecond before, a part end needs no new apply, an unruled website and a
 * malformed plan stay blocked, and an open answer may be cached no longer than the window's rest, at least 30 s.
 */
class DnsPolicyTest {
    private val utc = ZoneId.of("UTC")
    private val pause = AppliedPause(
        "session-1",
        AT_1100,
        setOf("example.com", "example.org"),
        emptySet(),
        listOf(DomainWindowPlan("example.com", listOf(WindowSegment(AT_1010, 0, 0), WindowSegment(AT_1100, 30, 5)))),
    )

    private fun answer(
        name: String,
        at: Long,
        applied: AppliedPause? = pause,
    ) = DnsPolicy.answer(name, applied, at, utc)

    @Test
    fun `given a plan cut at a part end when queried around the opening then the ruled name opens at b and not before`() {
        assertEquals(DnsAnswer.Blocked, answer("example.com", AT_1010))
        assertEquals(DnsAnswer.Blocked, answer("example.com", AT_1030 - SECOND))
        assertEquals(DnsAnswer.Blocked, answer("www.example.com", AT_1030 - 1))
        assertEquals(DnsAnswer.Forward(300), answer("example.com", AT_1030))
        assertEquals(DnsAnswer.Forward(300), answer("www.example.com", AT_1030))
        assertEquals(DnsAnswer.Blocked, answer("example.com", AT_1030 + 5 * MINUTE))
    }

    @Test
    fun `given an open window when it nears its close then the time to live shrinks to the rest but not below 30 s`() {
        assertEquals(DnsAnswer.Forward(60), answer("example.com", AT_1030 + 4 * MINUTE))
        assertEquals(DnsAnswer.Forward(30), answer("example.com", AT_1030 + 5 * MINUTE - 10 * SECOND))
    }

    @Test
    fun `given the unruled website of the same pause when queried inside the window then it stays blocked`() {
        assertEquals(DnsAnswer.Blocked, answer("example.org", AT_1030 + MINUTE))
        assertEquals(DnsAnswer.Blocked, answer("www.example.org", AT_1030 + MINUTE))
        assertEquals(DnsAnswer.Forward(null), answer("other.example", AT_1030 + MINUTE))
    }

    @Test
    fun `given a malformed plan when queried inside its window then the website stays blocked`() {
        val late = pause.copy(windows = listOf(DomainWindowPlan("example.com", listOf(WindowSegment(AT_1100 + 1, 30, 5)))))
        val code = pause.copy(windows = listOf(DomainWindowPlan("example.com", listOf(WindowSegment(AT_1100, 45, 5)))))

        assertEquals(DnsAnswer.Blocked, answer("example.com", AT_1030 + MINUTE, late))
        assertEquals(DnsAnswer.Blocked, answer("example.com", AT_1030 + MINUTE, code))
    }

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60_000L
        const val AT_1000 = 1_793_613_600_000L
        const val AT_1010 = AT_1000 + 10 * MINUTE
        const val AT_1030 = AT_1000 + 30 * MINUTE
        const val AT_1100 = AT_1000 + 60 * MINUTE
    }
}
