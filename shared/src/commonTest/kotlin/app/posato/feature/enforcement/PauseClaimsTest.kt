package app.posato.feature.enforcement

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun request(
    end: Long,
    domains: List<String> = listOf("example.com"),
): EnforcementRequest {
    return EnforcementRequest(domains, emptyList(), "id", 0L, end)
}

private class HelperDouble(
    var applyOutcome: EnforcementOutcome = EnforcementOutcome.APPLIED,
    /** The real helper takes a new configuration only when idle and refuses one while it holds another. */
    val refusesWhileHolding: Boolean = false,
) : EnforcementPort {
    val calls = mutableListOf<String>()
    var applied = false
    var clearOutcome = EnforcementOutcome.CLEARED
    var lastDomains: List<String> = emptyList()

    override val reapplyRequiresPrompt: Boolean = true

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        if (refusesWhileHolding && applied) {
            calls += "refused"
            return EnforcementApplyReport(EnforcementOutcome.FAILED, false, false)
        }
        calls += if (request.grantOnly) "apply grant ${request.sessionEndEpochMillis}" else "apply ${request.sessionEndEpochMillis}"
        applied = applyOutcome == EnforcementOutcome.APPLIED
        lastDomains = request.domains
        return EnforcementApplyReport(applyOutcome, false, false)
    }

    override suspend fun clear(): EnforcementOutcome {
        calls += "clear"
        if (clearOutcome == EnforcementOutcome.CLEARED) {
            applied = false
        }
        return clearOutcome
    }

    override suspend fun status(): EnforcementOutcome {
        return if (applied) EnforcementOutcome.APPLIED else EnforcementOutcome.CLEARED
    }
}

class PauseClaimsTest {
    @Test
    fun `given a schedule alone when it claims then it applies through the grant only and releasing clears`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)

        assertEquals(EnforcementOutcome.APPLIED, claims.claimSchedule(request(200)).outcome)
        assertEquals(EnforcementOutcome.CLEARED, claims.manual.status())
        claims.releaseSchedule()

        assertEquals(listOf("apply grant 200", "clear"), helper.calls)
    }

    @Test
    fun `given an applied manual session lasting longer when a schedule starts then it joins without touching the helper`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(300))

        claims.claimSchedule(request(200))

        assertEquals(listOf("apply 300"), helper.calls)
        assertEquals(EnforcementOutcome.APPLIED, claims.manual.status())
    }

    @Test
    fun `given applied manual restrictions ending earlier when a schedule starts then it still joins without touching the helper`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(100))

        claims.claimSchedule(request(200))

        assertEquals(listOf("apply 100"), helper.calls)
    }

    @Test
    fun `given a scheduled pause applied when a manual session starts or resumes then it joins and a failure could not lift the schedule`() =
        runTest {
            val helper = HelperDouble()
            val claims = PauseClaims(helper)
            claims.claimSchedule(request(200))
            helper.applyOutcome = EnforcementOutcome.FAILED

            assertEquals(EnforcementOutcome.APPLIED, claims.manual.apply(request(100)).outcome)

            assertEquals(listOf("apply grant 200"), helper.calls)
            assertTrue(helper.applied)
        }

    @Test
    fun `given a manual session applied first when the schedule it joined ends then the helper is not touched`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(300))
        claims.claimSchedule(request(200))

        claims.releaseSchedule()

        assertEquals(listOf("apply 300"), helper.calls)
        assertEquals(EnforcementOutcome.APPLIED, claims.manual.status())
    }

    @Test
    fun `given a schedule applied first when a longer manual session joins then the helper takes its later end and the schedule's end leaves it`() =
        runTest {
            val helper = HelperDouble()
            val claims = PauseClaims(helper)
            claims.claimSchedule(request(200))

            claims.manual.apply(request(300))
            claims.releaseSchedule()

            assertEquals(listOf("apply grant 200", "clear", "apply grant 300"), helper.calls)
            assertEquals(EnforcementOutcome.APPLIED, claims.manual.status())
        }

    @Test
    fun `given a longer manual session joined a schedule when the schedule is updated unchanged then the helper keeps the later end`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200))
        claims.manual.apply(request(300))
        helper.calls.clear()

        claims.updateSchedule(request(200))

        assertEquals(emptyList(), helper.calls)
    }

    @Test
    fun `given the helper holds an earlier manual end when the schedule is updated then the combined end is applied`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(100))
        claims.claimSchedule(request(200))
        helper.calls.clear()

        // The helper still holds the manual end 100 while the combined pause lasts to 200.
        claims.updateSchedule(request(200))

        assertEquals(listOf("clear", "apply grant 200"), helper.calls)
    }

    @Test
    fun `given the manual request held when the schedule's re-apply fails at the manual end then the helper is cleared and the claim dropped`() =
        runTest {
            val helper = HelperDouble()
            val claims = PauseClaims(helper)
            claims.manual.apply(request(100))
            claims.claimSchedule(request(200))
            helper.applyOutcome = EnforcementOutcome.AUTHORIZATION_REQUIRED

            claims.manual.clear()

            assertEquals(listOf("apply 100", "clear", "apply grant 200", "clear"), helper.calls)
            assertEquals(EnforcementOutcome.CLEARED, claims.scheduleStatus())
        }

    @Test
    fun `given a clear that fails at the schedule's end then the next release retries it`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200))
        helper.clearOutcome = EnforcementOutcome.FAILED

        assertEquals(EnforcementOutcome.FAILED, claims.releaseSchedule())
        helper.clearOutcome = EnforcementOutcome.CLEARED
        assertEquals(EnforcementOutcome.CLEARED, claims.releaseSchedule())
        assertEquals(EnforcementOutcome.CLEARED, claims.releaseSchedule())

        assertEquals(listOf("apply grant 200", "clear", "clear"), helper.calls)
    }

    @Test
    fun `given a manual session ending inside a schedule then restrictions stay under the schedule's request`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200))
        claims.manual.apply(request(100))

        assertEquals(EnforcementOutcome.CLEARED, claims.manual.clear())

        assertEquals(listOf("apply grant 200"), helper.calls)
        assertTrue(helper.applied)
        assertEquals(EnforcementOutcome.APPLIED, claims.scheduleStatus())
    }

    @Test
    fun `given a grant the helper refuses when a schedule claims then nothing is claimed and a later release does not clear a manual session`() =
        runTest {
            val helper = HelperDouble(applyOutcome = EnforcementOutcome.AUTHORIZATION_REQUIRED)
            val claims = PauseClaims(helper)

            assertEquals(EnforcementOutcome.AUTHORIZATION_REQUIRED, claims.claimSchedule(request(200)).outcome)
            assertEquals(EnforcementOutcome.CLEARED, claims.scheduleStatus())
        }

    @Test
    fun `given a helper that lost the restrictions then the schedule status drops the claim so the next attempt applies again`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200))
        helper.applied = false

        assertEquals(EnforcementOutcome.CLEARED, claims.scheduleStatus())
        claims.releaseSchedule()
        claims.claimSchedule(request(200))

        assertEquals(listOf("apply grant 200", "apply grant 200"), helper.calls)
    }

    @Test
    fun `given a held schedule when its paused items change then the helper gets them through the grant at once`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200))

        claims.updateSchedule(request(200))
        claims.updateSchedule(request(200, listOf("example.com", "example.org")))

        assertEquals(listOf("apply grant 200", "clear", "apply grant 200"), helper.calls)
        assertEquals(listOf("example.com", "example.org"), helper.lastDomains)
    }

    @Test
    fun `given a held schedule when the combined end moves later then the helper gets the later deadline`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200))

        claims.updateSchedule(request(260))

        assertEquals(listOf("apply grant 200", "clear", "apply grant 260"), helper.calls)
    }

    @Test
    fun `given a joined manual session when the schedule's items differ then they are applied and the manual end is kept`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(300))
        claims.claimSchedule(request(200))

        claims.updateSchedule(request(200))
        claims.updateSchedule(request(200, listOf("example.com", "example.org")))

        assertEquals(listOf("apply 300", "clear", "apply grant 300"), helper.calls)
        assertEquals(listOf("example.com", "example.org"), helper.lastDomains)
    }

    @Test
    fun `given a manual session on one set when a schedule on another set claims then the helper pauses both`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(300, listOf("work.example")))

        claims.claimSchedule(request(200, listOf("leisure.example")))

        assertEquals(listOf("leisure.example", "work.example"), helper.lastDomains)
        assertEquals("apply grant 300", helper.calls.last())
    }

    @Test
    fun `given a schedule on one set when a manual session on another set starts then the helper pauses both until the later end`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200, listOf("leisure.example")))

        claims.manual.apply(request(300, listOf("work.example")))

        assertEquals(listOf("leisure.example", "work.example"), helper.lastDomains)
        assertEquals("apply grant 300", helper.calls.last())
    }

    @Test
    fun `given both sets paused when the manual session ends then only the schedule's set stays paused`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200, listOf("leisure.example")))
        claims.manual.apply(request(300, listOf("work.example")))

        claims.manual.clear()

        assertEquals(listOf("leisure.example"), helper.lastDomains)
        assertEquals("apply grant 200", helper.calls.last())
    }

    @Test
    fun `given both sets paused when the schedule ends then only the manual session's set stays paused`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(300, listOf("work.example")))
        claims.claimSchedule(request(200, listOf("leisure.example")))

        claims.releaseSchedule()

        assertEquals(listOf("work.example"), helper.lastDomains)
    }

    @Test
    fun `given a helper that refuses while holding when the manual session ends inside the schedule then the schedule's set stays paused`() =
        runTest {
            val helper = HelperDouble(refusesWhileHolding = true)
            val claims = PauseClaims(helper)
            claims.claimSchedule(request(200, listOf("leisure.example")))
            claims.manual.apply(request(300, listOf("work.example")))

            claims.manual.clear()

            assertTrue(helper.applied)
            assertEquals(listOf("leisure.example"), helper.lastDomains)
            assertEquals(EnforcementOutcome.APPLIED, claims.scheduleStatus())
        }

    @Test
    fun `given a helper that refuses while holding when the schedule ends inside the manual session then the session's set stays paused`() =
        runTest {
            val helper = HelperDouble(refusesWhileHolding = true)
            val claims = PauseClaims(helper)
            claims.claimSchedule(request(200, listOf("leisure.example")))
            claims.manual.apply(request(300, listOf("work.example")))

            claims.releaseSchedule()

            assertTrue(helper.applied)
            assertEquals(listOf("work.example"), helper.lastDomains)
            assertEquals(EnforcementOutcome.APPLIED, claims.manual.status())
        }

    @Test
    fun `given a helper that refuses while holding when a running manual session is applied again with new items then the helper takes them`() =
        runTest {
            val helper = HelperDouble(refusesWhileHolding = true)
            val claims = PauseClaims(helper)
            claims.manual.apply(request(300, listOf("work.example")))

            val report = claims.manual.apply(request(300, listOf("added.example", "work.example")))

            assertEquals(EnforcementOutcome.APPLIED, report.outcome)
            assertEquals(listOf("added.example", "work.example"), helper.lastDomains)
        }

    @Test
    fun `given the same items in another order when the schedule is updated then the helper is not touched`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.claimSchedule(request(200, listOf("a.example", "b.example")))

        claims.updateSchedule(request(200, listOf("b.example", "a.example")))

        assertEquals(listOf("apply grant 200"), helper.calls)
    }

    @Test
    fun `given a relaunch adopted the held manual session when a schedule on another set claims then the helper pauses both`() = runTest {
        val helper = HelperDouble()
        helper.applied = true
        val claims = PauseClaims(helper)
        claims.manual.adopt(request(300, listOf("work.example")))

        claims.claimSchedule(request(200, listOf("leisure.example")))

        assertEquals(listOf("leisure.example", "work.example"), helper.lastDomains)
    }

    @Test
    fun `given a resumed manual session when a schedule on another set starts through update then the helper pauses both`() = runTest {
        val helper = HelperDouble()
        val claims = PauseClaims(helper)
        claims.manual.apply(request(300, listOf("work.example")))
        claims.manual.clear()
        claims.manual.apply(request(300, listOf("work.example")))

        claims.updateSchedule(request(200, listOf("leisure.example")))

        assertEquals(listOf("leisure.example", "work.example"), helper.lastDomains)
    }
}
