package app.posato.feature.schedules.host

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.session.data.PartRetentionStore
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val focusId = ScheduleId("000000000000400080000000000000a1")
private val monday = ScheduleDate(2026, 9, 28)
private val mondayKey = OccurrenceKey(focusId, monday)
private val eveningId = ScheduleId("000000000000400080000000000000b2")
private val workSet = checkNotNull(PauseSetId.of(testIdentifier(80)))
private val leisureSet = checkNotNull(PauseSetId.of(testIdentifier(81)))

private fun at(
    hour: Int,
    minute: Int = 0,
): Long {
    return CentralEuropeanZone.instantOf(monday, hour * 60 + minute)
}

private class Helper : EnforcementPort {
    val calls = mutableListOf<String>()
    var outcome = EnforcementOutcome.APPLIED
    var applied = false
    var throws = false
    var lastDomains: List<String> = emptyList()

    override val reapplyRequiresPrompt: Boolean = true

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        calls += if (request.grantOnly) "grant apply until ${request.sessionEndEpochMillis}" else "prompting apply"
        check(!throws) { "helper could not start" }
        lastDomains = request.domains
        applied = outcome == EnforcementOutcome.APPLIED
        return EnforcementApplyReport(outcome, false, false)
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

private class HostFixture(
    val store: SqlScheduleStore,
    val helper: Helper = Helper(),
    ticks: kotlinx.coroutines.flow.Flow<Unit> = emptyFlow(),
    val retention: PartRetentionStore? = null,
) {
    var setDomains: Map<PauseSetId, List<String>> = emptyMap()
    var now = at(8)
    var gate = StartGate.READY
    var consent = true
    var gateChecks = 0
    var maintenanceClosed = false
    var domains = listOf("example.com")
    val claims = PauseClaims(helper)
    val host = ScheduleHost(
        store = store,
        zone = CentralEuropeanZone,
        clock = { now },
        ports = ScheduleHostPorts(
            claims = claims,
            gate = {
                gateChecks += 1
                gate
            },
            hadConsent = { consent },
            targets = { setId ->
                val chosen = setId?.let(setDomains::get) ?: domains
                val policy = assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(chosen, null)).policy
                SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Unavailable())
            },
            maintenanceClosed = { maintenanceClosed },
            retention = retention,
        ),
        ticks = ticks,
    )

    suspend fun snapshot(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(store.read()).value
    }
}

private fun targets(): SessionTargetsState {
    val policy = assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(listOf("example.com"), null)).policy
    return SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Unavailable())
}

class ScheduleHostTest {
    private suspend fun withHost(
        name: String,
        block: suspend (HostFixture) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlScheduleStore(database, Dispatchers.Default)
            store.save(SchedulePlan(focusId, "Focus", 1, 9 * 60, 10 * 60, true), null)
            block(HostFixture(store, retention = SqlPartRetentionStore(database, Dispatchers.Default)))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a due occurrence on a ready Mac then it applies through the grant once and pins it and and releases at its end`() = runTest {
        withHost("host-start.db") { fixture ->
            fixture.host.evaluate()
            assertNull(fixture.host.pause.value)

            fixture.now = at(9, 1)
            fixture.host.evaluate()
            fixture.now = at(9, 2)
            fixture.host.evaluate()

            val pause = fixture.host.pause.value
            assertEquals(ScheduledPauseState.APPLIED, pause?.state)
            assertEquals(at(10), pause?.endEpochMillis)
            assertEquals(setOf(mondayKey), pause?.unannounced)
            assertEquals(listOf("grant apply until ${at(10)}"), fixture.helper.calls)
            assertEquals(at(9), fixture.snapshot().pins.single().startEpochMillis)

            fixture.now = at(10)
            fixture.host.evaluate()

            assertNull(fixture.host.pause.value)
            assertEquals(listOf("grant apply until ${at(10)}", "clear"), fixture.helper.calls)
            assertEquals(mapOf(mondayKey to at(10)), fixture.snapshot().facts.expired)
            assertTrue(fixture.snapshot().pins.isEmpty())
        }
    }

    @Test
    fun `given a Mac without setup then the pause says so and never applies and and posts a setup notice only after consent existed`() = runTest {
        withHost("host-setup.db") { fixture ->
            fixture.gate = StartGate.SETUP_REQUIRED
            fixture.consent = false
            fixture.now = at(9, 5)

            fixture.host.evaluate()

            assertEquals(ScheduledPauseState.SETUP_REQUIRED, fixture.host.pause.value?.state)
            assertEquals(emptySet(), fixture.host.pause.value?.setupUnannounced)
            assertTrue(fixture.helper.calls.isEmpty())

            fixture.consent = true
            fixture.host.evaluate()
            assertEquals(setOf(mondayKey), fixture.host.pause.value?.setupUnannounced)
            fixture.host.markAnnounced(setOf(mondayKey), ScheduleNotices.SETUP_REQUIRED)
            fixture.host.evaluate()
            assertEquals(emptySet(), fixture.host.pause.value?.setupUnannounced)
        }
    }

    @Test
    fun `given another account or an update in progress then the pause waits without asking the helper to apply`() = runTest {
        withHost("host-wait.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.gate = StartGate.OTHER_ACCOUNT
            fixture.host.evaluate()
            assertEquals(ScheduledPauseState.WAITING, fixture.host.pause.value?.state)

            fixture.gate = StartGate.READY
            fixture.maintenanceClosed = true
            fixture.host.evaluate()
            assertEquals(ScheduledPauseState.WAITING, fixture.host.pause.value?.state)
            assertTrue(fixture.helper.calls.isEmpty())
        }
    }

    @Test
    fun `given a grant the daemon refuses and a recheck finds it off then setup is required and no password apply ever runs`() = runTest {
        withHost("host-refused.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.helper.outcome = EnforcementOutcome.AUTHORIZATION_REQUIRED
            val answers = ArrayDeque(listOf(StartGate.READY, StartGate.SETUP_REQUIRED))
            val host = ScheduleHost(
                fixture.store,
                CentralEuropeanZone,
                { fixture.now },
                ScheduleHostPorts(fixture.claims, { answers.removeFirst() }, { true }, { targets() }, { false }),
                emptyFlow(),
            )

            host.evaluate()

            assertEquals(ScheduledPauseState.SETUP_REQUIRED, host.pause.value?.state)
            assertTrue(fixture.helper.calls.none { it == "prompting apply" })
        }
    }

    @Test
    fun `given a helper that lost the restrictions then the next minute applies them again`() = runTest {
        withHost("host-lost.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.host.evaluate()
            fixture.helper.applied = false

            fixture.now = at(9, 6)
            fixture.host.evaluate()

            assertEquals(2, fixture.helper.calls.count { it.startsWith("grant apply") })
            assertEquals(ScheduledPauseState.APPLIED, fixture.host.pause.value?.state)
        }
    }

    @Test
    fun `given a running pause when ended early then it records the end and releases and and stays ended after a relaunch`() = runTest {
        withHost("host-end.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.host.evaluate()

            assertTrue(fixture.host.endEarly())
            fixture.host.evaluate()

            assertNull(fixture.host.pause.value)
            assertEquals(setOf(mondayKey), fixture.snapshot().facts.ended)
            assertEquals("clear", fixture.helper.calls.last())

            val relaunched = HostFixture(fixture.store).apply { now = at(9, 7) }
            relaunched.host.evaluate()
            assertNull(relaunched.host.pause.value)
        }
    }

    @Test
    fun `given a relaunch inside the interval then it catches up to the original end without a second pin`() = runTest {
        withHost("host-relaunch.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.host.evaluate()

            val relaunched = HostFixture(fixture.store).apply { now = at(9, 30) }
            relaunched.host.evaluate()

            assertEquals(ScheduledPauseState.APPLIED, relaunched.host.pause.value?.state)
            assertEquals(at(10), relaunched.host.pause.value?.endEpochMillis)
            assertEquals(at(9), relaunched.snapshot().pins.single().startEpochMillis)
            assertEquals(listOf("grant apply until ${at(10)}"), relaunched.helper.calls)
        }
    }

    @Test
    fun `given a grant refusal while the grant read has no answer then it retries instead of asking for setup`() = runTest {
        withHost("host-transient.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.helper.outcome = EnforcementOutcome.AUTHORIZATION_REQUIRED
            fixture.gate = StartGate.READY
            val answers = ArrayDeque(listOf(StartGate.READY, StartGate.TRANSIENT))
            val host = ScheduleHost(
                fixture.store,
                CentralEuropeanZone,
                { fixture.now },
                ScheduleHostPorts(fixture.claims, { answers.removeFirst() }, { true }, { targets() }, { false }),
                emptyFlow(),
            )

            host.evaluate()

            assertEquals(ScheduledPauseState.RETRYING, host.pause.value?.state)
            assertEquals(emptySet(), host.pause.value?.setupUnannounced)
        }
    }

    @Test
    fun `given a helper that throws then the host keeps evaluating on later triggers`() = runTest {
        withHost("host-throws.db") { fixture ->
            val relaunched = HostFixture(fixture.store, ticks = kotlinx.coroutines.flow.flowOf(Unit, Unit)).apply {
                now = at(9, 5)
                helper.throws = true
            }
            val running = launch { relaunched.host.run() }
            repeat(100) {
                if (relaunched.helper.calls.size < 2) {
                    withContext(Dispatchers.Default) { delay(20) }
                }
            }
            running.cancel()

            assertTrue(relaunched.helper.calls.size >= 2)
        }
    }

    @Test
    fun `given a start announced before its bit reached the store then a later evaluation does not announce it again`() = runTest {
        withHost("host-announce.db") { fixture ->
            val lagging = object : app.posato.feature.schedules.data.LocalScheduleStore by fixture.store {
                override suspend fun recordHost(update: app.posato.feature.schedules.data.ScheduleHostUpdate): ScheduleResult<Unit> {
                    return fixture.store.recordHost(update.copy(notices = emptyMap()))
                }
            }
            val host = ScheduleHost(
                lagging,
                CentralEuropeanZone,
                { at(9, 5) },
                ScheduleHostPorts(fixture.claims, { StartGate.READY }, { true }, { targets() }, { false }),
                emptyFlow(),
            )
            host.evaluate()
            assertEquals(setOf(mondayKey), host.pause.value?.unannounced)

            host.markAnnounced(setOf(mondayKey), ScheduleNotices.STARTED)
            host.evaluate()

            assertEquals(emptySet(), host.pause.value?.unannounced)
        }
    }

    @Test
    fun `given an announced run when an edit stops it and a later interval starts it again then the second run is announced`() = runTest {
        withHost("host-second-run.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.host.evaluate()
            fixture.host.markAnnounced(setOf(mondayKey), ScheduleNotices.STARTED)
            fixture.host.evaluate()
            assertEquals(emptySet(), fixture.host.pause.value?.unannounced)

            fixture.store.save(SchedulePlan(focusId, "Focus", 1, 9 * 60 + 30, 10 * 60, true), null)
            fixture.now = at(9, 10)
            fixture.host.evaluate()
            assertNull(fixture.host.pause.value)

            fixture.now = at(9, 31)
            fixture.host.evaluate()
            assertEquals(setOf(mondayKey), fixture.host.pause.value?.unannounced)
        }
    }

    @Test
    fun `given a running pause when paused items change or the end moves then the helper follows without a lapse`() = runTest {
        withHost("host-current.db") { fixture ->
            fixture.now = at(9, 5)
            fixture.host.evaluate()

            fixture.domains = listOf("example.com", "example.org")
            fixture.host.evaluate()
            assertEquals(listOf("example.com", "example.org"), fixture.helper.lastDomains)

            fixture.store.save(SchedulePlan(focusId, "Focus", 1, 9 * 60, 11 * 60, true), null)
            fixture.host.evaluate()

            assertEquals(
                listOf("grant apply until ${at(10)}", "clear", "grant apply until ${at(10)}", "clear", "grant apply until ${at(11)}"),
                fixture.helper.calls,
            )
            assertEquals(ScheduledPauseState.APPLIED, fixture.host.pause.value?.state)
        }
    }

    @Test
    fun `given a host whose starts another process announces then it never announces and records theirs`() = runTest {
        withHost("host-elsewhere.db") { fixture ->
            val published = mutableListOf<Int>()
            val host = ScheduleHost(
                fixture.store,
                CentralEuropeanZone,
                { at(9, 5) },
                ScheduleHostPorts(
                    fixture.claims,
                    { StartGate.READY },
                    { false },
                    { targets() },
                    { false },
                    announcesStarts = false,
                    announcedElsewhere = { setOf(mondayKey) },
                    publish = { input -> published += input.running.size },
                ),
                emptyFlow(),
            )

            host.evaluate()

            assertEquals(emptySet(), host.pause.value?.unannounced)
            assertEquals(listOf(1), published)
            assertEquals(ScheduleNotices.STARTED, fixture.snapshot().pins.single().notices)
        }
    }

    @Test
    fun `given two schedules on different sets running together then both sets are paused`() = runTest {
        withHost("host-two-sets.db") { fixture ->
            fixture.store.save(SchedulePlan(focusId, "Focus", 1, 9 * 60, 10 * 60, true, workSet), null)
            fixture.store.save(SchedulePlan(eveningId, "Evening", 1, 9 * 60, 11 * 60, true, leisureSet), null)
            fixture.setDomains = mapOf(workSet to listOf("work.example"), leisureSet to listOf("leisure.example"))

            fixture.now = at(9, 1)
            fixture.host.evaluate()

            assertEquals(setOf("leisure.example", "work.example"), fixture.helper.lastDomains.toSet())
        }
    }

    @Test
    fun `given a website removed from a running occurrence's set then it stays paused until the occurrence ends`() = runTest {
        withHost("host-retained.db") { fixture ->
            fixture.store.save(SchedulePlan(focusId, "Focus", 1, 9 * 60, 10 * 60, true, workSet), null)
            fixture.setDomains = mapOf(workSet to listOf("kept.example", "work.example"))
            fixture.now = at(9, 1)
            fixture.host.evaluate()

            fixture.setDomains = mapOf(workSet to listOf("work.example"))
            fixture.now = at(9, 2)
            fixture.host.evaluate()

            assertEquals(setOf("kept.example", "work.example"), fixture.helper.lastDomains.toSet())
        }
    }
}
