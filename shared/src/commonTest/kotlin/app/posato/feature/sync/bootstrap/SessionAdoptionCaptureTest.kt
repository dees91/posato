package app.posato.feature.sync.bootstrap

import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.OperationOrder
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SessionCandidate
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.sync.domain.SynchronizedPauseSet
import app.posato.feature.sync.domain.SynchronizedSessionStart
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * What an adopted session freezes when its set comes from the workspace. Failure mode: the workspace bounds a set by
 * the synchronized limit (2,048), above the local limit (1,024) that a stored frozen set must meet, so an oversized
 * set would leave the session store unreadable. Two-peer exchanges cannot reach these bounds, because a peer stops at
 * the unknown-author bundle bound first, so the reconciler is tested with a hand-built projection.
 */
class SessionAdoptionCaptureTest {
    @Test
    fun `given a workspace set over the local limit when adopted then the store stays readable with the local capture`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("adoption-oversized-set.db")
        val driver = testDatabase.openDriver()
        try {
            val sessions = SqlLocalSessionStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
            val oversized = (1..ExactDomainPolicyLimits.MAX_DOMAIN_COUNT + 1).map { index -> checkNotNull(ExactDomain.restore("site$index.example")) }
            val start = sessionStart(SessionId(testIdentifier(71)))
            val local = FrozenStartSet(persistentListOf("local.example"), 2)

            val result = SessionProjectionReconciler(sessions).apply(
                bankExpiry = { true },
                projection = projection(SynchronizedPauseSet(PauseSetId.FIRST, null, oversized)),
                candidate = SessionCandidate.Current(start),
                pendingIds = emptySet(),
                retained = emptySet(),
                status = LocalSessionStatus.Inactive,
                nowEpochMillis = NOW,
                captureFrozen = { local },
            )

            assertIs<SessionReconcileResult.Completed>(result)
            val read = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(sessions.read(NOW))
            assertEquals(local, assertIs<LocalSessionStatus.Active>(read.value).frozenStartSet)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a workspace set within the local limit when adopted then it freezes its websites and this device's apps`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("adoption-workspace-set.db")
        val driver = testDatabase.openDriver()
        try {
            val sessions = SqlLocalSessionStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
            val start = sessionStart(SessionId(testIdentifier(72)))
            val workspaceSet = SynchronizedPauseSet(PauseSetId.FIRST, null, listOf(checkNotNull(ExactDomain.restore("workspace.example"))))

            SessionProjectionReconciler(sessions).apply(
                bankExpiry = { true },
                projection = projection(workspaceSet),
                candidate = SessionCandidate.Current(start),
                pendingIds = emptySet(),
                retained = emptySet(),
                status = LocalSessionStatus.Inactive,
                nowEpochMillis = NOW,
                captureFrozen = { FrozenStartSet(persistentListOf("local.example"), 2) },
            )

            val read = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(sessions.read(NOW))
            val adopted = assertIs<LocalSessionStatus.Active>(read.value)
            assertEquals(FrozenStartSet(persistentListOf("workspace.example"), 2), adopted.frozenStartSet)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private fun sessionStart(sessionId: SessionId): SynchronizedSessionStart {
        val operationId = BundleId(testIdentifier(90))
        return SynchronizedSessionStart(
            operationId = operationId,
            sessionId = sessionId,
            startEpochMillis = NOW - 1_000L,
            mandatoryEndEpochMillis = NOW + 30 * 60_000L,
            order = OperationOrder(HybridLogicalClock(NOW, 0), AuthorId(testIdentifier(91)), operationId),
            isEnded = false,
        )
    }

    private fun projection(set: SynchronizedPauseSet): SyncProjection {
        return SyncProjection(
            applicationPolicyName = null,
            eligibleSessionStarts = emptyList(),
            conflictedSessionIds = emptySet(),
            audit = emptyList(),
            pauseSets = listOf(set),
            pauseSetsEnabled = true,
        )
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
    }
}
