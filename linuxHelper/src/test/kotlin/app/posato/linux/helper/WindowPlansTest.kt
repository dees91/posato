package app.posato.linux.helper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The window plans of the sixth apply field (TARGETS-013, ADR 0010 amendment): the grammar that a root service
 * accepts, the fail-closed handling of a plan that is well formed but invalid, and the evaluation on the wall clock
 * read in the zone of each call, against the one cross-target vector file.
 */
class WindowPlansTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `given a six field apply when parsed then its plans are kept and the five field form still parses`() {
        val six = assertIs<HelperRequest.Apply>(
            HelperRequest.parse("apply\ts\t$AT_1100\texample.com,example.org\t\texample.com=$AT_1010:0:0;$AT_1100:30:5"),
        )
        val five = assertIs<HelperRequest.Apply>(HelperRequest.parse("apply\ts\t$AT_1100\texample.com,example.org\t"))

        assertEquals(
            listOf(HostWindowPlan("example.com", listOf(WindowSegment(AT_1010, 0, 0), WindowSegment(AT_1100, 30, 5)))),
            six.windows,
        )
        assertEquals(emptyList(), five.windows)
        assertEquals(six, HelperRequest.parse(HelperState(six, null).encode().lines().first()))
    }

    @Test
    fun `given a sixth field outside the grammar when parsed then the whole request is refused`() {
        val head = "apply\ts\t$AT_1100\texample.com\t\t"
        assertNull(HelperRequest.parse("${head}example.com=$AT_1100:30"))
        assertNull(HelperRequest.parse("${head}example.com=$AT_1100:30:5:1"))
        assertNull(HelperRequest.parse("${head}example.com=-1:30:5"))
        assertNull(HelperRequest.parse("${head}example.com=$AT_1100:3a:5"))
        assertNull(HelperRequest.parse("${head}EXAMPLE.com=$AT_1100:30:5"))
        assertNull(HelperRequest.parse("${head}example.com=$AT_1100:30:5\n1.2.3.4 bank.example"))
        assertNull(HelperRequest.parse("$head=$AT_1100:30:5"))
        assertNull(HelperRequest.parse("$head${"x".repeat(300_000)}"))
    }

    @Test
    fun `given well formed but invalid plans when evaluated inside their window then their hosts stay blocked`() {
        val hosts = listOf("a.example", "b.example", "c.example", "d.example", "e.example", "f.example")
        val plans = listOf(
            HostWindowPlan("a.example", listOf(WindowSegment(AT_1100, 30, 5))),
            HostWindowPlan("b.example", listOf(WindowSegment(AT_1100, 30, 5))),
            HostWindowPlan("b.example", listOf(WindowSegment(AT_1100, 30, 5))),
            HostWindowPlan("c.example", listOf(WindowSegment(AT_1100, 30, 5), WindowSegment(AT_1010, 30, 5))),
            HostWindowPlan("d.example", listOf(WindowSegment(AT_1100 + 1, 30, 5))),
            HostWindowPlan("e.example", listOf(WindowSegment(AT_1100, 45, 5))),
            HostWindowPlan("f.example", (1..22).map { index -> WindowSegment(AT_1000 + index, 0, 0) } + WindowSegment(AT_1100, 30, 5)),
            HostWindowPlan("not-held.example", listOf(WindowSegment(AT_1100, 30, 5))),
        )

        assertEquals(hosts - "a.example", WindowPlans.blockedNow(hosts, plans, AT_1100, AT_1030, utc))
    }

    @Test
    fun `given a plan cut at a part end when the wall clock reaches each instant then the host opens at b and never before`() {
        val hosts = listOf("example.com", "example.org")
        val plans = listOf(HostWindowPlan("example.com", listOf(WindowSegment(AT_1010, 0, 0), WindowSegment(AT_1100, 30, 5))))

        fun blocked(at: Long) = WindowPlans.blockedNow(hosts, plans, AT_1100, at, utc)

        assertEquals(hosts, blocked(AT_1000 + 5 * MINUTE))
        assertEquals(hosts, blocked(AT_1030 - SECOND))
        assertEquals(hosts, blocked(AT_1030 - 1))
        assertEquals(listOf("example.org"), blocked(AT_1030))
        assertEquals(listOf("example.org"), blocked(AT_1030 + 5 * MINUTE - 1))
        assertEquals(hosts, blocked(AT_1030 + 5 * MINUTE))
        assertEquals(hosts, blocked(AT_1100))
    }

    @Test
    fun `given the shared vectors when the helper evaluates them then every open state matches`() {
        val cases = Json.parseToJsonElement(File(VECTORS).readText()).jsonObject.getValue("cases").jsonArray

        assertTrue(cases.size >= 20)
        cases.map { it.jsonObject }.forEach { case ->
            val name = case.getValue("name").jsonPrimitive.content
            val at = case.getValue("at").jsonPrimitive.long
            val segments = case.getValue("plan").jsonArray.map { it.jsonObject.segment() }
            val end = maxOf(segments.last().untilEpochMillis, at + 1)
            val zone = ZoneId.of(case.getValue("zone").jsonPrimitive.content)
            val blocked = WindowPlans.blockedNow(listOf(HOST), listOf(HostWindowPlan(HOST, segments)), end, at, zone)

            assertEquals(case.getValue("open").jsonPrimitive.boolean, blocked.isEmpty(), "$name: open")
        }
    }

    private fun JsonObject.segment(): WindowSegment {
        return WindowSegment(getValue("until").jsonPrimitive.long, getValue("period").jsonPrimitive.int, getValue("length").jsonPrimitive.int)
    }

    private companion object {
        /** Relative to this module, where Gradle runs its tests. */
        const val VECTORS = "../shared/src/commonTest/resources/website-window-vectors.json"
        const val HOST = "vector.example"
        const val SECOND = 1_000L
        const val MINUTE = 60_000L

        /** 2026-11-01 10:00 UTC and the instants after it. */
        const val AT_1000 = 1_793_613_600_000L
        const val AT_1010 = AT_1000 + 10 * MINUTE
        const val AT_1030 = AT_1000 + 30 * MINUTE
        const val AT_1100 = AT_1000 + 60 * MINUTE
    }
}
