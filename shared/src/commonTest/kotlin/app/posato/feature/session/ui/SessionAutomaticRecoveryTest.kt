package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.schedules.host.ScheduleStartGate
import app.posato.feature.schedules.host.StartGate
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * MACOS-029: with the standing grant on and automatic starts agreed, the Mac applies a running pause
 * again by itself instead of asking the person. A run in Tart shows the outcome; these cases pin what
 * no run can time: how many grant-only attempts one loss gets, that a pause ended or replaced during
 * the wait gets none, that the notice the person would have seen comes back unchanged when every
 * attempt fails, and that the person's own failed start never starts one.
 */
class SessionAutomaticRecoveryTest {
    @Test
    fun `given a received pause and a ready gate then it applies with the grant and shows no notice`() = runTest {
        val port = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val gate = CountingGate(StartGate.READY)
        val owner = receivedPause(port, gate)
        try {
            assertIs<EnforcementState.Active>(owner.view.value.state)
            assertFalse(owner.view.value.recovering)
            assertEquals(true, port.lastRequest?.grantOnly)
            assertEquals(CURRENT.reconciliationId(), port.lastRequest?.sessionId)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given setup required then the resume notice appears as before without an automatic apply`() = runTest {
        val port = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val owner = receivedPause(port, CountingGate(StartGate.SETUP_REQUIRED))
        try {
            val action = assertIs<EnforcementState.ActionRequired>(owner.view.value.state)
            assertEquals(EnforcementActionKind.RESUME_REQUIRED, action.kind)
            assertTrue(action.repeatsSystemPrompt)
            assertFalse(owner.view.value.recovering)
            assertEquals(1, port.calls.count { it == "apply" })
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given every attempt fails then three grant applies run and the original notice returns`() = runTest {
        val port = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val gate = CountingGate(StartGate.READY)
        val owner = receivedPause(port, gate, failApplies = true)
        try {
            assertTrue(owner.view.value.recovering)
            advanceTimeBy(RECOVERY_WINDOW_MILLIS)
            runCurrent()
            val action = assertIs<EnforcementState.ActionRequired>(owner.view.value.state)
            assertEquals(EnforcementActionKind.RESUME_REQUIRED, action.kind)
            assertTrue(action.repeatsSystemPrompt)
            assertFalse(owner.view.value.recovering)
            assertEquals(1 + MAX_ATTEMPTS, port.calls.count { it == "apply" })
            advanceTimeBy(RECOVERY_WINDOW_MILLIS)
            runCurrent()
            assertEquals(1 + MAX_ATTEMPTS, port.calls.count { it == "apply" })
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given the pause ends early during the wait then no further apply runs`() = runTest {
        val port = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val owner = receivedPause(port, CountingGate(StartGate.READY), failApplies = true)
        try {
            val applies = port.calls.count { it == "apply" }
            owner.endEarly(CURRENT)
            runCurrent()
            advanceTimeBy(RECOVERY_WINDOW_MILLIS)
            runCurrent()
            assertEquals(applies, port.calls.count { it == "apply" })
            assertFalse(owner.view.value.recovering)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given the person's own start fails then no automatic apply follows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeEnforcementPort(
            reapplyRequiresPrompt = true,
            applyReport = EnforcementApplyReport(EnforcementOutcome.FAILED, false, true),
        )
        val gate = CountingGate(StartGate.READY)
        val owner = ownerOf(dispatcher, FakeLocalSessionStore(), port, gate)
        try {
            owner.startSession(CURRENT, NOW, END, FROZEN)
            runCurrent()
            advanceTimeBy(RECOVERY_WINDOW_MILLIS)
            runCurrent()
            assertEquals(1, port.calls.count { it == "apply" })
            assertEquals(0, gate.checks)
            assertEquals(EnforcementActionKind.APPLY_FAILED, assertIs<EnforcementState.ActionRequired>(owner.view.value.state).kind)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given overlapping poll losses then one recovery runs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeLocalSessionStore()
        val port = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val gate = CountingGate(StartGate.TRANSIENT)
        val owner = ownerOf(dispatcher, store, port, gate)
        try {
            owner.startSession(CURRENT, NOW, END, FROZEN)
            runCurrent()
            val active = assertIs<LocalSessionStatus.Active>(assertIs<LocalSessionResult.Success<LocalSessionStatus>>(store.read(NOW)).value)
            port.statusError = IllegalStateException("helper lost")
            owner.pollNow(active.record)
            owner.pollNow(active.record)
            runCurrent()
            advanceTimeBy(RECOVERY_WINDOW_MILLIS)
            runCurrent()
            assertEquals(MAX_ATTEMPTS, gate.checks)
        } finally {
            owner.close()
        }
    }

    // SESSION-009: after a clean end in the same run, a pause received from another device that ends
    // unresumed left the notice in place, and the transition drain re-read the store without pause.
    @Test
    fun `given an unresumed received pause ends then the notice clears and the drain stops`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val delegate = FakeLocalSessionStore()
        var reads = 0
        val store = object : LocalSessionSyncStore by delegate {
            override suspend fun read(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
                reads += 1
                check(reads < READ_LIMIT) { "The transition drain kept reading" }
                return delegate.read(nowEpochMillis)
            }
        }
        val clock = FakeSessionClock(NOW)
        val port = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val owner = SessionTransitionOwner(dispatcher, store, clock, port, { targets() }, FakeSessionSyncTriggers())
        try {
            owner.startSession(OLD, NOW, END, FROZEN)
            runCurrent()
            owner.endEarly(OLD)
            runCurrent()
            delegate.adopt(CURRENT, NOW, END, NOW, FROZEN)
            owner.refresh()
            runCurrent()
            assertEquals(EnforcementActionKind.RESUME_REQUIRED, assertIs<EnforcementState.ActionRequired>(owner.view.value.state).kind)
            clock.nowEpochMillis = END + 1
            owner.refresh()
            runCurrent()
            assertIs<EnforcementState.Inactive>(owner.view.value.state)
        } finally {
            owner.close()
        }
    }

    private suspend fun TestScope.receivedPause(
        port: FakeEnforcementPort,
        gate: ScheduleStartGate,
        failApplies: Boolean = false,
    ): SessionTransitionOwner {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeLocalSessionStore()
        val owner = ownerOf(dispatcher, store, port, gate)
        owner.startSession(OLD, NOW, END, FROZEN)
        runCurrent()
        owner.endEarly(OLD)
        runCurrent()
        if (failApplies) {
            port.applyReport = EnforcementApplyReport(EnforcementOutcome.FAILED, false, false)
        }
        store.adopt(CURRENT, NOW, END, NOW, FROZEN)
        owner.refresh()
        runCurrent()
        return owner
    }

    private fun ownerOf(
        dispatcher: CoroutineDispatcher,
        store: LocalSessionSyncStore,
        port: FakeEnforcementPort,
        gate: ScheduleStartGate,
    ): SessionTransitionOwner {
        return SessionTransitionOwner(
            backgroundDispatcher = dispatcher,
            store = store,
            clock = FakeSessionClock(NOW),
            enforcement = port,
            loadTargets = { targets() },
            triggers = FakeSessionSyncTriggers(),
            automaticApply = gate,
        )
    }

    private fun targets(): SessionTargetsState {
        val policy = assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(FROZEN.domains, null)).policy
        return SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Unavailable())
    }

    private class CountingGate(
        private val answer: StartGate,
    ) : ScheduleStartGate {
        var checks = 0

        override suspend fun check(): StartGate {
            checks += 1
            return answer
        }
    }

    private companion object {
        const val NOW = 1_000_000_000_000L
        const val END = NOW + 30 * 60_000L
        const val MAX_ATTEMPTS = 3
        const val RECOVERY_WINDOW_MILLIS = 120_000L
        const val READ_LIMIT = 200
        val OLD = SessionId(testIdentifier(93))
        val CURRENT = SessionId(testIdentifier(94))
        val FROZEN = FrozenStartSet(persistentListOf("recovery.example"), null)
    }
}
