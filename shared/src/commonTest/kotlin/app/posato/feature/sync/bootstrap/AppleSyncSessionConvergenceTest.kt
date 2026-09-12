package app.posato.feature.sync.bootstrap

import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SequencedSessionIntent
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.ui.FakeEnforcementPort
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncReducer
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleSyncSessionConvergenceTest {
    @Test
    fun `given a local start when exchanged then the peer adopts the same session`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-adopt-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-adopt-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            val sessionId = SessionId(testIdentifier(61))
            start(first, sessionId)
            exchange(first, second)

            val adopted = assertIs<LocalSessionStatus.Active>(second.read())
            assertEquals(sessionId, adopted.record.sessionId)
            assertEquals(SessionOrigin.ADOPTED, adopted.origin)
            assertEquals(FROZEN_SECOND, adopted.frozenStartSet)
            assertTrue(second.enforcement.calls.contains("apply"))
            assertEquals(sessionId.reconciliationId(), second.enforcement.lastRequest?.sessionId)
            assertEquals(SyncStatus.COMPLETED, first.status())
            assertEquals(SyncStatus.COMPLETED, second.status())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given an early end when exchanged then both converge to ended`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-end-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-end-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            val sessionId = SessionId(testIdentifier(62))
            start(first, sessionId)
            exchange(first, second)
            assertIs<LocalSessionStatus.Active>(second.read())

            end(second, sessionId)
            exchange(first, second)

            val firstEnded = assertIs<LocalSessionStatus.Ended>(first.read())
            assertEquals(SessionEndKind.ENDED_EARLY, firstEnded.kind)
            val secondEnded = assertIs<LocalSessionStatus.Ended>(second.read())
            assertEquals(SessionEndKind.ENDED_EARLY, secondEnded.kind)
            assertTrue(first.enforcement.calls.contains("clear"))
            assertTrue(second.enforcement.calls.contains("clear"))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given concurrent starts when exchanged then both converge to the greatest order start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-race-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-race-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            // The second device holds the later sync clock, so its start wins the
            // total order on both replicas.
            start(first, SessionId(testIdentifier(63)))
            start(second, SessionId(testIdentifier(64)))
            exchange(first, second)
            exchange(first, second)

            val winner = SessionId(testIdentifier(64))
            assertEquals(winner, assertIs<LocalSessionStatus.Active>(first.read()).record.sessionId)
            assertEquals(winner, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
            assertEquals(SessionOrigin.ADOPTED, assertIs<LocalSessionStatus.Active>(first.read()).origin)
            assertEquals(winner.reconciliationId(), first.enforcement.lastRequest?.sessionId)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a start and end before the peer syncs then the peer never activates`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-endfirst-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-endfirst-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            val sessionId = SessionId(testIdentifier(65))
            start(first, sessionId)
            end(first, sessionId)
            second.harness.sync.syncNow()
            advanceUntilIdle()

            assertIs<LocalSessionStatus.Inactive>(second.read())
            assertTrue(second.enforcement.calls.none { call -> call == "apply" })
            assertIs<LocalSessionStatus.Ended>(first.read())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a future start when exchanged then the peer waits until it is eligible`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-future-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-future-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            // The second device evaluates behind the start: the candidate is future.
            second.clock.nowEpochMillis = NOW - 60_000L
            val sessionId = SessionId(testIdentifier(66))
            start(first, sessionId)
            exchange(first, second)

            assertIs<LocalSessionStatus.Inactive>(second.read())
            assertTrue(second.enforcement.calls.none { call -> call == "apply" })

            second.clock.nowEpochMillis = NOW
            second.harness.sync.syncNow()
            advanceUntilIdle()

            val adopted = assertIs<LocalSessionStatus.Active>(second.read())
            assertEquals(sessionId, adopted.record.sessionId)
            assertTrue(second.enforcement.calls.contains("apply"))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a restart before authoring when exchanged then the exact start is published once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-restart-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-restart-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            val sessionId = SessionId(testIdentifier(67))
            start(first, sessionId)

            // Restart before any exchange: the durable intent survives and the
            // retried pass authors the same immutable start instead of a second one.
            first.reopenOwner()
            exchange(first, second)

            assertEquals(sessionId, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
            assertEquals(1, sessionStartOperations(first, sessionId))
            assertEquals(1, sessionStartOperations(second, sessionId))
            assertTrue(second.conflicted().isEmpty())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given an offline start and end when reconnecting then both operations arrive in order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-offline-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-offline-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            mailbox.saveResult = BundleSaveResult.Retryable
            val sessionId = SessionId(testIdentifier(68))
            start(first, sessionId)
            first.clock.nowEpochMillis = NOW + 60_000L
            end(first, sessionId)
            first.harness.sync.syncNow()
            advanceUntilIdle()

            mailbox.saveResult = BundleSaveResult.Saved
            exchange(first, second)

            assertIs<LocalSessionStatus.Ended>(first.read())
            assertIs<LocalSessionStatus.Inactive>(second.read())
            assertTrue(second.enforcement.calls.none { call -> call == "apply" })
            assertEquals(1, sessionStartOperations(second, sessionId))
            assertEquals(1, sessionEndOperations(second, sessionId))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given expiry when exchanged then the terminal fact survives restart and rollback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-expiry-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-expiry-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            val sessionId = SessionId(testIdentifier(69))
            start(first, sessionId, NOW + MIN_DURATION)
            exchange(first, second)
            assertIs<LocalSessionStatus.Active>(second.read())

            first.clock.nowEpochMillis = NOW + MIN_DURATION + 1
            second.clock.nowEpochMillis = NOW + MIN_DURATION + 1
            first.owner.refresh()
            second.owner.refresh()
            runCurrent()
            exchange(first, second)

            assertIs<LocalSessionStatus.Ended>(first.read())
            assertIs<LocalSessionStatus.Ended>(second.read())

            // Restart both owners on the same databases, then roll the wall clock
            // back into the session window: neither replica revives the session.
            // The setup apply above is history; the revival check starts here.
            first.enforcement.calls.clear()
            second.enforcement.calls.clear()
            first.reopenOwner()
            second.reopenOwner()
            first.clock.nowEpochMillis = NOW
            second.clock.nowEpochMillis = NOW
            first.owner.refresh()
            second.owner.refresh()
            runCurrent()
            exchange(first, second)

            assertIs<LocalSessionStatus.Ended>(first.read())
            assertIs<LocalSessionStatus.Ended>(second.read())
            assertTrue(first.enforcement.calls.none { call -> call == "apply" })
            assertTrue(second.enforcement.calls.none { call -> call == "apply" })
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given an adopted session when the workspace is removed then it survives without replay`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-removal-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-removal-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            val adopted = SessionId(testIdentifier(70))
            start(first, adopted)
            exchange(first, second)
            assertIs<LocalSessionStatus.Active>(second.read())

            second.harness.sync.removeWorkspace()
            advanceUntilIdle()

            // D2: removal preserves the current bounded session locally while
            // discarding its pending sync intent and workspace association.
            val preserved = assertIs<LocalSessionStatus.Active>(second.read())
            assertEquals(adopted, preserved.record.sessionId)
            assertEquals(SessionOrigin.ADOPTED, preserved.origin)
            assertTrue(second.sessionIntents().isEmpty())

            // A later explicit link follows D1 for local sessions only: the
            // adopted session is never replayed into the workspace. Removal
            // (SYNC-015) resets the replica continuation, so the replica can
            // only be read again after the fresh link refetches it.
            second.establish()
            advanceUntilIdle()
            second.harness.sync.syncNow()
            advanceUntilIdle()
            // Exactly the original start is accepted back: no duplicate is
            // published for the adopted session.
            assertEquals(1, sessionStartOperations(second, adopted))
            assertTrue(second.sessionIntents().isEmpty())
            val relinked = assertIs<LocalSessionStatus.Active>(second.read())
            assertEquals(adopted, relinked.record.sessionId)
            assertEquals(SessionOrigin.ADOPTED, relinked.origin)

            // A new local session still publishes through the fresh link. The
            // preserved session stays active until it is deliberately ended:
            // starting over it would fail with ALREADY_ACTIVE.
            end(second, adopted)
            val fresh = SessionId(testIdentifier(71))
            start(second, fresh)
            exchange(first, second)
            exchange(first, second)

            assertEquals(fresh, assertIs<LocalSessionStatus.Active>(first.read()).record.sessionId)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a pre-link local session when linking then the first pass publishes it once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-link-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-link-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            advanceUntilIdle()

            // The second device starts while unlinked: the intent waits for the link.
            val sessionId = SessionId(testIdentifier(72))
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
                second.sessions.start(sessionId, NOW, NOW + DURATION, NOW, FROZEN_SECOND),
            )
            second.establish()
            advanceUntilIdle()

            exchange(first, second)
            exchange(first, second)

            assertEquals(sessionId, assertIs<LocalSessionStatus.Active>(first.read()).record.sessionId)
            assertEquals(1, sessionStartOperations(first, sessionId))
            assertEquals(1, sessionStartOperations(second, sessionId))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a replacement when exchanged then cleanup precedes the new application`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-replace-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-replace-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            start(first, SessionId(testIdentifier(73)))
            exchange(first, second)
            assertIs<LocalSessionStatus.Active>(second.read())
            second.enforcement.calls.clear()

            val replacement = SessionId(testIdentifier(74))
            end(first, SessionId(testIdentifier(73)))
            start(first, replacement)
            exchange(first, second)
            exchange(first, second)

            val adopted = assertIs<LocalSessionStatus.Active>(second.read())
            assertEquals(replacement, adopted.record.sessionId)
            assertEquals(FROZEN_SECOND, adopted.frozenStartSet)
            val clearIndex = second.enforcement.calls.indexOf("clear")
            val applyIndex = second.enforcement.calls.indexOf("apply")
            assertTrue(clearIndex >= 0 && applyIndex > clearIndex)
            assertEquals(replacement.reconciliationId(), second.enforcement.lastRequest?.sessionId)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given an ended row when a newer session converges then it supersedes without fallback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-supersede-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-supersede-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()

            val old = SessionId(testIdentifier(80))
            start(first, old)
            exchange(first, second)
            assertIs<LocalSessionStatus.Active>(second.read())

            end(first, old)
            end(second, old)
            val fresh = SessionId(testIdentifier(81))
            start(second, fresh)
            exchange(first, second)
            exchange(first, second)

            val adopted = assertIs<LocalSessionStatus.Active>(first.read())
            assertEquals(fresh, adopted.record.sessionId)
            val current = assertIs<LocalSessionStatus.Active>(second.read())
            assertEquals(fresh, current.record.sessionId)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a fresh peer when an expired session converges then it banks without activating`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-expired-bank-first.db", mailbox, SyncWallClock { 100 }, FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-expired-bank-second.db", mailbox, SyncWallClock { 200 }, FROZEN_SECOND)
        val third = sessionPeer(dispatcher, "session-expired-bank-third.db", mailbox, SyncWallClock { 300 }, FROZEN_THIRD)
        try {
            first.establish()
            second.establish()
            third.establish()
            advanceUntilIdle()

            val session = SessionId(testIdentifier(82))
            start(first, session)
            exchange(first, second)
            assertIs<LocalSessionStatus.Active>(second.read())

            // All clocks move past the end before the fresh peer ever syncs:
            // it observes only the expired candidate.
            first.clock.nowEpochMillis = NOW + DURATION + 1
            second.clock.nowEpochMillis = NOW + DURATION + 1
            third.clock.nowEpochMillis = NOW + DURATION + 1
            first.harness.sync.syncNow()
            advanceUntilIdle()
            second.harness.sync.syncNow()
            advanceUntilIdle()
            third.harness.sync.syncNow()
            advanceUntilIdle()

            assertIs<LocalSessionStatus.Inactive>(third.read())
            assertTrue(session in third.harness.snapshot().terminalExpiryFacts)

            // A rolled-back clock cannot revive the never-activated session.
            third.clock.nowEpochMillis = NOW
            third.harness.sync.syncNow()
            advanceUntilIdle()

            assertIs<LocalSessionStatus.Inactive>(third.read())
            assertTrue(session in third.harness.snapshot().terminalExpiryFacts)
        } finally {
            first.close()
            second.close()
            third.close()
        }
    }

    private fun sessionPeer(
        dispatcher: CoroutineDispatcher,
        databaseName: String,
        mailbox: SharedFakeMailboxPort,
        wallClock: SyncWallClock,
        frozen: FrozenStartSet,
    ): SessionPeer {
        val harness = AppleSyncTestHarness(
            dispatcher,
            databaseName,
            mailboxPort = mailbox,
            wallClock = wallClock,
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = databaseName.hashCode()),
        )
        val sessions = SqlLocalSessionStore(harness.database, dispatcher)
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val clock = FakeSessionClock(NOW)
        val owner = SessionTransitionOwner(
            backgroundDispatcher = dispatcher,
            store = sessions,
            clock = clock,
            enforcement = enforcement,
            loadTargets = { targetsFor(frozen) },
            triggers = harness.sync.sessionTriggers,
        )
        harness.sync.sessionObserver = owner
        return SessionPeer(harness, sessions, enforcement, clock, owner, frozen, dispatcher)
    }

    private suspend fun TestScope.exchange(
        first: SessionPeer,
        second: SessionPeer,
    ) {
        first.harness.sync.syncNow()
        advanceUntilIdle()
        second.harness.sync.syncNow()
        advanceUntilIdle()
    }

    private suspend fun SessionPeer.establish() {
        harness.establish()
        harness.sync.onForeground()
    }

    private suspend fun TestScope.start(
        peer: SessionPeer,
        sessionId: SessionId,
        endEpochMillis: Long = NOW + DURATION,
    ) {
        val result = peer.owner.startSession(sessionId, peer.clock.nowEpochMillis, endEpochMillis, peer.frozen)
        testScheduler.runCurrent()
        advanceUntilIdle()
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
    }

    private suspend fun TestScope.end(
        peer: SessionPeer,
        sessionId: SessionId,
    ) {
        val result = peer.owner.endEarly(sessionId)
        testScheduler.runCurrent()
        advanceUntilIdle()
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
    }

    private suspend fun SessionPeer.read(): LocalSessionStatus {
        return assertIs<LocalSessionResult.Success<LocalSessionStatus>>(sessions.read(clock.nowEpochMillis)).value
    }

    private suspend fun SessionPeer.sessionIntents(): List<SequencedSessionIntent> {
        return assertIs<LocalSessionResult.Success<List<SequencedSessionIntent>>>(sessions.readIntents()).value
    }

    private suspend fun sessionStartOperations(
        peer: SessionPeer,
        sessionId: SessionId,
    ): Int {
        return peer.harness.snapshot().acceptedBundles.values.count { stored ->
            val payload = stored.operation.payload
            payload is SyncOperationPayload.SessionStart && payload.sessionId == sessionId
        }
    }

    private suspend fun sessionEndOperations(
        peer: SessionPeer,
        sessionId: SessionId,
    ): Int {
        return peer.harness.snapshot().acceptedBundles.values.count { stored ->
            val payload = stored.operation.payload
            payload is SyncOperationPayload.SessionEnd && payload.sessionId == sessionId
        }
    }

    private suspend fun SessionPeer.conflicted(): Set<SessionId> {
        return harness.snapshot().let { snapshot ->
            SyncReducer.reduce(
                snapshot.acceptedBundles.values.map { stored -> stored.operation },
            ).conflictedSessionIds
        }
    }

    private fun SessionPeer.status(): SyncStatus {
        return harness.sync.state.value.status
    }

    private suspend fun SessionPeer.close() {
        owner.close()
        harness.close()
    }

    private suspend fun SessionPeer.reopenOwner() {
        owner.close()
        owner = SessionTransitionOwner(
            backgroundDispatcher = dispatcher,
            store = sessions,
            clock = clock,
            enforcement = enforcement,
            loadTargets = { targetsFor(frozen) },
            triggers = harness.sync.sessionTriggers,
        )
        harness.sync.sessionObserver = owner
    }

    private fun targetsFor(frozen: FrozenStartSet): SessionTargetsState {
        val policy = TargetPolicy.fromStoredValues(frozen.domains, null)
        val validated = assertIs<TargetPolicyValidationResult.Success>(policy).policy
        return SessionTargetsState(validated, LocalApplicationMappingsLoadResult.Unavailable())
    }

    private class SessionPeer(
        val harness: AppleSyncTestHarness,
        val sessions: SqlLocalSessionStore,
        val enforcement: FakeEnforcementPort,
        val clock: FakeSessionClock,
        var owner: SessionTransitionOwner,
        val frozen: FrozenStartSet,
        val dispatcher: CoroutineDispatcher,
    )

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
        const val DURATION: Long = 30 * 60_000L
        const val MIN_DURATION: Long = SessionLimits.MIN_DURATION_MILLIS
        val FROZEN_FIRST: FrozenStartSet = FrozenStartSet(persistentListOf("first.example"), null)
        val FROZEN_SECOND: FrozenStartSet = FrozenStartSet(persistentListOf("second.example"), null)
        val FROZEN_THIRD: FrozenStartSet = FrozenStartSet(persistentListOf("third.example"), null)
    }
}
