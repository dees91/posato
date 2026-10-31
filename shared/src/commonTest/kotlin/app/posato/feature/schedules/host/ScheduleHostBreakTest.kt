package app.posato.feature.schedules.host

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.enforcement.PauseItems
import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.schedules.data.OccurrenceFact
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleBreak
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val focus = ScheduleId("000000000000400080000000000000d1")
private val evening = ScheduleId("000000000000400080000000000000d2")
private val late = ScheduleId("000000000000400080000000000000d3")
private val monday = ScheduleDate(2026, 9, 28)
private val focusKey = OccurrenceKey(focus, monday)
private val workSet = checkNotNull(PauseSetId.of(testIdentifier(90)))
private val leisureSet = checkNotNull(PauseSetId.of(testIdentifier(91)))
private val lateSet = checkNotNull(PauseSetId.of(testIdentifier(92)))
private const val EVERY_DAY = 0b1111111

private fun at(
    hour: Int,
    minute: Int = 0,
    date: ScheduleDate = monday,
): Long {
    return CentralEuropeanZone.instantOf(date, hour * 60 + minute)
}

/** Records each request with the clock at the call, so a test can tell when restrictions returned. */
private class BreakHelper(
    private val clock: () -> Long,
) : EnforcementPort {
    val calls = mutableListOf<String>()
    val appliedAt = mutableListOf<Long>()
    private var applied = false

    override val reapplyRequiresPrompt: Boolean = true

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        calls += "apply ${request.domains.sorted().joinToString(",")}"
        appliedAt += clock()
        applied = true
        return EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
    }

    override suspend fun clear(): EnforcementOutcome {
        calls += "clear"
        applied = false
        return EnforcementOutcome.CLEARED
    }

    override suspend fun status(): EnforcementOutcome {
        return if (applied) EnforcementOutcome.APPLIED else EnforcementOutcome.CLEARED
    }
}

private class BreakHostFixture(
    val store: SqlScheduleStore,
    database: PosatoDatabase,
    limits: PauseLimits = PauseLimits.MAC,
) {
    var now = at(9, 10)
    var clock: () -> Long = { now }
    val helper = BreakHelper { clock() }
    val retention = SqlPartRetentionStore(database, Dispatchers.Default)
    val claims = PauseClaims(helper)
    val sets = mapOf(
        workSet to listOf("a.example", "b.example"),
        leisureSet to listOf("c.example"),
        lateSet to listOf("d.example"),
    )
    val host = ScheduleHost(
        store = store,
        zone = CentralEuropeanZone,
        clock = { clock() },
        ports = ScheduleHostPorts(
            claims = claims,
            gate = { StartGate.READY },
            hadConsent = { true },
            targets = { setId ->
                val domains = sets[setId] ?: listOf("a.example", "b.example")
                val policy = assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(domains, null)).policy
                SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Unavailable())
            },
            maintenanceClosed = { false },
            limits = limits,
            retention = retention,
        ),
        ticks = emptyFlow(),
    )

    suspend fun snapshot(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(store.read()).value
    }

    suspend fun evaluateAt(
        hour: Int,
        minute: Int,
    ) {
        now = at(hour, minute)
        host.evaluate()
    }
}

/**
 * Host decisions a device run cannot force: exact instants, clock rollback and skew, limits, and the order of
 * overlapping occurrences. Each test names the failure it guards against.
 */
class ScheduleHostBreakTest {
    private suspend fun withBreakHost(
        name: String,
        limits: PauseLimits = PauseLimits.MAC,
        plans: List<SchedulePlan> = listOf(SchedulePlan(focus, "Focus", EVERY_DAY, 9 * 60, 10 * 60, true, workSet)),
        block: suspend (BreakHostFixture) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlScheduleStore(database, Dispatchers.Default)
            plans.forEach { plan -> store.save(plan, null) }
            block(BreakHostFixture(store, database, limits))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a running pause when a break starts then enforcement is released while the occurrence keeps its lifetime`() = runTest {
        // Failure mode: treating a break as an end would drop the pin, the end notice, and the retention record.
        withBreakHost("break-suspend.db") { fixture ->
            fixture.host.evaluate()
            val pinsBefore = fixture.snapshot().pins

            assertEquals(BreakStart.STARTED, fixture.host.breaks.start(5))
            fixture.host.evaluate()

            assertEquals(listOf("apply a.example,b.example", "clear"), fixture.helper.calls)
            val pause = assertIs<ScheduledPause>(fixture.host.pause.value)
            assertTrue(pause.restricts)
            assertEquals(setOf(focusKey), pause.keys)
            assertEquals(at(10), pause.endEpochMillis)
            assertEquals(setOf(focusKey), pause.onBreak)
            assertEquals(at(9, 15), pause.breakEndEpochMillis)
            assertTrue(pause.breakUsed)
            assertEquals(pinsBefore, fixture.snapshot().pins)
            assertTrue(fixture.snapshot().facts.expired.isEmpty())
            assertEquals(mapOf(focusKey to ScheduleBreak(at(9, 10), 5)), fixture.snapshot().facts.breaks)
        }
    }

    @Test
    fun `given a running break when its end comes then restrictions return at that instant and no second break is offered`() = runTest {
        withBreakHost("break-return.db") { fixture ->
            fixture.host.evaluate()
            fixture.host.breaks.start(5)
            fixture.host.evaluate()

            fixture.now = at(9, 15) - 1
            fixture.host.evaluate()
            assertEquals(listOf("apply a.example,b.example", "clear"), fixture.helper.calls)

            fixture.evaluateAt(9, 15)

            assertEquals(listOf("apply a.example,b.example", "clear", "apply a.example,b.example"), fixture.helper.calls)
            val pause = assertIs<ScheduledPause>(fixture.host.pause.value)
            assertEquals(emptySet(), pause.onBreak)
            assertNull(pause.breakEndEpochMillis)
            assertTrue(pause.breakUsed)
            assertEquals(setOf(focusKey), fixture.snapshot().facts.breaksOver)
            assertEquals(BreakStart.UNAVAILABLE, fixture.host.breaks.start(5))
        }
    }

    @Test
    fun `given a running host with no minute ticks when a break ends then it evaluates at the exact break end`() = runTest {
        // Failure mode: waiting for the next minute tick would keep a device released up to a minute too long.
        withBreakHost("break-timer.db") { fixture ->
            fixture.host.evaluate()
            fixture.host.breaks.start(5)
            val base = testScheduler.currentTime
            fixture.clock = { at(9, 10) + (testScheduler.currentTime - base) }
            val running = launch { fixture.host.run() }

            advanceUntil(this) { fixture.helper.calls.size >= 3 }
            running.cancel()

            assertEquals(listOf("apply a.example,b.example", "clear", "apply a.example,b.example"), fixture.helper.calls)
            // The host reads the clock when the timer fires; the database threads may let virtual time move a step on.
            assertTrue(fixture.helper.appliedAt.last() in at(9, 15) until at(9, 15) + TIMER_TOLERANCE_MILLIS, "${fixture.helper.appliedAt}")
        }
    }

    @Test
    fun `given a running break when End break is pressed then restrictions return at once and the break stays used`() = runTest {
        withBreakHost("break-end.db") { fixture ->
            fixture.host.evaluate()
            assertFalse(fixture.host.breaks.end())
            fixture.host.breaks.start(10)
            fixture.host.evaluate()

            fixture.now = at(9, 12)
            assertTrue(fixture.host.breaks.end())
            fixture.host.evaluate()

            assertEquals(listOf("apply a.example,b.example", "clear", "apply a.example,b.example"), fixture.helper.calls)
            assertEquals(setOf(focusKey), fixture.snapshot().facts.breakEnds)
            assertEquals(emptySet(), fixture.host.pause.value?.onBreak)
            assertEquals(BreakStart.UNAVAILABLE, fixture.host.breaks.start(5))
        }
    }

    @Test
    fun `given a running break when End early is pressed then nothing is applied at the break end or later`() = runTest {
        withBreakHost("break-end-early.db") { fixture ->
            fixture.host.evaluate()
            fixture.host.breaks.start(5)
            fixture.host.evaluate()

            fixture.now = at(9, 12)
            assertTrue(fixture.host.endEarly())
            fixture.host.evaluate()
            fixture.evaluateAt(9, 15)
            fixture.evaluateAt(9, 20)

            assertNull(fixture.host.pause.value)
            assertEquals(listOf("apply a.example,b.example", "clear"), fixture.helper.calls.distinct())
            assertFalse(fixture.helper.calls.drop(2).any { it.startsWith("apply") })
        }
    }

    @Test
    fun `given a break that outlasts its occurrence when the occurrence ends then it ends there and nothing returns later`() = runTest {
        withBreakHost("break-outlasts.db") { fixture ->
            fixture.host.evaluate()
            fixture.now = at(9, 57)
            fixture.host.breaks.start(5)
            fixture.host.evaluate()

            fixture.evaluateAt(10, 0)
            fixture.evaluateAt(10, 2)

            assertNull(fixture.host.pause.value)
            assertEquals(mapOf(focusKey to at(10)), fixture.snapshot().facts.expired)
            assertEquals(1, fixture.helper.calls.count { it.startsWith("apply") })
        }
    }

    @Test
    fun `given overlapping occurrences when a break is taken then all break together and one starting during it restricts at once`() = runTest {
        val plans = listOf(
            SchedulePlan(focus, "Focus", EVERY_DAY, 9 * 60, 10 * 60, true, workSet),
            SchedulePlan(evening, "Evening", EVERY_DAY, 9 * 60 + 5, 10 * 60 + 30, true, leisureSet),
            SchedulePlan(late, "Late", EVERY_DAY, 9 * 60 + 12, 10 * 60 + 30, true, lateSet),
        )
        withBreakHost("break-overlap.db", plans = plans) { fixture ->
            fixture.host.evaluate()
            assertEquals(BreakStart.STARTED, fixture.host.breaks.start(10))
            fixture.host.evaluate()
            val eveningKey = OccurrenceKey(evening, monday)
            val taken = ScheduleBreak(at(9, 10), 10)
            assertEquals(mapOf(focusKey to taken, eveningKey to taken), fixture.snapshot().facts.breaks)

            fixture.evaluateAt(9, 12)
            assertEquals("apply d.example", fixture.helper.calls.last())
            assertEquals(BreakStart.UNAVAILABLE, fixture.host.breaks.start(5))

            fixture.evaluateAt(9, 20)
            assertEquals("apply a.example,b.example,c.example,d.example", fixture.helper.calls.last())
            assertTrue(assertIs<ScheduledPause>(fixture.host.pause.value).breakUsed)
            assertEquals(BreakStart.UNAVAILABLE, fixture.host.breaks.start(5))
        }
    }

    @Test
    fun `given lengths that are not offered or no running pause when a break is asked for then nothing is recorded`() = runTest {
        withBreakHost("break-unavailable.db") { fixture ->
            fixture.now = at(8)
            fixture.host.evaluate()
            assertEquals(BreakStart.UNAVAILABLE, fixture.host.breaks.start(5))
            fixture.evaluateAt(9, 10)
            listOf(0, 4, 7, 20).forEach { minutes -> assertEquals(BreakStart.UNAVAILABLE, fixture.host.breaks.start(minutes)) }

            assertTrue(fixture.snapshot().facts.breaks.isEmpty())
        }
    }

    @Test
    fun `given limits nearly full when an occurrence starts during a break then the broken part's items still count`() = runTest {
        // Failure mode: admitting items into the released room would leave the returning part over the limit.
        val plans = listOf(
            SchedulePlan(focus, "Focus", EVERY_DAY, 9 * 60, 10 * 60, true, workSet),
            SchedulePlan(evening, "Evening", EVERY_DAY, 9 * 60 + 12, 10 * 60 + 30, true, leisureSet),
        )
        withBreakHost("break-limits.db", limits = PauseLimits(2, 64) { it.size }, plans = plans) { fixture ->
            fixture.host.evaluate()
            fixture.host.breaks.start(5)
            fixture.host.evaluate()
            assertEquals(PauseItems(setOf("a.example", "b.example")), fixture.host.reservedDuringBreak)
            // A manual session counts what the occurrences hold, also on a break, so it cannot take the room.
            assertEquals(PauseItems(setOf("a.example", "b.example")), fixture.claims.scheduleItems())

            fixture.evaluateAt(9, 12)
            assertEquals("clear", fixture.helper.calls.last())
            assertEquals(1, fixture.host.pause.value?.notPausedYet)

            fixture.evaluateAt(9, 15)
            assertEquals("apply a.example,b.example", fixture.helper.calls.last())
            assertEquals(PauseItems(), fixture.host.reservedDuringBreak)
            assertEquals(PauseItems(setOf("a.example", "b.example")), fixture.claims.scheduleItems())
        }
    }

    @Test
    fun `given a break whose end passed before this device first saw it then it never releases`() = runTest {
        withBreakHost("break-expired.db") { fixture ->
            fixture.store.recordFact(setOf(focusKey), OccurrenceFact.Break(ScheduleBreak(at(9, 0), 5)), monday, null)

            fixture.evaluateAt(9, 10)

            assertEquals(listOf("apply a.example,b.example"), fixture.helper.calls)
            assertEquals(setOf(focusKey), fixture.snapshot().facts.breaksOver)
        }
    }

    @Test
    fun `given a break this device saw end when its clock is set back into the window then it does not release again`() = runTest {
        withBreakHost("break-rollback.db") { fixture ->
            fixture.host.evaluate()
            fixture.host.breaks.start(5)
            fixture.host.evaluate()
            fixture.evaluateAt(9, 15)

            fixture.evaluateAt(9, 12)

            assertEquals(listOf("apply a.example,b.example", "clear", "apply a.example,b.example"), fixture.helper.calls)
            assertEquals(emptySet(), fixture.host.pause.value?.onBreak)
        }
    }

    @Test
    fun `given a break starting more than two minutes after this device's clock then it never releases here`() = runTest {
        withBreakHost("break-far-future.db") { fixture ->
            fixture.store.recordFact(setOf(focusKey), OccurrenceFact.Break(ScheduleBreak(at(9, 13), 5)), monday, null)

            fixture.evaluateAt(9, 10)
            fixture.evaluateAt(9, 13)
            fixture.evaluateAt(9, 15)

            assertEquals(listOf("apply a.example,b.example"), fixture.helper.calls)
            assertEquals(setOf(focusKey), fixture.snapshot().facts.breaksOver)
        }
    }

    @Test
    fun `given a break starting two minutes after this device's clock then it releases once the clock reaches it`() = runTest {
        withBreakHost("break-near-future.db") { fixture ->
            fixture.store.recordFact(setOf(focusKey), OccurrenceFact.Break(ScheduleBreak(at(9, 12), 5)), monday, null)

            fixture.evaluateAt(9, 10)
            assertTrue(fixture.snapshot().facts.breaksOver.isEmpty())
            fixture.evaluateAt(9, 12)
            fixture.evaluateAt(9, 17)

            assertEquals(listOf("apply a.example,b.example", "clear", "apply a.example,b.example"), fixture.helper.calls)
        }
    }

    @Test
    fun `given a break taken in one occurrence when the next occurrence of the schedule runs then it is not released`() = runTest {
        // Failure mode: a leftover keyed by schedule only would release a later day's occurrence.
        withBreakHost("break-no-leftover.db") { fixture ->
            fixture.host.evaluate()
            fixture.host.breaks.start(5)
            fixture.host.evaluate()

            fixture.now = at(9, 12, monday.plusDays(1))
            fixture.host.evaluate()

            assertEquals("apply a.example,b.example", fixture.helper.calls.last())
            assertEquals(emptySet(), fixture.host.pause.value?.onBreak)
            assertFalse(assertIs<ScheduledPause>(fixture.host.pause.value).breakUsed)
        }
    }

    @Test
    fun `given a skipped occurrence with a break fact then the fact releases nothing`() = runTest {
        withBreakHost("break-skipped.db") { fixture ->
            fixture.store.recordFact(setOf(focusKey), OccurrenceFact.Break(ScheduleBreak(at(9, 10), 15)), monday, null)
            fixture.store.recordFact(setOf(focusKey), OccurrenceFact.Skip, monday, null)

            fixture.evaluateAt(9, 11)

            assertNull(fixture.host.pause.value)
            assertTrue(fixture.helper.calls.none { it.startsWith("apply") })
        }
    }
}

/** Advances virtual time a minute at a time, letting the database threads finish, until [done] or ten minutes passed. */
private suspend fun advanceUntil(
    scope: TestScope,
    done: () -> Boolean,
) {
    repeat(TIMER_STEPS) {
        if (done()) return
        withContext(Dispatchers.Default) { delay(REAL_WAIT_MILLIS) }
        scope.testScheduler.advanceTimeBy(VIRTUAL_STEP_MILLIS)
        scope.testScheduler.runCurrent()
    }
}

private const val TIMER_STEPS: Int = 200
private const val REAL_WAIT_MILLIS: Long = 50
private const val VIRTUAL_STEP_MILLIS: Long = 3_000
private const val TIMER_TOLERANCE_MILLIS: Long = 15_000
