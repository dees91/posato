package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.ui.SyncBootstrapUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleSyncJoinTest {
    @Test
    fun `given adoption persisted before cancellation when retried then it is reconciled without another commit`() = runTest {
        for (conflicting in listOf(false, true)) {
            val store = FakeBootstrapStore()
            val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), bootstrapStore = store)
            try {
                harness.waitForKey()
                harness.deliverJoinKey()
                store.afterCommit = { throw CancellationException() }
                var cancelled = false
                try {
                    harness.sync.syncWithIcloud()
                } catch (_: CancellationException) {
                    cancelled = true
                }
                assertTrue(cancelled)
                assertFalse(harness.sync.state.value.checkingJoin)
                assertEquals(1, store.commitCalls)
                store.afterCommit = {}
                if (conflicting) {
                    store.state = BootstrapState.Established(EstablishedWorkspace(app.posato.feature.sync.testContext, bindingB))
                }
                harness.sync.syncWithIcloud()
                advanceUntilIdle()
                assertEquals(1, store.commitCalls)
                assertEquals(if (conflicting) 0 else 1, harness.mailbox.cursors.size)
                assertEquals(if (conflicting) SyncStatus.ACTION_REQUIRED else SyncStatus.COMPLETED, harness.sync.state.value.status)
                assertFalse(harness.sync.state.value.joinPending)
                assertFalse(harness.sync.state.value.checkingJoin)
                assertEquals(0, harness.cloud.zoneSaveCalls)
                assertEquals(0, harness.cloud.anchorCreateCalls)
                assertEquals(0, harness.keys.createCalls)
                assertEquals(0, harness.keys.deleteCalls)
            } finally {
                harness.close()
            }
        }
    }

    @Test
    fun `given retryable adoption storage failure when the workspace disappears then the UI retry cannot create one`() = runTest {
        val store = FakeBootstrapStore()
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), bootstrapStore = store)
        try {
            harness.waitForKey()
            harness.deliverJoinKey()
            store.writeFailure = BootstrapStoreFailure.STORAGE_FAILURE
            val holder = SyncBootstrapUiState(harness.sync, this)
            holder.sync()
            advanceUntilIdle()
            assertEquals(SyncStatus.RETRYABLE, holder.syncState.value.status)
            assertTrue(holder.syncState.value.joinPending)
            assertEquals(0, harness.mailbox.cursors.size)
            store.writeFailure = null
            harness.cloud.storedAnchor = null
            holder.sync()
            advanceUntilIdle()
            assertEquals(SyncStatus.LOCAL_ONLY, holder.syncState.value.status)
            assertFalse(holder.syncState.value.joinPending)
            assertEquals(1, store.commitCalls)
            assertEquals(0, harness.cloud.anchorCreateCalls)
            assertEquals(0, harness.keys.createCalls)
            assertEquals(0, harness.mailbox.cursors.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a persisted candidate waiting for its key when retried then its original bootstrap still recovers`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            val context = app.posato.feature.sync.testContext
            harness.store.persistCandidate(
                PersistedCandidate(WorkspaceId(testIdentifier(99)), context.transportEpochId, context.keyEpochId, bindingA),
            )
            harness.cloud.zoneExists = true
            harness.cloud.storedAnchor = WorkspaceAnchor(context.workspaceId, context.transportEpochId, context.keyEpochId)
            harness.sync.syncWithIcloud()
            assertEquals(SyncStatus.WAITING_FOR_KEY, harness.sync.state.value.status)
            assertFalse(harness.sync.state.value.joinPending)
            val reads = harness.keys.readCalls
            harness.sync.onForeground()
            assertEquals(reads, harness.keys.readCalls)
            harness.deliverJoinKey()
            harness.sync.syncWithIcloud()
            advanceUntilIdle()
            assertTrue(harness.sync.state.value.linked)
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a changed account or workspace when either check runs then no new setup is created`() = runTest {
        for (manual in listOf(false, true)) {
            for (change in 0..2) {
                val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
                try {
                    harness.waitForKey()
                    when (change) {
                        0 -> harness.account.default = BindingResolution.Available(bindingB)
                        1 -> harness.cloud.storedAnchor = null
                        2 -> harness.cloud.storedAnchor = checkNotNull(harness.cloud.storedAnchor).copy(workspaceId = WorkspaceId(testIdentifier(99)))
                    }
                    if (manual) harness.sync.syncWithIcloud() else harness.sync.onForeground()
                    advanceUntilIdle()
                    assertEquals(if (change == 1) SyncStatus.LOCAL_ONLY else SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
                    assertFalse(harness.sync.state.value.linked)
                    assertFalse(harness.sync.state.value.joinPending)
                    assertEquals(0, harness.cloud.zoneSaveCalls)
                    assertEquals(0, harness.cloud.anchorCreateCalls)
                    assertEquals(0, harness.keys.createCalls)
                    assertEquals(0, harness.keys.deleteCalls)
                    assertEquals(0, harness.mailbox.cursors.size)
                    assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
                    harness.sync.onForeground()
                    assertEquals(0, harness.cloud.anchorCreateCalls)
                } finally {
                    harness.close()
                }
            }
        }
    }

    @Test
    fun `given a delivered key when either check runs then the existing workspace is exchanged`() = runTest {
        for (manual in listOf(false, true)) {
            val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
            try {
                harness.waitForKey()
                harness.deliverJoinKey()
                if (manual) harness.sync.syncWithIcloud() else harness.sync.onForeground()
                advanceUntilIdle()
                assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
                assertTrue(harness.sync.state.value.linked)
                assertFalse(harness.sync.state.value.joinPending)
                assertEquals(1, harness.mailbox.cursors.size)
                assertEquals(0, harness.cloud.anchorCreateCalls)
            } finally {
                harness.close()
            }
        }
    }

    @Test
    fun `given a transient read when either check runs then waiting remains available`() = runTest {
        for (manual in listOf(false, true)) {
            val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
            try {
                harness.waitForKey()
                val before = harness.sync.state.value
                harness.keys.scriptRead(KeyItemReadResult.UnknownOutcome)
                if (manual) harness.sync.syncWithIcloud() else harness.sync.onForeground()
                assertEquals(before, harness.sync.state.value)
                assertEquals(0, harness.mailbox.cursors.size)
            } finally {
                harness.close()
            }
        }
    }

    @Test
    fun `given a running join when manual and foreground checks overlap then adoption is not duplicated`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.waitForKey()
            harness.deliverJoinKey()
            val gate = CompletableDeferred<Unit>()
            harness.cloud.beforeZoneFetch = { gate.await() }
            val job = launch { harness.sync.onForeground() }
            runCurrent()
            assertTrue(harness.sync.state.value.checkingJoin)
            repeat(5) { harness.sync.onForeground() }
            harness.sync.syncWithIcloud()
            assertEquals(2, harness.cloud.zoneFetchCalls)
            gate.complete(Unit)
            job.join()
            advanceUntilIdle()
            assertEquals(1, harness.mailbox.cursors.size)
            assertTrue(harness.sync.state.value.linked)
            assertFalse(harness.sync.state.value.checkingJoin)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an established workspace with a missing key when the key returns then normal exchange recovers`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.keys.items.clear()
            harness.sync.onForeground()
            advanceUntilIdle()
            assertEquals(SyncStatus.WAITING_FOR_KEY, harness.sync.state.value.status)
            assertTrue(harness.sync.state.value.linked)
            assertFalse(harness.sync.state.value.joinPending)
            harness.deliverJoinKey()
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }
}
