package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.SyncTargetPolicyStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleSyncPersistenceTest {
    @Test
    fun `given a failed final local clear then all sync tables roll back and retry completes`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            harness.mailbox.saveResult = BundleSaveResult.UnknownOutcome
            harness.sync.recordDomainChanges(testPolicy(), testPolicy("pending.example"))
            advanceUntilIdle()
            val before = harness.snapshot()
            val bootstrapBefore = harness.store.read()
            harness.driver.execute(
                null,
                "CREATE TRIGGER fail_clear BEFORE DELETE ON sync_bootstrap_state BEGIN SELECT RAISE(ABORT, 'synthetic'); END",
                0,
            )
            harness.sync.removeWorkspace()
            assertEquals(before, harness.snapshot())
            assertEquals(bootstrapBefore, harness.store.read())
            harness.driver.execute(null, "DROP TRIGGER fail_clear", 0)
            harness.cloud.zoneExists = false
            harness.sync.removeWorkspace()
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertTrue(harness.database.syncReplicaQueries.selectPendingBundles().executeAsList().isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given reordered and duplicate remote bundles when fetched then only the replica changes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = AppleSyncTestHarness(dispatcher, "sync-source.db")
        val destination = AppleSyncTestHarness(dispatcher, "sync-destination.db")
        try {
            source.establish()
            source.sync.onForeground()
            advanceUntilIdle()
            source.sync.recordDomainChanges(testPolicy(), testPolicy("one.example"))
            advanceUntilIdle()
            val bundles = source.mailbox.saved
            destination.establish()
            destination.mailbox.pages.add(ChangeFetchResult.Page(ChangePage(bundles[1], true, testCursor(2))))
            destination.mailbox.pages.add(ChangeFetchResult.Page(ChangePage(bundles[0], true, testCursor(3))))
            destination.mailbox.pages.add(ChangeFetchResult.Page(ChangePage(bundles[1], false, testCursor(4))))
            destination.sync.onForeground()
            advanceUntilIdle()
            assertEquals(source.snapshot().acceptedBundles, destination.snapshot().acceptedBundles)
            assertEquals(0, destination.snapshot().stagedBundles.size)
            assertContentEquals(byteArrayOf(4), destination.snapshot().transportProgress?.copyBytes())
            val local = SqlLocalTargetPolicyStore(destination.database, dispatcher).read()
            assertTrue(assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(local).value.policy.domains.isEmpty())
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun `given removal when a key deletion fails then retry clears sync atomically and keeps local domains`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            val local = SqlLocalTargetPolicyStore(harness.database, dispatcher)
            val before = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(local.read()).value
            val policy = testPolicy("kept.example")
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(local.replace(before.revision, policy))
            harness.mailbox.saveResult = BundleSaveResult.Retryable
            harness.sync.recordDomainChanges(testPolicy(), policy)
            advanceUntilIdle()
            val replicaBefore = harness.snapshot()
            harness.keys.scriptDelete(KeyItemDeleteResult.UnknownOutcome)
            harness.sync.removeWorkspace()
            assertEquals(replicaBefore, harness.snapshot())
            harness.cloud.zoneExists = false
            harness.sync.removeWorkspace()
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(policy, assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(local.read()).value.policy)
            assertTrue(harness.database.syncReplicaQueries.selectSyncReplicaState().executeAsList().isEmpty())
            assertTrue(harness.database.syncReplicaQueries.selectPendingBundles().executeAsList().isEmpty())
            assertTrue(harness.database.syncReplicaQueries.selectAcceptedBundles().executeAsList().isEmpty())
            assertEquals(1, harness.mailbox.deleteCalls)
            assertEquals(false, harness.sync.state.value.linked)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an unavailable writer when a local edit commits then local success and action required are preserved`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            harness.establish()
            val local = SyncTargetPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), harness.sync)
            val initial = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(local.read()).value
            val saved = local.replace(initial.revision, testPolicy("kept.example"))
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(saved)
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
            assertEquals(saved, local.read())
            assertEquals(0, harness.mailbox.saved.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a UI caller that cancels while removing then shared removal can finish`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        try {
            harness.establish()
            harness.mailbox.beforeFetch = { gate.await() }
            harness.sync.onForeground()
            runCurrent()
            val waiter = async { harness.sync.removeWorkspace() }
            runCurrent()
            waiter.cancelAndJoin()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given scoped workspace key use when it fails then the owned bytes are cleared`() {
        val key = checkNotNull(WorkspaceKeyValue.fromBytes(ByteArray(32) { 7 }))
        var owned: ByteArray? = null
        try {
            key.useAndClear {
                owned = it
                error("synthetic failure")
            }
        } catch (_: IllegalStateException) {
            assertContentEquals(ByteArray(32), owned)
            assertContentEquals(ByteArray(32), key.copyBytes())
        }
    }
}
