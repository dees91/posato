package app.posato.feature.sync.bootstrap

import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SequencedSessionIntent
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.ui.FakeEnforcementPort
import app.posato.feature.session.ui.SessionTargetsState
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncReducer
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalPolicyTestDatabase
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertIs

internal fun sessionPeer(
    dispatcher: CoroutineDispatcher,
    databaseName: String,
    mailbox: SharedFakeMailboxPort,
    wallClock: SyncWallClock,
    frozen: FrozenStartSet,
    database: LocalPolicyTestDatabase = createLocalPolicyTestDatabase(databaseName),
): SessionPeer {
    val harness = AppleSyncTestHarness(
        dispatcher,
        databaseName,
        mailboxPort = mailbox,
        wallClock = wallClock,
        cryptoProvider = FakeSyncCryptoProvider(streamSeed = databaseName.hashCode()),
        testDatabase = database,
    )
    val sessions = SqlLocalSessionStore(harness.database, dispatcher)
    val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
    val clock = FakeSessionClock(SESSION_NOW)
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

internal suspend fun TestScope.exchange(
    first: SessionPeer,
    second: SessionPeer,
) {
    first.harness.sync.syncNow()
    advanceUntilIdle()
    second.harness.sync.syncNow()
    advanceUntilIdle()
}

internal suspend fun SessionPeer.establish() {
    harness.establish()
    harness.sync.onForeground()
}

internal suspend fun TestScope.start(
    peer: SessionPeer,
    sessionId: SessionId,
    endEpochMillis: Long = SESSION_NOW + SESSION_DURATION,
) {
    val result = peer.owner.startSession(sessionId, peer.clock.nowEpochMillis, endEpochMillis, peer.frozen)
    testScheduler.runCurrent()
    advanceUntilIdle()
    assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
}

internal suspend fun TestScope.end(
    peer: SessionPeer,
    sessionId: SessionId,
) {
    val result = peer.owner.endEarly(sessionId)
    testScheduler.runCurrent()
    advanceUntilIdle()
    assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
}

internal suspend fun SessionPeer.read(): LocalSessionStatus {
    return assertIs<LocalSessionResult.Success<LocalSessionStatus>>(sessions.read(clock.nowEpochMillis)).value
}

internal suspend fun retainedMarkers(peer: SessionPeer): Set<SessionId> {
    return assertIs<LocalSessionResult.Success<Set<SessionId>>>(peer.sessions.retainedExpiryMarkers()).value
}

internal suspend fun SessionPeer.sessionIntents(): List<SequencedSessionIntent> {
    return assertIs<LocalSessionResult.Success<List<SequencedSessionIntent>>>(sessions.readIntents()).value
}

internal suspend fun sessionStartOperations(
    peer: SessionPeer,
    sessionId: SessionId,
): Int {
    return peer.harness.snapshot().acceptedBundles.values.count { stored ->
        val payload = stored.operation.payload
        payload is SyncOperationPayload.SessionStart && payload.sessionId == sessionId
    }
}

internal suspend fun sessionEndOperations(
    peer: SessionPeer,
    sessionId: SessionId,
): Int {
    return peer.harness.snapshot().acceptedBundles.values.count { stored ->
        val payload = stored.operation.payload
        payload is SyncOperationPayload.SessionEnd && payload.sessionId == sessionId
    }
}

internal suspend fun SessionPeer.conflicted(): Set<SessionId> {
    return harness.snapshot().let { snapshot ->
        SyncReducer.reduce(
            snapshot.acceptedBundles.values.map { stored -> stored.operation },
        ).conflictedSessionIds
    }
}

internal fun SessionPeer.status(): SyncStatus {
    return harness.sync.state.value.status
}

internal suspend fun SessionPeer.close() {
    owner.close()
    harness.close()
}

internal suspend fun SessionPeer.reopenOwner() {
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

internal fun targetsFor(frozen: FrozenStartSet): SessionTargetsState {
    val policy = TargetPolicy.fromStoredValues(frozen.domains, null)
    val validated = assertIs<TargetPolicyValidationResult.Success>(policy).policy
    return SessionTargetsState(validated, LocalApplicationMappingsLoadResult.Unavailable())
}

internal class SessionPeer(
    val harness: AppleSyncTestHarness,
    val sessions: SqlLocalSessionStore,
    val enforcement: FakeEnforcementPort,
    val clock: FakeSessionClock,
    var owner: SessionTransitionOwner,
    val frozen: FrozenStartSet,
    val dispatcher: CoroutineDispatcher,
)

internal const val SESSION_NOW: Long = 1_000_000_000_000L
internal const val SESSION_DURATION: Long = 30 * 60_000L
internal const val SESSION_MIN_DURATION: Long = SessionLimits.MIN_DURATION_MILLIS
internal val SESSION_FROZEN_FIRST: FrozenStartSet = FrozenStartSet(persistentListOf("first.example"), null)
internal val SESSION_FROZEN_SECOND: FrozenStartSet = FrozenStartSet(persistentListOf("second.example"), null)
internal val SESSION_FROZEN_THIRD: FrozenStartSet = FrozenStartSet(persistentListOf("third.example"), null)
