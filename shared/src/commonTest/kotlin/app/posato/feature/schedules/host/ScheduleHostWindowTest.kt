package app.posato.feature.schedules.host

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.DomainWindowPlan
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.enforcement.PauseItems
import app.posato.feature.enforcement.blockedNow
import app.posato.feature.enforcement.windowSegment
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import app.posato.feature.targets.domain.WebsiteWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val focus = ScheduleId("000000000000400080000000000000e1")
private val evening = ScheduleId("000000000000400080000000000000e2")
private val monday = ScheduleDate(2026, 9, 28)
private val workSet = checkNotNull(PauseSetId.of(testIdentifier(95)))
private val leisureSet = checkNotNull(PauseSetId.of(testIdentifier(96)))
private const val EVERY_DAY = 0b1111111
private val halfHourly = checkNotNull(WebsiteWindow.of(30, 5))
private val halfHourlyLong = checkNotNull(WebsiteWindow.of(30, 15))
private val hourly = checkNotNull(WebsiteWindow.of(60, 5))

private fun at(
    hour: Int,
    minute: Int = 0,
): Long {
    return CentralEuropeanZone.instantOf(monday, hour * 60 + minute)
}

private class WindowHelper : EnforcementPort {
    val requests = mutableListOf<EnforcementRequest>()
    var cleared = 0
    private var applied = false

    override val reapplyRequiresPrompt: Boolean = true

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        requests += request
        applied = true
        return EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
    }

    override suspend fun clear(): EnforcementOutcome {
        cleared += 1
        applied = false
        return EnforcementOutcome.CLEARED
    }

    override suspend fun status(): EnforcementOutcome {
        return if (applied) EnforcementOutcome.APPLIED else EnforcementOutcome.CLEARED
    }
}

/** A set's websites and rules, as the device holds them when the host reads them. */
private class SetContents(
    var domains: List<String>,
    var windows: Map<String, WebsiteWindow> = emptyMap(),
)

private class WindowHostFixture(
    val store: SqlScheduleStore,
    database: PosatoDatabase,
) {
    var now = at(9, 10)
    val helper = WindowHelper()
    val claims = PauseClaims(helper)
    val sets = mutableMapOf(workSet to SetContents(listOf("a.example", "b.example")), leisureSet to SetContents(listOf("a.example")))
    val host = ScheduleHost(
        store = store,
        zone = CentralEuropeanZone,
        clock = { now },
        ports = ScheduleHostPorts(
            claims = claims,
            gate = { StartGate.READY },
            hadConsent = { true },
            targets = { setId -> targetsOf(checkNotNull(setId)) },
            maintenanceClosed = { false },
            retention = SqlPartRetentionStore(database, Dispatchers.Default),
        ),
        ticks = emptyFlow(),
    )

    fun targetsOf(setId: PauseSetId): SessionTargetsState {
        val all = sets.map { (id, contents) ->
            LocalPauseSet(
                id,
                "Set",
                contents.domains.map { checkNotNull(ExactDomain.restore(it)) }.sortedBy(ExactDomain::canonicalValue),
                windows = contents.windows.mapKeys { (domain, _) -> checkNotNull(ExactDomain.restore(domain)) },
            )
        }
        val contents = checkNotNull(sets[setId])
        val policy = assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(contents.domains, null)).policy
        return SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Unavailable(), setId, PauseSets.of(all, null))
    }

    val held: EnforcementRequest
        get() = helper.requests.last()

    suspend fun snapshot(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(store.read()).value
    }
}

/**
 * Scheduled and overlapping parts with window rules and an injected clock. Failure modes: a part with no rule
 * fails to keep a shared website blocked, the intersection of two rules opens too much, a part's end loses the
 * surviving part's window, or an edited rule never reaches the helper.
 */
class ScheduleHostWindowTest {
    private suspend fun withHost(
        name: String,
        plans: List<SchedulePlan> = listOf(SchedulePlan(focus, "Focus", EVERY_DAY, 9 * 60, 10 * 60, true, workSet)),
        block: suspend (WindowHostFixture) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlScheduleStore(database, Dispatchers.Default)
            plans.forEach { plan -> store.save(plan, null) }
            block(WindowHostFixture(store, database))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a ruled website in a scheduled pause when applied then only it opens inside its window`() = runTest {
        withHost("schedule-window-only.db") { fixture ->
            fixture.sets.getValue(workSet).windows = mapOf("a.example" to halfHourly)

            fixture.host.evaluate()

            assertEquals(listOf("a.example", "b.example"), fixture.held.domains)
            assertEquals(listOf(DomainWindowPlan("a.example", listOf(windowSegment(at(10), halfHourly)))), fixture.held.windows)
            assertEquals(listOf("b.example"), fixture.held.blockedNow(at(9, 30), CentralEuropeanZone))
            assertEquals(listOf("a.example", "b.example"), fixture.held.blockedNow(at(9, 29), CentralEuropeanZone))
        }
    }

    @Test
    fun `given an unruled part ending before a ruled one when both run then the website opens after that end with no new request`() = runTest {
        val plans = listOf(
            SchedulePlan(focus, "Focus", EVERY_DAY, 9 * 60, 10 * 60, true, workSet),
            SchedulePlan(evening, "Evening", EVERY_DAY, 9 * 60, 9 * 60 + 20, true, leisureSet),
        )
        withHost("schedule-window-overlap.db", plans) { fixture ->
            fixture.sets.getValue(workSet).windows = mapOf("a.example" to halfHourly)

            fixture.host.evaluate()

            val plan = fixture.held.windows.single()
            assertEquals("a.example", plan.domain)
            assertEquals(listOf(windowSegment(at(9, 20), null), windowSegment(at(10), halfHourly)), plan.segments)
            assertEquals(listOf("a.example", "b.example"), fixture.held.blockedNow(at(9, 0), CentralEuropeanZone), "the unruled part blocks")
            assertEquals(listOf("b.example"), fixture.held.blockedNow(at(9, 30), CentralEuropeanZone), "opens from the same request")
        }
    }

    @Test
    fun `given two parts with different rules when both run then the website opens only on their intersection`() = runTest {
        val plans = listOf(
            SchedulePlan(focus, "Focus", EVERY_DAY, 9 * 60, 10 * 60, true, workSet),
            SchedulePlan(evening, "Evening", EVERY_DAY, 9 * 60, 9 * 60 + 40, true, leisureSet),
        )
        withHost("schedule-window-intersection.db", plans) { fixture ->
            fixture.sets.getValue(workSet).windows = mapOf("a.example" to halfHourlyLong)
            fixture.sets.getValue(leisureSet).windows = mapOf("a.example" to hourly)

            fixture.host.evaluate()

            assertEquals(listOf(windowSegment(at(9, 40), hourly), windowSegment(at(10), halfHourlyLong)), fixture.held.windows.single().segments)
        }
    }

    @Test
    fun `given a running pause when a rule is added edited and removed then each change re-applies and removal closes at once`() = runTest {
        withHost("schedule-window-edits.db") { fixture ->
            fixture.host.evaluate()
            assertEquals(emptyList(), fixture.held.windows)

            fixture.sets.getValue(workSet).windows = mapOf("a.example" to halfHourly)
            fixture.host.evaluate()
            assertEquals(listOf(windowSegment(at(10), halfHourly)), fixture.held.windows.single().segments)

            fixture.sets.getValue(workSet).windows = mapOf("a.example" to hourly)
            fixture.host.evaluate()
            assertEquals(listOf(windowSegment(at(10), hourly)), fixture.held.windows.single().segments)

            fixture.now = at(9, 31)
            fixture.sets.getValue(workSet).windows = emptyMap()
            fixture.host.evaluate()
            assertEquals(emptyList(), fixture.held.windows)
            assertEquals(listOf("a.example", "b.example"), fixture.held.blockedNow(at(9, 31), CentralEuropeanZone))
            assertEquals(4, fixture.helper.requests.size)
        }
    }

    @Test
    fun `given an open window when the limits are full then nothing is freed and the held items are unchanged`() = runTest {
        withHost("schedule-window-limits.db") { fixture ->
            fixture.sets.getValue(workSet).windows = mapOf("a.example" to halfHourly)
            val withoutRule = PauseItems(setOf("a.example", "b.example"))
            fixture.now = at(9, 30)

            fixture.host.evaluate()

            assertEquals(withoutRule.domains, fixture.claims.scheduleItems().domains, "the limits count held websites, open or not")
        }
    }

    @Test
    fun `given a ruled pause on a break when the break ends then the windows apply again`() = runTest {
        withHost("schedule-window-break.db") { fixture ->
            fixture.sets.getValue(workSet).windows = mapOf("a.example" to halfHourly)
            fixture.host.evaluate()
            fixture.host.breaks.start(5)
            fixture.host.evaluate()
            assertEquals(1, fixture.helper.cleared)

            fixture.now = at(9, 15)
            fixture.host.evaluate()

            assertEquals(listOf(DomainWindowPlan("a.example", listOf(windowSegment(at(10), halfHourly)))), fixture.held.windows)
        }
    }

    @Test
    fun `given a ruled set when the iPhone table is built then the set lists its rules`() = runTest {
        withHost("schedule-window-table.db") { fixture ->
            fixture.sets.getValue(workSet).windows = mapOf("a.example" to halfHourly)

            val table = ScheduleMonitorTables.build(
                ScheduleMonitorInput(fixture.snapshot(), emptyList(), at(8)) { setId -> fixture.targetsOf(setId ?: workSet) },
                CentralEuropeanZone,
            )

            assertEquals(listOf(MonitorWindow("a.example", 30, 5)), table.sets.single { it.id == workSet.hexId() }.windows)
        }
    }
}
