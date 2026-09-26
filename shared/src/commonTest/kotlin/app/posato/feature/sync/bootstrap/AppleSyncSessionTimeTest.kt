package app.posato.feature.sync.bootstrap

import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncReducer
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleSyncSessionTimeTest {
    @Test
    fun `given a future start when the host clock advances without a screen then it applies and expires locally`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-host-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-host-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW - 60_000L
            val session = SessionId(testIdentifier(93))
            start(first, session)
            exchange(first, second)
            val fetchCount = mailbox.cursors.size
            val hosted = backgroundScope.launch { second.owner.runWhileHosted() }
            runCurrent()
            second.clock.nowEpochMillis = SESSION_NOW
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(session, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
            assertEquals(session.reconciliationId(), second.enforcement.lastRequest?.sessionId)
            assertEquals(fetchCount, mailbox.cursors.size)
            second.clock.nowEpochMillis = SESSION_NOW + SESSION_DURATION
            advanceTimeBy(1_000)
            runCurrent()
            assertIs<LocalSessionStatus.Ended>(second.read())
            assertIs<app.posato.feature.enforcement.EnforcementState.Inactive>(second.owner.view.value.state)
            second.clock.nowEpochMillis = SESSION_NOW
            advanceTimeBy(1_000)
            runCurrent()
            assertIs<LocalSessionStatus.Ended>(second.read())
            second.owner.close()
            runCurrent()
            val calls = second.enforcement.calls.toList()
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(calls, second.enforcement.calls)
            assertTrue(hosted.isCompleted)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given idle waiting and an accepted future start when its start arrives then it applies on time`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-idle-future-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-idle-future-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW - 5_000L
            val session = SessionId(testIdentifier(95))
            start(first, session)
            exchange(first, second)
            backgroundScope.launch { second.owner.runWhileHosted(idleRecheckMillis = 60_000L) }
            runCurrent()
            assertIs<LocalSessionStatus.Inactive>(second.read())

            second.clock.nowEpochMillis = SESSION_NOW
            advanceTimeBy(5_001)
            runCurrent()
            assertEquals(session, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given idle waiting when an exchange brings a future start then it applies on time`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-idle-exchange-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-idle-exchange-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW - 5_000L
            backgroundScope.launch { second.owner.runWhileHosted(idleRecheckMillis = 60_000L) }
            runCurrent()
            val session = SessionId(testIdentifier(97))
            start(first, session)
            exchange(first, second)
            assertIs<LocalSessionStatus.Inactive>(second.read())

            second.clock.nowEpochMillis = SESSION_NOW
            advanceTimeBy(5_001)
            runCurrent()
            assertEquals(session, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given an accepted future start when the core reopens then local restore needs no mailbox exchange`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-future-reopen-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val database = createLocalPolicyTestDatabase("session-future-reopen-second.db")
        var second = sessionPeer(dispatcher, "session-future-reopen-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND, database)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW - 60_000L
            val session = SessionId(testIdentifier(94))
            start(first, session)
            exchange(first, second)
            val fetchCount = mailbox.cursors.size
            second.owner.close()
            second.harness.closeKeepingDatabase()
            second = sessionPeer(dispatcher, "session-future-reopen-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND, database)
            second.harness.deliverJoinKey()
            second.harness.sync.sessionTriggers.restoreSessions()
            advanceUntilIdle()
            second.owner.onTick(SESSION_NOW)
            runCurrent()
            assertEquals(session, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
            assertEquals(fetchCount, mailbox.cursors.size)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a cached future start when workspace removal succeeds then later ticks cannot adopt it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-future-remove-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-future-remove-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW - 60_000L
            start(first, SessionId(testIdentifier(95)))
            exchange(first, second)
            second.harness.sync.removeWorkspace()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW
            second.owner.onTick(SESSION_NOW)
            runCurrent()
            assertIs<LocalSessionStatus.Inactive>(second.read())
            assertTrue("apply" !in second.enforcement.calls)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a future start during a suspended exchange then local eligibility does not wait for the mailbox`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-held-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-held-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW - 60_000L
            val session = SessionId(testIdentifier(96))
            start(first, session)
            exchange(first, second)
            mailbox.beforeFetch = { gate.await() }
            second.harness.sync.syncNow()
            runCurrent()
            second.clock.nowEpochMillis = SESSION_NOW
            second.owner.onTick(SESSION_NOW)
            runCurrent()
            assertEquals(session, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
            assertEquals(session.reconciliationId(), second.enforcement.lastRequest?.sessionId)
        } finally {
            gate.complete(Unit)
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a pending local command when a cached remote start becomes eligible then the local intent stays authoritative`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-pending-time-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-pending-time-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            second.clock.nowEpochMillis = SESSION_NOW - 60_000L
            start(first, SessionId(testIdentifier(97)))
            exchange(first, second)
            val local = SessionId(testIdentifier(98))
            val workspaceId = app.posato.feature.sync.testContext.workspaceId.value.copyBytes()
            second.sessions.start(local, SESSION_NOW, SESSION_NOW + SESSION_DURATION, SESSION_NOW, SESSION_FROZEN_SECOND, workspaceId)
            second.clock.nowEpochMillis = SESSION_NOW
            second.owner.onTick(SESSION_NOW)
            runCurrent()
            assertEquals(local, assertIs<LocalSessionStatus.Active>(second.read()).record.sessionId)
            assertEquals(1, second.sessionIntents().size)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given multiple future starts then local time selects each eligible winner without another exchange`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-future-many-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-future-many-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        val receiver = sessionPeer(dispatcher, "session-future-many-receiver.db", mailbox, SyncWallClock { 300 }, SESSION_FROZEN_THIRD)
        try {
            first.establish()
            second.establish()
            receiver.establish()
            advanceUntilIdle()
            receiver.clock.nowEpochMillis = SESSION_NOW - 60_000L
            val early = SessionId(testIdentifier(99))
            val later = SessionId(testIdentifier(100))
            start(first, early)
            second.clock.nowEpochMillis = SESSION_NOW + 60_000L
            start(second, later)
            exchange(first, receiver)
            exchange(second, receiver)
            val fetchCount = mailbox.cursors.size
            receiver.clock.nowEpochMillis = SESSION_NOW
            receiver.owner.onTick(SESSION_NOW)
            runCurrent()
            assertEquals(early, assertIs<LocalSessionStatus.Active>(receiver.read()).record.sessionId)
            receiver.clock.nowEpochMillis = SESSION_NOW + 60_000L
            receiver.owner.onTick(receiver.clock.nowEpochMillis)
            runCurrent()
            assertEquals(later, assertIs<LocalSessionStatus.Active>(receiver.read()).record.sessionId)
            assertEquals(later.reconciliationId(), receiver.enforcement.lastRequest?.sessionId)
            assertEquals(fetchCount, mailbox.cursors.size)
        } finally {
            first.close()
            second.close()
            receiver.close()
        }
    }

    @Test
    fun `given a failed terminal bank during local evaluation then retry cannot apply the concluded identity`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = sessionPeer(dispatcher, "session-bank-time-first.db", mailbox, SyncWallClock { 100 }, SESSION_FROZEN_FIRST)
        val second = sessionPeer(dispatcher, "session-bank-time-second.db", mailbox, SyncWallClock { 200 }, SESSION_FROZEN_SECOND)
        try {
            first.establish()
            second.establish()
            advanceUntilIdle()
            val session = SessionId(testIdentifier(101))
            start(first, session)
            exchange(first, second)
            val stored = second.harness.snapshot()
            var failBank = true
            val store = object : app.posato.feature.session.data.LocalSessionSyncStore by second.sessions {
                override suspend fun retainExpiryMarker(sessionId: SessionId): LocalSessionResult<Unit> {
                    return if (failBank) {
                        LocalSessionResult.Failure(app.posato.feature.session.data.LocalSessionFailure.STORAGE_FAILURE)
                    } else {
                        second.sessions.retainExpiryMarker(sessionId)
                    }
                }
            }
            second.owner.close()
            second.enforcement.calls.clear()
            second.owner = SessionTransitionOwner(
                dispatcher,
                store,
                second.clock,
                second.enforcement,
                { targetsFor(SESSION_FROZEN_SECOND) },
                second.harness.sync.sessionTriggers,
            )
            second.owner.onReplicaSnapshot(
                app.posato.feature.sync.domain.SessionReplicaSnapshot(
                    stored.context,
                    stored.revision + 1,
                    SyncReducer.reduce(stored.acceptedBundles.values.map { it.operation }),
                    setOf(session),
                ),
            )
            second.owner.onTick(SESSION_NOW)
            runCurrent()
            second.owner.retry()
            runCurrent()
            assertTrue("apply" !in second.enforcement.calls)
            assertIs<app.posato.feature.enforcement.EnforcementState.ActionRequired>(second.owner.view.value.state)
            failBank = false
            second.owner.onTick(SESSION_NOW)
            runCurrent()
            assertIs<LocalSessionStatus.Ended>(second.read())
            assertTrue("apply" !in second.enforcement.calls)
        } finally {
            first.close()
            second.close()
        }
    }
}
