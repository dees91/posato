package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.SyncTargetPolicyStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleSyncAuthoringTest {
    @Test
    fun `given a missing workspace key when saving then local success retains a waiting outcome without backfill`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            harness.establish()
            harness.keys.scriptRead(KeyItemReadResult.Missing)
            val local = SyncTargetPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), harness.sync)
            val saved = local.replace(0, testPolicy("waiting.example"))
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(saved)
            advanceUntilIdle()
            assertEquals(saved, local.read())
            assertEquals(SyncStatus.WAITING_FOR_KEY, harness.sync.state.value.status)
            assertTrue(harness.sync.state.value.linked)
            assertTrue(harness.mailbox.cursors.isEmpty())
            harness.sync.syncNow()
            advanceUntilIdle()
            assertTrue(harness.snapshot().acceptedBundles.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an outbox commit failure when saving then local success and action required survive the worker pass`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            val fetches = harness.mailbox.cursors.size
            harness.driver.execute(
                null,
                "CREATE TRIGGER fail_authoring BEFORE INSERT ON sync_pending_bundle BEGIN SELECT RAISE(ABORT, 'synthetic'); END",
                0,
            )
            val local = SyncTargetPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), harness.sync)
            val saved = local.replace(0, testPolicy("kept.example"))
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(saved)
            advanceUntilIdle()
            assertEquals(saved, local.read())
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
            assertEquals(fetches, harness.mailbox.cursors.size)
            assertTrue(harness.snapshot().acceptedBundles.isEmpty())
            harness.driver.execute(null, "DROP TRIGGER fail_authoring", 0)
            harness.sync.syncNow()
            advanceUntilIdle()
            assertTrue(harness.snapshot().acceptedBundles.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a linked device before its first exchange when saving then the writer authors the change`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        try {
            harness.establish()
            harness.mailbox.saveResult = BundleSaveResult.UnknownOutcome
            val local = SyncTargetPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), harness.sync)
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(local.replace(0, testPolicy("first.example")))
            advanceUntilIdle()
            assertEquals(2, harness.snapshot().pendingBundles.size)
            assertEquals(1, harness.keys.readCalls)
            assertEquals(SyncStatus.RETRYABLE, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a blocked fetch when saving then local success returns before the network finishes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        try {
            harness.establish()
            harness.mailbox.beforeFetch = { gate.await() }
            harness.sync.onForeground()
            runCurrent()
            assertEquals(1, harness.mailbox.cursors.size)
            val local = SyncTargetPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), harness.sync)
            val saved = async { local.replace(0, testPolicy("during-fetch.example")) }
            runCurrent()
            assertTrue(saved.isCompleted)
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(saved.await())
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, harness.snapshot().acceptedBundles.size)
        } finally {
            gate.complete(Unit)
            advanceUntilIdle()
            harness.close()
        }
    }

    @Test
    fun `given a blocked established check when saving then local success returns before the network finishes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher)
        val gate = CompletableDeferred<Unit>()
        try {
            harness.establish()
            harness.cloud.beforeZoneFetch = { gate.await() }
            harness.sync.onForeground()
            runCurrent()
            assertEquals(1, harness.cloud.zoneFetchCalls)
            val local = SyncTargetPolicyStore(SqlLocalTargetPolicyStore(harness.database, dispatcher), harness.sync)
            val saved = async { local.replace(0, testPolicy("during-check.example")) }
            runCurrent()
            assertTrue(saved.isCompleted)
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(saved.await())
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, harness.snapshot().acceptedBundles.size)
        } finally {
            gate.complete(Unit)
            advanceUntilIdle()
            harness.close()
        }
    }
}
