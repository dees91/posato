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
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionLimits
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
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
    fun `given a suspended expiry signal when settled then the bank commits before the acknowledge`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        // The wall clock is rolled back inside the interval: only the native
        // signal proves the end, so the bank must not consult the clock.
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(5))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(current, NOW, NOW + DURATION, NOW, START_SET),
        )
        enforcement.expiredSessionIds = setOf(current.reconciliationId())

        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()

        assertEquals(listOf("peek", "acknowledge", "status"), enforcement.calls)
        assertEquals(listOf(current.reconciliationId()), enforcement.acknowledgedSessionIds)
        val ended = assertIs<LocalSessionStatus.Ended>(assertRead(store, NOW))
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
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
        store.markExpiredFailure = LocalSessionFailure.STORAGE_FAILURE

        // A fresh owner settles the last known status: the reconcile job peeks
        // the native signal, then fails the durable bank and must park the
        // observation instead of clearing or settling Active as success.
        val fresh = ownerOf(store, enforcement, clock)
        val known = LocalSessionStatus.Active(
            SessionRecord(current, NOW, NOW + DURATION),
            DURATION,
            START_SET,
            SessionOrigin.LOCAL,
        )
        fresh.settle(known)
        scheduler.runCurrent()

        assertEquals(listOf("peek"), enforcement.calls)
        assertTrue(enforcement.acknowledgedSessionIds.isEmpty())
        val action = assertIs<EnforcementState.ActionRequired>(fresh.view.value.state)
        assertEquals(EnforcementActionKind.APPLY_FAILED, action.kind)

        // The parked observation converges through the bank flow once the
        // store recovers, never through a re-apply of the expired session.
        store.markExpiredFailure = null
        fresh.retry()
        scheduler.runCurrent()

        assertEquals(listOf("peek", "peek", "acknowledge", "clear"), enforcement.calls)
        assertIs<LocalSessionStatus.Ended>(assertRead(store, NOW))
    }

    @Test
    fun `given a parked expiry when the owner restarts before banking then the peek banks again`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val first = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(13))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(current, NOW, NOW + DURATION, NOW, START_SET),
        )
        enforcement.expiredSessionIds = setOf(current.reconciliationId())
        store.markExpiredFailure = LocalSessionFailure.STORAGE_FAILURE
        first.settle(assertRead(store, NOW))
        scheduler.runCurrent()
        assertEquals(listOf("peek"), enforcement.calls)

        // Restart: a new owner on the same store observes the still-unacked
        // native record and banks it.
        store.markExpiredFailure = null
        val second = ownerOf(store, enforcement, clock)
        second.settle(assertRead(store, NOW))
        scheduler.runCurrent()

        assertEquals(listOf("peek", "peek", "acknowledge", "status"), enforcement.calls)
        assertIs<LocalSessionStatus.Ended>(assertRead(store, NOW))
    }

    @Test
    fun `given a banked expiry when the acknowledge fails then the row still converges`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(14))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(current, NOW, NOW + DURATION, NOW, START_SET),
        )
        enforcement.expiredSessionIds = setOf(current.reconciliationId())
        enforcement.acknowledgeError = IllegalStateException("ack offline")

        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()

        assertEquals(listOf("peek", "acknowledge", "status"), enforcement.calls)
        assertIs<LocalSessionStatus.Ended>(assertRead(store, NOW))

        // A restart finds the terminal row: nothing re-applies, and the
        // leftover native record waits for a future schedule to clear it.
        enforcement.acknowledgeError = null
        val restarted = ownerOf(store, enforcement, clock)
        restarted.settle(assertRead(store, NOW))
        scheduler.runCurrent()

        assertIs<LocalSessionStatus.Ended>(assertRead(store, NOW))
        assertTrue("apply" !in enforcement.calls)
        assertEquals(setOf(current.reconciliationId()), enforcement.expiredSessionIds)
    }

    @Test
    fun `given a replaced row when a stale expiry banks then the new session drains instead`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val old = SessionId(testIdentifier(15))
        val fresh = SessionId(testIdentifier(16))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.start(old, NOW, NOW + DURATION, NOW, START_SET),
        )
        val stale = assertRead(store, NOW)
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.adopt(fresh, NOW, NOW + DURATION, NOW, START_SET),
        )
        enforcement.expiredSessionIds = setOf(old.reconciliationId())

        // The settle captured the old row before the replacement landed.
        owner.settle(stale)
        scheduler.runCurrent()

        assertEquals(listOf(old.reconciliationId()), enforcement.acknowledgedSessionIds)
        val current = assertIs<LocalSessionStatus.Active>(assertRead(store, NOW))
        assertEquals(fresh, current.record.sessionId)
    }

    @Test
    fun `given a command that outlasts the session when started then the end is recorded without applying`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW + SessionLimits.MIN_DURATION_MILLIS)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(17))

        val result = owner.startSession(current, NOW, NOW + SessionLimits.MIN_DURATION_MILLIS, START_SET)
        scheduler.runCurrent()

        val ended = assertIs<LocalSessionStatus.Ended>(assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result).value)
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
        assertEquals(NOW + SessionLimits.MIN_DURATION_MILLIS, ended.record.endEpochMillis)
        assertTrue("apply" !in enforcement.calls)
    }

    @Test
    fun `given a setup wait that outlasts the session when started then the end is recorded without applying`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val policyStore = policyStoreOf(listOf("stable.example"))
        // The session is still active at commit time, but target setup
        // suspends past its end before the apply runs.
        val owner = SessionTransitionOwner(
            backgroundDispatcher = dispatcher,
            store = store,
            clock = clock,
            enforcement = enforcement,
            loadTargets = {
                clock.nowEpochMillis = NOW + DURATION + 1
                loadSessionTargets(policyStore, FakeSessionMappings())
            },
            triggers = FakeSessionSyncTriggers(),
        )
        val current = SessionId(testIdentifier(21))

        val result = owner.startSession(current, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()

        val ended = assertIs<LocalSessionStatus.Ended>(assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result).value)
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
        assertEquals(NOW + DURATION, ended.record.endEpochMillis)
        assertTrue("apply" !in enforcement.calls)
        assertTrue("clear" !in enforcement.calls)
    }

    @Test
    fun `given a mid-life read failure before apply when started then no terminal fact is invented`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val policyStore = policyStoreOf(listOf("stable.example"))
        // The commit lands while the store is healthy; the store turns
        // unreadable during setup, so the pre-apply guard proves nothing and
        // must skip the apply without banking an expiry for a live session.
        val owner = SessionTransitionOwner(
            backgroundDispatcher = dispatcher,
            store = store,
            clock = clock,
            enforcement = enforcement,
            loadTargets = {
                store.readFailure = LocalSessionFailure.STORAGE_FAILURE
                loadSessionTargets(policyStore, FakeSessionMappings())
            },
            triggers = FakeSessionSyncTriggers(),
        )
        val current = SessionId(testIdentifier(22))

        val result = owner.startSession(current, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()

        assertIs<LocalSessionResult.Failure>(result)
        assertEquals(0, store.markExpiredCalls)
        assertTrue("apply" !in enforcement.calls)
        assertTrue("clear" !in enforcement.calls)
        store.readFailure = null
        val live = assertIs<LocalSessionStatus.Active>(assertRead(store, NOW))
        assertEquals(NOW + DURATION, live.record.endEpochMillis)
    }

    @Test
    fun `given an outlasted command with a failing terminal write when started then the read still converges`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW + SessionLimits.MIN_DURATION_MILLIS)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(18))
        store.markExpiredFailure = LocalSessionFailure.STORAGE_FAILURE

        val result = owner.startSession(current, NOW, NOW + SessionLimits.MIN_DURATION_MILLIS, START_SET)
        scheduler.runCurrent()

        // The commit landed but the terminal write failed: no apply ever
        // happens, and the tail read past the deadline still converges the row
        // without enforcing anything (nothing was applied, so no clear either).
        val ended = assertIs<LocalSessionStatus.Ended>(assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result).value)
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
        assertTrue("apply" !in enforcement.calls)
        assertTrue("clear" !in enforcement.calls)
    }

    @Test
    fun `given a stale in-flight clear when the row is replaced then the new session drains`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val first = SessionId(testIdentifier(19))
        val second = SessionId(testIdentifier(20))
        owner.startSession(first, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()
        // The end queues its clear job without running it; the replacement
        // applies first, so the in-flight clear removes a newer enforcement.
        // Fake port calls never yield, keeping this interleaving deterministic.
        owner.endEarly(first)
        owner.startSession(second, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()

        assertEquals(
            listOf("displace", "apply", "displace", "apply"),
            enforcement.calls,
        )
        assertEquals(second.reconciliationId(), enforcement.lastRequest?.sessionId)
        assertIs<EnforcementState.Active>(owner.view.value.state)
        val current = assertIs<LocalSessionStatus.Active>(assertRead(store, NOW))
        assertEquals(second, current.record.sessionId)
    }

    @Test
    fun `given a reapply whose clear outlasts the session when retrying then the end is recorded without applying`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val current = SessionId(testIdentifier(23))
        owner.startSession(current, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()
        assertEquals(1, enforcement.calls.count { call -> call == "apply" })
        // The retry's clear suspends past the mandatory end: the decisive
        // check before apply must bank the terminal fact instead of
        // enforcing the expired request.
        enforcement.clearHook = { clock.nowEpochMillis = NOW + DURATION + 1 }

        owner.retry()
        scheduler.runCurrent()

        assertEquals(1, enforcement.calls.count { call -> call == "apply" })
        val ended = assertIs<LocalSessionStatus.Ended>(assertRead(store, NOW + DURATION + 1))
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
        assertEquals(NOW + DURATION, ended.record.endEpochMillis)
        assertIs<EnforcementState.Inactive>(owner.view.value.state)
    }

    @Test
    fun `given a replacement clear that outlasts the replaced session when drained then the view converges without applying`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val first = SessionId(testIdentifier(24))
        val second = SessionId(testIdentifier(25))
        owner.startSession(first, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()
        // The replacement cleanup suspends mid-clear; the replaced session
        // ends before cleanup completes.
        enforcement.clearGate = CompletableDeferred()
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.adopt(second, NOW, NOW + DURATION, NOW, START_SET),
        )
        owner.settle(assertRead(store, NOW))
        scheduler.runCurrent()
        owner.endEarly(second)
        enforcement.clearGate?.complete(Unit)
        scheduler.runCurrent()

        // The stale apply never runs for the ended row, and the skipped end
        // transition still converges the view instead of leaving it Active.
        assertEquals(1, enforcement.calls.count { call -> call == "apply" })
        assertEquals(first.reconciliationId(), enforcement.lastRequest?.sessionId)
        assertIs<LocalSessionStatus.Ended>(assertRead(store, NOW))
        assertIs<EnforcementState.Inactive>(owner.view.value.state)
    }

    @Test
    fun `given a pending foreign native signal when starting then it is retained before the schedule lands`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val superseded = SessionId(testIdentifier(26))
        enforcement.displacedSessionId = superseded.reconciliationId()
        val current = SessionId(testIdentifier(27))

        owner.startSession(current, NOW, NOW + DURATION, START_SET)
        scheduler.runCurrent()

        // The replacement path itself persists the foreign fact: no manual
        // retain call stands between the signal and the new schedule.
        assertTrue("displace" in enforcement.calls)
        assertTrue(superseded in store.retainedMarkers)
        assertTrue(superseded.reconciliationId() in enforcement.acknowledgedSessionIds)
        val live = assertIs<LocalSessionStatus.Active>(assertRead(store, NOW))
        assertEquals(current, live.record.sessionId)
    }

    @Test
    fun `given a superseded observation when reconciling then the fact is recorded before acknowledgement`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.APPLIED)
        val clock = FakeSessionClock(NOW)
        val owner = ownerOf(store, enforcement, clock)
        val superseded = SessionId(testIdentifier(28))
        val current = SessionId(testIdentifier(29))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.adopt(current, NOW, NOW + DURATION, NOW, START_SET),
        )
        // A stale settle carries the superseded identity while the row
        // already moved on: the peek still observes its native signal, but
        // the bank is refused by identity.
        val stale = LocalSessionStatus.Active(
            SessionRecord(superseded, NOW, NOW + DURATION),
            DURATION,
            START_SET,
            SessionOrigin.LOCAL,
        )
        owner.settle(stale)
        enforcement.expiredSessionIds += superseded.reconciliationId()
        scheduler.runCurrent()

        assertTrue(superseded in store.retainedMarkers)
        assertTrue(superseded.reconciliationId() in enforcement.acknowledgedSessionIds)
        assertEquals(current.reconciliationId(), enforcement.lastRequest?.sessionId)
        assertTrue(enforcement.calls.indexOf("acknowledge") < enforcement.calls.indexOf("apply"))
        val live = assertIs<LocalSessionStatus.Active>(assertRead(store, NOW))
        assertEquals(current, live.record.sessionId)
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
    fun `given an adopted row when ended then the end intent is recorded`() = runTest(dispatcher) {
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
        assertEquals(listOf(StoredSessionIntent.EndSession(adopted)), store.intentKinds())
        assertTrue("clear" in enforcement.calls)
    }

    @Test
    fun `given replacement during a status read then the new identity is applied`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val clock = FakeSessionClock(NOW)
        val delegate = FakeEnforcementPort(statusOutcome = EnforcementOutcome.APPLIED)
        val gate = CompletableDeferred<Unit>()
        var blockStatus = true
        val port = object : app.posato.feature.enforcement.EnforcementPort by delegate {
            override suspend fun status(): EnforcementOutcome {
                if (blockStatus) gate.await()
                return delegate.status()
            }
        }
        val owner = SessionTransitionOwner(
            dispatcher,
            store,
            clock,
            port,
            { loadSessionTargets(policyStoreOf(listOf("stable.example")), FakeSessionMappings()) },
            FakeSessionSyncTriggers(),
        )
        val a = SessionId(testIdentifier(96))
        val b = SessionId(testIdentifier(97))
        try {
            // On reopen the native port is still enforcing A; its generic status cannot identify B.
            store.adopt(a, NOW, NOW + DURATION, NOW, START_SET)
            owner.settle(assertRead(store, NOW))
            scheduler.runCurrent()
            store.adopt(b, NOW, NOW + DURATION, NOW, START_SET)
            owner.settle(assertRead(store, NOW))
            scheduler.runCurrent()
            blockStatus = false
            gate.complete(Unit)
            scheduler.runCurrent()
            assertEquals(
                b.reconciliationId(),
                delegate.lastRequest?.sessionId,
                "B was declared enforced from A's generic APPLIED status without an apply: ${delegate.calls}",
            )
        } finally {
            owner.close()
        }
    }

    @Test
    fun `given idle waiting without a session when a minute passes then the store is read only at the recheck`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val owner = ownerOf(store, FakeEnforcementPort(), FakeSessionClock(NOW))
        backgroundScope.launch { owner.runWhileHosted(idleRecheckMillis = IDLE_RECHECK) }
        runCurrent()
        val initialReads = store.reads.size

        advanceTimeBy(IDLE_RECHECK - 1)
        runCurrent()
        assertEquals(initialReads, store.reads.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(initialReads + 1, store.reads.size)
        owner.close()
    }

    @Test
    fun `given idle waiting when a session starts then ticking resumes within a second`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val owner = ownerOf(store, FakeEnforcementPort(), FakeSessionClock(NOW))
        backgroundScope.launch { owner.runWhileHosted(idleRecheckMillis = IDLE_RECHECK) }
        runCurrent()
        advanceTimeBy(5_000)
        store.start(SessionId(testIdentifier(7)), NOW, NOW + DURATION, NOW, START_SET)
        owner.settle(assertRead(store, NOW))
        runCurrent()
        val readsAfterStart = store.reads.size

        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(store.reads.size > readsAfterStart)
        owner.close()
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
        const val IDLE_RECHECK: Long = 60_000L
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
