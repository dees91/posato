package app.posato.feature.session.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.ExpiryDisplacement
import app.posato.feature.enforcement.ExpiryDisplacementOutcome
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.data.LocalSessionFailure
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionDisplacementRecoveryTest {
    @Test
    fun `given an unreadable native signal when starting or retrying then scheduling waits for a confirmed read`() = runTest {
        for (throwOnRead in listOf(false, true)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = FakeLocalSessionStore()
            val delegate = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
            var failing = true
            val port = object : EnforcementPort by delegate {
                override suspend fun displacedSuspendedExpiry(currentSessionId: String): ExpiryDisplacement {
                    if (!failing) return delegate.displacedSuspendedExpiry(currentSessionId)
                    if (throwOnRead) error("synthetic read failure")
                    return ExpiryDisplacement(ExpiryDisplacementOutcome.FAILED, null)
                }
            }
            val owner = SessionTransitionOwner(dispatcher, store, FakeSessionClock(NOW), port, ::targets, FakeSessionSyncTriggers())
            try {
                owner.startSession(CURRENT, NOW, END, FROZEN)
                runCurrent()
                assertTrue("apply" !in delegate.calls)
                assertEquals(EnforcementActionKind.APPLY_FAILED, assertIs<EnforcementState.ActionRequired>(owner.view.value.state).kind)
                owner.retry()
                runCurrent()
                assertTrue("apply" !in delegate.calls)
                failing = false
                owner.retry()
                runCurrent()
                assertEquals(CURRENT.reconciliationId(), delegate.lastRequest?.sessionId)
            } finally {
                owner.close()
            }
        }
    }

    @Test
    fun `given a failed retain when restarting then native expiry is banked in SQL before replacement is scheduled`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database = createLocalPolicyTestDatabase("displacement-recovery.db")
        var driver = database.openDriver()
        var sql = SqlLocalSessionStore(PosatoDatabase(driver), dispatcher)
        val failed = object : LocalSessionSyncStore by sql {
            override suspend fun retainExpiryMarker(sessionId: SessionId): LocalSessionResult<Unit> {
                return LocalSessionResult.Failure(LocalSessionFailure.STORAGE_FAILURE)
            }
        }
        val port = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        port.displacedSessionId = OLD.reconciliationId()
        var owner = SessionTransitionOwner(dispatcher, failed, FakeSessionClock(NOW), port, ::targets, FakeSessionSyncTriggers())
        try {
            owner.startSession(CURRENT, NOW, END, FROZEN)
            runCurrent()
            assertTrue("apply" !in port.calls)
            assertTrue(port.acknowledgedSessionIds.isEmpty())
            owner.close()
            driver.close()
            driver = database.openDriver()
            sql = SqlLocalSessionStore(PosatoDatabase(driver), dispatcher)
            owner = SessionTransitionOwner(dispatcher, sql, FakeSessionClock(NOW), port, ::targets, FakeSessionSyncTriggers())
            owner.settle(assertIs<LocalSessionResult.Success<LocalSessionStatus>>(sql.read(NOW)).value)
            runCurrent()
            assertTrue(OLD in assertIs<LocalSessionResult.Success<Set<SessionId>>>(sql.retainedExpiryMarkers()).value)
            assertTrue(port.calls.indexOf("acknowledge") < port.calls.indexOf("apply"))
            assertEquals(CURRENT.reconciliationId(), port.lastRequest?.sessionId)
            owner.close()
            driver.close()
            driver = database.openDriver()
            sql = SqlLocalSessionStore(PosatoDatabase(driver), dispatcher)
            assertTrue(OLD in assertIs<LocalSessionResult.Success<Set<SessionId>>>(sql.retainedExpiryMarkers()).value)
        } finally {
            owner.close()
            driver.close()
            database.delete()
        }
    }

    @Test
    fun `given a current native expiry after clock rollback then retry banks it instead of rescheduling`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeLocalSessionStore()
        val port = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val owner = SessionTransitionOwner(dispatcher, store, FakeSessionClock(NOW), port, ::targets, FakeSessionSyncTriggers())
        try {
            owner.startSession(CURRENT, NOW, END, FROZEN)
            runCurrent()
            port.expiredSessionIds = setOf(CURRENT.reconciliationId())
            owner.retry()
            runCurrent()
            assertEquals(1, port.calls.count { it == "apply" })
            assertIs<LocalSessionStatus.Ended>(assertIs<LocalSessionResult.Success<LocalSessionStatus>>(store.read(NOW)).value)
            assertTrue(CURRENT in store.retainedMarkers)
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
