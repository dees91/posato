package app.posato.feature.enforcement

import app.posato.feature.targets.domain.WebsiteWindow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared helper holds one request for the manual session and the running occurrences together. Failure
 * modes: the union drops a part's windows, so a website opens while another part keeps it unruled, or an
 * edited rule is hidden behind unchanged websites and never reaches the helper.
 */
class PauseClaimsWindowTest {
    private val halfHourly = checkNotNull(WebsiteWindow.of(30, 5))
    private val hourly = checkNotNull(WebsiteWindow.of(60, 5))

    private class RecordingHelper : EnforcementPort {
        val applied = mutableListOf<EnforcementRequest>()
        private var holding = false

        override val reapplyRequiresPrompt: Boolean = true

        override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
            applied += request
            holding = true
            return EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
        }

        override suspend fun clear(): EnforcementOutcome {
            holding = false
            return EnforcementOutcome.CLEARED
        }

        override suspend fun status(): EnforcementOutcome {
            return if (holding) EnforcementOutcome.APPLIED else EnforcementOutcome.CLEARED
        }
    }

    private fun scheduled(
        window: WebsiteWindow?,
        end: Long = 2_000,
    ): EnforcementRequest {
        val windows = window?.let { listOf(DomainWindowPlan("news.example", listOf(windowSegment(end, it)))) }.orEmpty()
        return EnforcementRequest(listOf("news.example"), emptyList(), "schedule", 0, end, windows = windows)
    }

    @Test
    fun `given an unruled manual session when a ruled schedule joins then the helper holds the union's segments`() = runTest {
        val helper = RecordingHelper()
        val claims = PauseClaims(helper)
        claims.manual.apply(EnforcementRequest(listOf("news.example"), emptyList(), "manual", 0, 1_000))

        claims.claimSchedule(scheduled(halfHourly))

        assertEquals(
            listOf(DomainWindowPlan("news.example", listOf(windowSegment(1_000, null), windowSegment(2_000, halfHourly)))),
            helper.applied.last().windows,
        )
        assertEquals(2_000, helper.applied.last().sessionEndEpochMillis)
    }

    @Test
    fun `given a held schedule when only its rule is edited or removed then the helper gets the new plans each time`() = runTest {
        val helper = RecordingHelper()
        val claims = PauseClaims(helper)
        claims.claimSchedule(scheduled(halfHourly))

        claims.updateSchedule(scheduled(hourly))
        assertEquals(listOf(windowSegment(2_000, hourly)), helper.applied.last().windows.single().segments)

        claims.updateSchedule(scheduled(null))
        assertEquals(emptyList(), helper.applied.last().windows)
        assertEquals(3, helper.applied.size)

        claims.updateSchedule(scheduled(null))
        assertEquals(3, helper.applied.size, "an unchanged request touches nothing")
    }

    private fun held(
        domain: String,
        window: WebsiteWindow?,
        end: Long,
    ): EnforcementRequest {
        val windows = window?.let { listOf(DomainWindowPlan(domain, listOf(windowSegment(end, it)))) }.orEmpty()
        return EnforcementRequest(listOf(domain), emptyList(), "part", 0, end, windows = windows)
    }

    private fun bothForms(vararg segments: WindowSegment): List<DomainWindowPlan> {
        return listOf("example.com", "www.example.com").map { domain -> DomainWindowPlan(domain, segments.toList()) }
    }

    @Test
    fun `given a ruled website and its unruled www form in the other request then both forms stay blocked while the unruled part runs`() = runTest {
        val helper = RecordingHelper()
        val claims = PauseClaims(helper)
        claims.manual.apply(held("example.com", halfHourly, 2_000))

        claims.claimSchedule(held("www.example.com", null, 1_000))

        assertEquals(
            bothForms(windowSegment(1_000, null), windowSegment(2_000, halfHourly)),
            helper.applied.last().windows.sortedBy(DomainWindowPlan::domain),
        )
    }

    @Test
    fun `given different rules on the two forms in the two requests then both forms follow their intersection and then the surviving rule`() = runTest {
        val helper = RecordingHelper()
        val claims = PauseClaims(helper)
        val quarter = checkNotNull(WebsiteWindow.of(30, 15))
        claims.manual.apply(held("example.com", quarter, 2_000))

        claims.claimSchedule(held("www.example.com", hourly, 1_000))

        assertEquals(
            bothForms(windowSegment(1_000, quarter.intersect(hourly)), windowSegment(2_000, quarter)),
            helper.applied.last().windows.sortedBy(DomainWindowPlan::domain),
        )
    }

    @Test
    fun `given an unruled form in the longer request then neither form of the website ever opens`() = runTest {
        val helper = RecordingHelper()
        val claims = PauseClaims(helper)
        claims.manual.apply(held("example.com", null, 2_000))

        claims.claimSchedule(held("www.example.com", halfHourly, 1_000))

        assertEquals(emptyList(), helper.applied.last().windows)
    }

    @Test
    fun `given a joined schedule when the manual session ends then the helper returns to the schedule's own plans`() = runTest {
        val helper = RecordingHelper()
        val claims = PauseClaims(helper)
        claims.manual.apply(EnforcementRequest(listOf("news.example"), emptyList(), "manual", 0, 1_000))
        claims.claimSchedule(scheduled(halfHourly))

        claims.manual.clear()

        assertEquals(listOf(DomainWindowPlan("news.example", listOf(windowSegment(2_000, halfHourly)))), helper.applied.last().windows)
    }
}
