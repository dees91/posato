package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.SyncTargetPolicyStore
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppleSyncQueueTest {
    @Test
    fun `given overlapping saves when the first committed result is delayed then domain mutations retain commit order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        try {
            harness.establish()
            val raw = GatedPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), afterReplace = { gate.await() })
            val local = SyncTargetPolicyStore(raw, harness.sync)
            val adding = async { local.replace(0, testPolicy("ordered.example")) }
            runCurrent()
            val removing = async { local.replace(1, testPolicy()) }
            runCurrent()
            assertEquals(1, raw.replacements)
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(adding.await())
            assertEquals(2, assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(removing.await()).value.revision)
            val operations = harness.snapshot().acceptedBundles.values.map { it.operation }.sortedBy { it.authorSequence }
            assertEquals(3, operations.size)
            assertIs<SyncOperationPayload.DomainPresent>(operations[1].payload)
            assertIs<SyncOperationPayload.DomainAbsent>(operations[2].payload)
        } finally {
            gate.complete(Unit)
            advanceUntilIdle()
            harness.close()
        }
    }

    @Test
    fun `given a caller cancelled after local commit when the result returns then the process still authors the change`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        try {
            harness.establish()
            val raw = GatedPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), afterReplace = { gate.await() })
            val local = SyncTargetPolicyStore(raw, harness.sync)
            val saving = async { local.replace(0, testPolicy("cancelled.example")) }
            runCurrent()
            assertEquals(1, assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(raw.read()).value.revision)
            saving.cancel()
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(saving.isCancelled)
            assertEquals(2, harness.snapshot().acceptedBundles.size)
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
        } finally {
            gate.complete(Unit)
            advanceUntilIdle()
            harness.close()
        }
    }

    @Test
    fun `given an unlinked edit when consent finishes before its local commit then the edit is not backfilled`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        try {
            val raw = GatedPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), beforeReplace = { gate.await() })
            val local = SyncTargetPolicyStore(raw, harness.sync)
            val saving = async { local.replace(0, testPolicy("pre-link.example")) }
            runCurrent()
            assertEquals(1, raw.replacements)
            harness.establish()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(saving.await())
            assertEquals(0, harness.account.calls)
            harness.sync.onForeground()
            advanceUntilIdle()
            assertTrue(harness.snapshot().acceptedBundles.isEmpty())
        } finally {
            gate.complete(Unit)
            advanceUntilIdle()
            harness.close()
        }
    }

    @Test
    fun `given a captured old workspace when removal and relinking precede handoff then the new workspace receives no old edit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        try {
            harness.establish()
            val oldWorkspace = harness.sync.captureWorkspace()
            val raw = GatedPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), afterReplace = { gate.await() })
            val local = SyncTargetPolicyStore(raw, harness.sync)
            val saving = async { local.replace(0, testPolicy("old-workspace.example")) }
            runCurrent()
            harness.sync.removeWorkspace()
            harness.cloud.zoneExists = false
            harness.cloud.storedAnchor = null
            harness.sync.syncWithIcloud()
            advanceUntilIdle()
            assertNotEquals(oldWorkspace, harness.sync.captureWorkspace())
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(saving.await())
            assertTrue(harness.database.syncReplicaQueries.selectAcceptedBundles().executeAsList().isEmpty())
            assertTrue(harness.mailbox.saved.isEmpty())
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
        } finally {
            gate.complete(Unit)
            advanceUntilIdle()
            harness.close()
        }
    }
}

private class GatedPolicyStore(
    private val local: LocalTargetPolicyStore,
    private val beforeReplace: suspend () -> Unit = {},
    private val afterReplace: suspend () -> Unit = {},
) : LocalTargetPolicyStore by local {
    var replacements = 0
        private set

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy
    ): LocalPolicyResult<LocalTargetPolicyState> {
        replacements += 1
        beforeReplace()
        val result = local.replace(expectedRevision, policy)
        afterReplace()
        return result
    }
}
