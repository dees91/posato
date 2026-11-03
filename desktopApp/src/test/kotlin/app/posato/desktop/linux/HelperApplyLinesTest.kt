package app.posato.desktop.linux

import app.posato.feature.enforcement.DomainWindowPlan
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.WindowSegment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wire contract with the Linux and Windows services (ADR 0010 TARGETS-013 amendment): plans travel in a sixth
 * field that the services parse, and the five-field form follows as the fallback for a service that refuses it.
 */
class HelperApplyLinesTest {
    private fun request(windows: List<DomainWindowPlan>) = EnforcementRequest(
        domains = listOf("example.com", "example.org"),
        mappingIds = emptyList(),
        sessionId = "session-1",
        sessionStartEpochMillis = 1L,
        sessionEndEpochMillis = 3_600_000L,
        windows = windows,
    )

    @Test
    fun `given window plans when the lines are built then the six field form comes first and the five field form follows`() {
        val plan = DomainWindowPlan("example.com", listOf(WindowSegment(600_000L, 0, 0), WindowSegment(3_600_000L, 30, 5)))

        val lines = HelperApplyLines.lines(request(listOf(plan)), "session-1", listOf("/usr/bin/foo"))

        assertEquals(
            listOf(
                "apply\tsession-1\t3600000\texample.com,example.org\t/usr/bin/foo\texample.com=600000:0:0;3600000:30:5",
                "apply\tsession-1\t3600000\texample.com,example.org\t/usr/bin/foo",
            ),
            lines,
        )
    }

    @Test
    fun `given no plans or plans past the field limit when the lines are built then only the five field form is sent`() {
        val segments = (1..22).map { index -> WindowSegment(1_793_613_600_000L + index, 30, 5) }
        val many = (0 until 1_000).map { index -> DomainWindowPlan("host-$index.example", segments) }

        assertEquals(1, HelperApplyLines.lines(request(emptyList()), "session-1", emptyList()).size)
        assertEquals(1, HelperApplyLines.lines(request(many), "session-1", emptyList()).size)
    }
}
