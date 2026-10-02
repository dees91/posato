package app.posato.feature.sync.bootstrap

import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.StoredPolicyIntent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What a migrated replica authors on its own: one kind 19 per workspace, and a set removal only after schedules left it. */
class AppleSyncPauseSetsTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))

    @Test
    fun `given a linked workspace when several passes run then pause sets enabled is authored exactly once`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), name = "pause-sets-enabled.db", pauseSetsAlreadyEnabled = false)
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            harness.sync.syncNow()
            advanceUntilIdle()
            harness.sync.syncNow()
            advanceUntilIdle()

            assertEquals(1, harness.authored().count { operation -> operation.payload == SyncOperationPayload.PauseSetsEnabled })
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a schedule moved off a set and the set removed when a pass runs then the move is authored before the removal`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), name = "pause-sets-removal.db", withSchedules = true)
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            val workspaceId = checkNotNull(
                assertIs<BootstrapStoreResult.Success<EstablishedWorkspace?>>(harness.sync.captureWorkspace()).value,
            ).context.workspaceId.value.copyBytes()
            val moved = SchedulePlan(ScheduleId(ScheduleSyncId(testIdentifier(70)).hex), "Focus", 1, 540, 600, true, PauseSetId.FIRST)
            harness.sqlPolicy.recordIntents(PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.RemoveSet(work))))
            harness.schedules.save(moved, workspaceId)
            harness.sync.syncNow()
            advanceUntilIdle()

            val authored = harness.authored().sortedBy(SyncOperation::authorSequence).map(SyncOperation::payload)
            val move = authored.indexOfFirst { payload -> payload is SyncOperationPayload.SchedulePut }
            val removal = authored.indexOfFirst { payload -> payload == SyncOperationPayload.PauseSetRemove(work) }
            assertTrue(move >= 0 && removal > move, "move $move removal $removal")
            assertIs<LocalPolicyResult.Success<*>>(harness.sqlPolicy.readIntents())
        } finally {
            harness.close()
        }
    }

    private suspend fun AppleSyncTestHarness.authored(): List<SyncOperation> {
        return snapshot().acceptedBundles.values.map { stored -> stored.operation }
    }
}
