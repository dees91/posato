package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SessionWorkspaceCapture
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.domain.StoredSessionIntent
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionTransitionOwnerTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a stale confirmation when ending then the row is untouched`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(1))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(current, NOW, NOW + DURATION, NOW, START_SET),
        )
        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()
        enforcement.calls.clear()

        val result = owner.endEarly(SessionId(testIdentifier(2)))
        scheduler.runCurrent()

        val status = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result).value
        val active = assertIs<LocalSessionStatus.Active>(status)
        assertEquals(current, active.record.sessionId)
        assertTrue(store.intentKinds().isEmpty())
        assertTrue("clear" !in enforcement.calls)
    }

    @Test
    fun `given a replacement when settled then cleanup precedes the new application`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val first = SessionId(testIdentifier(3))
        val second = SessionId(testIdentifier(4))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(first, NOW, NOW + DURATION, NOW, START_SET),
        )
        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()
        assertTrue(enforcement.calls.contains("apply"))

        enforcement.calls.clear()
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.adopt(second, NOW, NOW + DURATION, NOW, START_SET),
        )
        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()

        val clearIndex = enforcement.calls.indexOf("clear")
        val applyIndex = enforcement.calls.indexOf("apply")
        assertTrue(clearIndex >= 0 && applyIndex > clearIndex)
        assertEquals(second.reconciliationId(), enforcement.lastRequest?.sessionId)
        assertIs<EnforcementState.Active>(owner.view.value.state)
    }

    @Test
    fun `given a suspended expiry signal when settled then restrictions clear`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(5))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(current, NOW, NOW + DURATION, NOW, START_SET),
        )
        enforcement.expiredSessionIds = setOf(current.reconciliationId())

        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()

        assertEquals(listOf("poll", "clear"), enforcement.calls)
    }

    @Test
    fun `given a suspended expiry signal with a failing store when settled then nothing clears`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(6))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(current, NOW, NOW + DURATION, NOW, START_SET),
        )
        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()
        enforcement.calls.clear()
        enforcement.expiredSessionIds = setOf(current.reconciliationId())
        store.readFailure = LocalSessionFailure.STORAGE_FAILURE

        // A fresh owner settles the last known status: the reconcile job polls the
        // native signal, then fails the durable read and must not clear first.
        val fresh = ownerOf(store, enforcement, clock)
        val known = LocalSessionStatus.Active(
            SessionRecord(current, NOW, NOW + DURATION),
            DURATION,
            START_SET,
            SessionOrigin.LOCAL,
        )
        fresh.settle(known)
        scheduler.runCurrent()

        assertTrue("clear" !in enforcement.calls)
        val action = assertIs<EnforcementState.ActionRequired>(fresh.view.value.state)
        assertEquals(EnforcementActionKind.APPLY_FAILED, action.kind)
    }

    @Test
    fun `given an expired row when ticked then it settles without an exchange`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(SessionId(testIdentifier(7)), NOW, NOW + DURATION, NOW, START_SET),
        )
        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()
        assertTrue(enforcement.calls.contains("apply"))
        enforcement.calls.clear()

        clock.nowEpochMillis = NOW + DURATION + 1
        owner.onTick(clock.nowEpochMillis)
        scheduler.runCurrent()

        assertTrue("clear" in enforcement.calls)
        assertIs<LocalSessionStatus.Ended>(assertRead(store, clock.nowEpochMillis))
    }

    @Test
    fun `given a linked start when commanded then the intent is recorded and sync is requested`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val triggers = FakeSessionSyncTriggers(SessionWorkspaceCapture.Linked(testIdentifier(8).copyBytes()))
        val owner = ownerOf(store, enforcement, clock, triggers)
        val current = SessionId(testIdentifier(9))

        val result = owner.startSession(current, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()

        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
        assertEquals(
            listOf(StoredSessionIntent.StartSession(current, NOW, NOW + DURATION)),
            store.intentKinds(),
        )
        assertEquals(1, triggers.syncRequests)
        assertTrue(enforcement.calls.contains("apply"))
    }

    @Test
    fun `given an unlinked start when commanded then no intent is recorded and sync is not requested`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val triggers = FakeSessionSyncTriggers(SessionWorkspaceCapture.Unlinked)
        val owner = ownerOf(store, enforcement, clock, triggers)

        val result = owner.startSession(SessionId(testIdentifier(10)), NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()

        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
        assertTrue(store.intentKinds().isEmpty())
        assertEquals(0, triggers.syncRequests)
        assertTrue(enforcement.calls.contains("apply"))
    }

    @Test
    fun `given an adopted row when ended then no end intent is recorded`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val triggers = FakeSessionSyncTriggers(SessionWorkspaceCapture.Linked(testIdentifier(11).copyBytes()))
        val owner = ownerOf(store, enforcement, clock, triggers)
        val adopted = SessionId(testIdentifier(12))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.adopt(adopted, NOW, NOW + DURATION, NOW, START_SET),
        )
        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()

        val result = owner.endEarly(adopted)
        scheduler.runCurrent()

        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
        assertTrue(store.intentKinds().isEmpty())
        assertTrue("clear" in enforcement.calls)
    }

    private fun ownerOf(
        store: FakeLocalSessionStore,
        enforcement: FakeEnforcementPort,
        clock: FakeSessionClock,
        triggers: FakeSessionSyncTriggers = FakeSessionSyncTriggers(),
    ): SessionTransitionOwner {
        val policyStore = FakeSessionPolicyStore(
            LocalPolicyResult.Success(LocalTargetPolicyState(0, testPolicy())),
        )
        return sessionOwnerOf(store, enforcement, clock, policyStore, FakeSessionMappings(), triggers, dispatcher)
    }

    private suspend fun assertRead(
        store: FakeLocalSessionStore,
        now: Long,
    ): LocalSessionStatus {
        return assertIs<LocalSessionResult.Success<LocalSessionStatus>>(store.read(now)).value
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
        const val DURATION: Long = 30 * 60_000L
        val START_SET: FrozenStartSet = FrozenStartSet(persistentListOf("stable.example"), null)

        fun testPolicy() = policyStoreOf(listOf("stable.example")).let { policy ->
            (policy.result as LocalPolicyResult.Success).value.policy
        }

        fun policyStoreOf(domains: List<String>): FakeSessionPolicyStore {
            val result = TargetPolicy.fromStoredValues(domains, null)
            val policy = (result as TargetPolicyValidationResult.Success).policy
            return FakeSessionPolicyStore(LocalPolicyResult.Success(LocalTargetPolicyState(0, policy)))
        }
    }
}
