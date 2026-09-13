package app.posato.feature.session.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionOwnerCompletionTest {
    @Test
    fun `given confirmed cleanup when a remote session arrives then enforcement resumes or requests permission`() = runTest {
        for (requiresPrompt in listOf(true, false)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = FakeLocalSessionStore()
            val port = FakeEnforcementPort(reapplyRequiresPrompt = requiresPrompt)
            val owner = SessionTransitionOwner(dispatcher, store, FakeSessionClock(NOW), port, ::targets, FakeSessionSyncTriggers())
            try {
                owner.startSession(OLD, NOW, END, FROZEN)
                runCurrent()
                owner.endEarly(OLD)
                runCurrent()
                assertIs<EnforcementState.Inactive>(owner.view.value.state)
                store.adopt(CURRENT, NOW, END, NOW, FROZEN)
                owner.refresh()
                runCurrent()
                if (requiresPrompt) {
                    val action = assertIs<EnforcementState.ActionRequired>(owner.view.value.state)
                    assertEquals(EnforcementActionKind.RESUME_REQUIRED, action.kind)
                    assertEquals(1, port.calls.count { it == "apply" })
                    owner.retry()
                    runCurrent()
                }
                assertIs<EnforcementState.Active>(owner.view.value.state)
                assertEquals(CURRENT.reconciliationId(), port.lastRequest?.sessionId)
                assertEquals(2, port.calls.count { it == "apply" })
                val calls = port.calls.toList()
                owner.refresh()
                runCurrent()
                assertEquals(calls, port.calls)
            } finally {
                owner.close()
            }
        }
    }

    @Test
    fun `given a durable end before a crash then reopening clears and quiesces or offers truthful retry`() = runTest {
        for (outcome in listOf(EnforcementOutcome.CLEARED, EnforcementOutcome.FAILED)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val database = createLocalPolicyTestDatabase("ended-recovery.db")
            var driver = database.openDriver()
            var store = SqlLocalSessionStore(PosatoDatabase(driver), dispatcher)
            store.start(CURRENT, NOW, END, NOW, FROZEN)
            store.endEarly(NOW + 1)
            driver.close()
            driver = database.openDriver()
            store = SqlLocalSessionStore(PosatoDatabase(driver), dispatcher)
            val port = FakeEnforcementPort(clearOutcome = outcome, statusOutcome = EnforcementOutcome.APPLIED)
            val owner = SessionTransitionOwner(dispatcher, store, FakeSessionClock(NOW + 1), port, ::targets, FakeSessionSyncTriggers())
            try {
                owner.refresh()
                runCurrent()
                assertEquals(1, port.calls.count { it == "clear" })
                if (outcome == EnforcementOutcome.FAILED) {
                    assertEquals(EnforcementActionKind.CLEAR_FAILED, assertIs<EnforcementState.ActionRequired>(owner.view.value.state).kind)
                    owner.refresh()
                    runCurrent()
                    assertEquals(1, port.calls.count { it == "clear" })
                    port.clearOutcome = EnforcementOutcome.CLEARED
                    owner.retry()
                    runCurrent()
                    assertEquals(2, port.calls.count { it == "clear" })
                }
                assertIs<EnforcementState.Inactive>(owner.view.value.state)
                val calls = port.calls.toList()
                owner.refresh()
                runCurrent()
                assertEquals(calls, port.calls)
            } finally {
                owner.close()
                driver.close()
                database.delete()
            }
        }
    }

    @Test
    fun `given an empty local selection then successful empty enforcement converges once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeLocalSessionStore()
        val delegate = FakeEnforcementPort(applyReport = EnforcementApplyReport(EnforcementOutcome.NOTHING_TO_ENFORCE, false, false))
        val port = object : EnforcementPort by delegate {
            override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
                check(delegate.calls.count { it == "apply" } < 2)
                return delegate.apply(request)
            }
        }
        val owner = SessionTransitionOwner(dispatcher, store, FakeSessionClock(NOW), port, ::targets, FakeSessionSyncTriggers())
        try {
            owner.startSession(CURRENT, NOW, END, FROZEN)
            runCurrent()
            owner.refresh()
            runCurrent()
            assertEquals(1, delegate.calls.count { it == "apply" })
            assertIs<EnforcementState.Active>(owner.view.value.state)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given an ended cleanup queued behind a newer apply then the newer enforcement survives`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val delegateStore = FakeLocalSessionStore()
        var oldRead: LocalSessionStatus? = null
        val store = object : LocalSessionSyncStore by delegateStore {
            override suspend fun read(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
                val captured = oldRead
                oldRead = null
                return if (captured != null) LocalSessionResult.Success(captured) else delegateStore.read(nowEpochMillis)
            }
        }
        val delegate = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val gate = CompletableDeferred<Unit>()
        val port = object : EnforcementPort by delegate {
            override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
                if (request.sessionId == CURRENT.reconciliationId()) gate.await()
                return delegate.apply(request)
            }
        }
        val owner = SessionTransitionOwner(dispatcher, store, FakeSessionClock(NOW), port, ::targets, FakeSessionSyncTriggers())
        try {
            owner.startSession(OLD, NOW, END, FROZEN)
            val ended = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(delegateStore.endEarly(NOW)).value
            val start = launch { owner.startSession(CURRENT, NOW, END, FROZEN) }
            runCurrent()
            oldRead = ended
            owner.settle(ended)
            runCurrent()
            val clears = delegate.calls.count { it == "clear" }
            gate.complete(Unit)
            runCurrent()
            start.join()
            assertEquals(clears, delegate.calls.count { it == "clear" })
            assertEquals(CURRENT.reconciliationId(), delegate.lastRequest?.sessionId)
            assertIs<EnforcementState.Active>(owner.view.value.state)
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given retry target loading suspended across replacement then it cannot clear the newer applied session`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeLocalSessionStore()
        val port = FakeEnforcementPort(reapplyRequiresPrompt = true)
        val gate = CompletableDeferred<Unit>()
        var suspendTargets = false
        val owner = SessionTransitionOwner(dispatcher, store, FakeSessionClock(NOW), port, {
            if (suspendTargets) {
                suspendTargets = false
                gate.await()
            }
            targets()
        }, FakeSessionSyncTriggers())
        try {
            owner.startSession(OLD, NOW, END, FROZEN)
            runCurrent()
            suspendTargets = true
            owner.retry()
            runCurrent()
            store.endEarly(NOW)
            owner.startSession(CURRENT, NOW, END, FROZEN)
            runCurrent()
            val calls = port.calls.toList()
            gate.complete(Unit)
            runCurrent()
            assertEquals(calls, port.calls)
            assertEquals(CURRENT.reconciliationId(), port.lastRequest?.sessionId)
            assertIs<EnforcementState.Active>(owner.view.value.state)
        } finally {
            owner.close()
        }
    }

    private fun targets(): SessionTargetsState {
        val policy = assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(FROZEN.domains, null)).policy
        return SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Unavailable())
    }

    private companion object {
        const val NOW = 1_000_000_000_000L
        const val END = NOW + 30 * 60_000L
        val OLD = SessionId(testIdentifier(91))
        val CURRENT = SessionId(testIdentifier(92))
        val FROZEN = FrozenStartSet(persistentListOf("recovery.example"), null)
    }
}
