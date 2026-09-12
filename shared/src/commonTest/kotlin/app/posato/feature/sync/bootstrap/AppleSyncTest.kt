package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.RecordDeleteResult
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppleSyncTest {
    @Test
    fun `given a delayed join key when foreground returns then the existing workspace is adopted`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.waitForKey()
            assertEquals(SyncStatus.WAITING_FOR_KEY, harness.sync.state.value.status)
            harness.deliverJoinKey()
            harness.sync.onForeground()
            advanceUntilIdle()
            assertTrue(harness.sync.state.value.linked)
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
            assertEquals(0, harness.cloud.zoneSaveCalls)
            assertEquals(0, harness.cloud.anchorCreateCalls)
            assertEquals(0, harness.keys.createCalls)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given no consent when foreground arrives then no provider is called`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.sync.onForeground()
            advanceUntilIdle()
            assertEquals(0, harness.account.calls)
            assertEquals(0, harness.mailbox.cursors.size)
            assertEquals(SyncStatus.LOCAL_ONLY, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given pending work when publish is uncertain then retry reuses bytes and the writer remains usable`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            harness.mailbox.saveResult = BundleSaveResult.UnknownOutcome
            harness.recordDomainChanges(testPolicy(), testPolicy("one.example"))
            advanceUntilIdle()
            assertEquals(2, harness.snapshot().pendingBundles.size)
            val original = harness.mailbox.saved.first()
            harness.mailbox.saveResult = BundleSaveResult.Identical
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(original, harness.mailbox.saved[1])
            assertTrue(harness.snapshot().pendingBundles.isEmpty())
            harness.recordDomainChanges(testPolicy("one.example"), testPolicy("two.example"))
            advanceUntilIdle()
            assertEquals(4, harness.snapshot().acceptedBundles.size)
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
            assertEquals(1, harness.keys.readCalls)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a switched account when sync runs then both mailbox legs preserve pending work and cursor`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            harness.mailbox.saveResult = BundleSaveResult.Retryable
            harness.recordDomainChanges(testPolicy(), testPolicy("one.example"))
            advanceUntilIdle()
            val before = harness.snapshot()
            val sends = harness.mailbox.saved.size
            val fetches = harness.mailbox.cursors.size
            harness.account.default = BindingResolution.Available(bindingB)
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(before, harness.snapshot())
            assertEquals(sends, harness.mailbox.saved.size)
            assertEquals(fetches, harness.mailbox.cursors.size)
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a removed workspace when the old anchor returns then relink is refused`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            val removed = checkNotNull(harness.cloud.storedAnchor)
            harness.sync.removeWorkspace()
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(BootstrapStoreResult.Success(true), harness.store.containsRemoved(removed.workspaceId))
            harness.cloud.zoneExists = true
            harness.cloud.storedAnchor = removed
            val item = checkNotNull(
                BootstrapEncoding.encodeKeyItem(
                    removed.workspaceId,
                    removed.transportEpochId,
                    removed.keyEpochId,
                    ByteArray(32) { 7 },
                ),
            )
            val account = checkNotNull(BootstrapEncoding.identifierToAccountText(removed.workspaceId.value))
            harness.keys.items[account] = item.copyBytes()
            harness.sync.syncWithIcloud()
            assertEquals(SyncStatus.RETRYABLE, harness.sync.state.value.status)
            assertEquals(false, harness.sync.state.value.linked)
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(0, harness.cloud.anchorCreateCalls)
            assertEquals(0, harness.keys.createCalls)
            assertTrue(harness.database.syncReplicaQueries.selectAcceptedBundles().executeAsList().isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a different anchor when exchanging and removing then the foreign zone is untouched`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.cloud.storedAnchor = checkNotNull(harness.cloud.storedAnchor).copy(workspaceId = WorkspaceId(testIdentifier(90)))
            harness.sync.onForeground()
            advanceUntilIdle()
            assertEquals(0, harness.mailbox.cursors.size)
            harness.sync.removeWorkspace()
            assertEquals(0, harness.mailbox.deleteCalls)
            assertEquals(1, harness.keys.deleteCalls)
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a failed zone deletion when removing then key and local state stay`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.mailbox.deleteResult = RecordDeleteResult.UnknownOutcome
            harness.sync.removeWorkspace()
            assertEquals(0, harness.keys.deleteCalls)
            assertIs<BootstrapState.Established>(assertIs<BootstrapStoreResult.Success<BootstrapState>>(harness.store.read()).value)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a ready workspace when removing then records key and state clear with tombstone`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            val removed = checkNotNull(harness.cloud.storedAnchor)
            harness.sync.removeWorkspace()
            assertEquals(1, harness.mailbox.deleteCalls)
            assertEquals(1, harness.keys.deleteCalls)
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(BootstrapStoreResult.Success(true), harness.store.containsRemoved(removed.workspaceId))
            assertEquals(SyncStatus.LOCAL_ONLY, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a partial removal when removing again then the second removal completes`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.mailbox.deleteResults.addAll(
                listOf(RecordDeleteResult.UnknownOutcome, RecordDeleteResult.DeletedAndAbsent),
            )
            harness.sync.removeWorkspace()
            assertEquals(SyncStatus.RETRYABLE, harness.sync.state.value.status)
            assertIs<BootstrapState.Established>(assertIs<BootstrapStoreResult.Success<BootstrapState>>(harness.store.read()).value)
            harness.sync.removeWorkspace()
            assertEquals(2, harness.mailbox.deleteCalls)
            assertEquals(1, harness.keys.deleteCalls)
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(SyncStatus.LOCAL_ONLY, harness.sync.state.value.status)
            harness.sync.removeWorkspace()
            assertEquals(2, harness.mailbox.deleteCalls)
            assertEquals(SyncStatus.LOCAL_ONLY, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a remaining record when removing then retryable is reported and the row is kept`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.mailbox.deleteResult = RecordDeleteResult.UnknownOutcome
            harness.sync.removeWorkspace()
            assertEquals(SyncStatus.RETRYABLE, harness.sync.state.value.status)
            assertEquals(0, harness.keys.deleteCalls)
            assertIs<BootstrapState.Established>(assertIs<BootstrapStoreResult.Success<BootstrapState>>(harness.store.read()).value)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an account change when removing then action required is reported and the row is kept`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.mailbox.deleteResult = RecordDeleteResult.AccountChanged
            harness.sync.removeWorkspace()
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
            assertEquals(0, harness.keys.deleteCalls)
            assertIs<BootstrapState.Established>(assertIs<BootstrapStoreResult.Success<BootstrapState>>(harness.store.read()).value)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a missing anchor when syncing then action required is reported without fetching`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.cloud.storedAnchor = null
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
            assertTrue(harness.mailbox.cursors.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a missing anchor when removing then leftovers are swept and state ends local-only`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            val removed = checkNotNull(harness.cloud.storedAnchor)
            harness.cloud.storedAnchor = null
            harness.sync.removeWorkspace()
            assertEquals(0, harness.mailbox.deleteCalls)
            assertEquals(1, harness.mailbox.sweepCalls)
            assertEquals(1, harness.keys.deleteCalls)
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(BootstrapStoreResult.Success(true), harness.store.containsRemoved(removed.workspaceId))
            assertEquals(SyncStatus.LOCAL_ONLY, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a missing anchor when the sweep is pending then retryable is reported and the row is kept`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.cloud.storedAnchor = null
            harness.mailbox.sweepResult = BundleSweepResult.Retryable
            harness.sync.removeWorkspace()
            assertEquals(1, harness.mailbox.sweepCalls)
            assertEquals(SyncStatus.RETRYABLE, harness.sync.state.value.status)
            assertEquals(0, harness.keys.deleteCalls)
            assertIs<BootstrapState.Established>(assertIs<BootstrapStoreResult.Success<BootstrapState>>(harness.store.read()).value)
            harness.mailbox.sweepResult = BundleSweepResult.Swept
            harness.sync.removeWorkspace()
            assertEquals(2, harness.mailbox.sweepCalls)
            assertEquals(1, harness.keys.deleteCalls)
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), harness.store.read())
            assertEquals(SyncStatus.LOCAL_ONLY, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a deletion-only page when consuming then the cursor advances without accepting`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            harness.mailbox.pages.add(ChangeFetchResult.Page(ChangePage(null, false, testCursor(2))))
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
            assertTrue(harness.database.syncReplicaQueries.selectAcceptedBundles().executeAsList().isEmpty())
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(testCursor(2), harness.mailbox.cursors.last())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a foreign-context bundle when consuming then the cursor advances without accepting`() = runTest {
        val shared = FakeMailboxPort()
        val first = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), name = "foreign-first.db", mailboxPort = shared)
        val second = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), name = "foreign-second.db", mailboxPort = shared)
        try {
            first.establish()
            val foreignContext = SyncContext(
                workspaceId = WorkspaceId(testIdentifier(11)),
                transportEpochId = TransportEpochId(testIdentifier(12)),
                keyEpochId = KeyEpochId(testIdentifier(13)),
            )
            second.store.commitEstablished(EstablishedWorkspace(foreignContext, bindingA))
            second.cloud.zoneExists = true
            second.cloud.storedAnchor = WorkspaceAnchor(foreignContext.workspaceId, foreignContext.transportEpochId, foreignContext.keyEpochId)
            val account = checkNotNull(BootstrapEncoding.identifierToAccountText(foreignContext.workspaceId.value))
            second.keys.items[account] = checkNotNull(
                BootstrapEncoding.encodeKeyItem(
                    foreignContext.workspaceId,
                    foreignContext.transportEpochId,
                    foreignContext.keyEpochId,
                    ByteArray(32) { 8 },
                ),
            ).copyBytes()
            first.sync.onForeground()
            advanceUntilIdle()
            first.recordDomainChanges(testPolicy(), testPolicy("one.example"))
            first.sync.syncNow()
            advanceUntilIdle()
            assertTrue(shared.saved.isNotEmpty())
            val foreign = shared.saved.last()
            shared.pages.add(ChangeFetchResult.Page(ChangePage(foreign, false, testCursor(5))))
            second.sync.syncNow()
            advanceUntilIdle()
            assertEquals(SyncStatus.COMPLETED, second.sync.state.value.status)
            assertTrue(second.database.syncReplicaQueries.selectAcceptedBundles().executeAsList().isEmpty())
            second.sync.syncNow()
            advanceUntilIdle()
            assertEquals(testCursor(5), shared.cursors.last())
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a removal when relinking then a fresh workspace establishes in the found zone`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            val removed = checkNotNull(harness.cloud.storedAnchor)
            harness.sync.removeWorkspace()
            assertEquals(SyncStatus.LOCAL_ONLY, harness.sync.state.value.status)
            harness.cloud.storedAnchor = null
            val zoneSaves = harness.cloud.zoneSaveCalls
            harness.sync.syncWithIcloud()
            val established = assertIs<BootstrapState.Established>(
                assertIs<BootstrapStoreResult.Success<BootstrapState>>(harness.store.read()).value,
            )
            assertTrue(established.workspace.context.workspaceId != removed.workspaceId)
            assertEquals(1, harness.mailbox.sweepCalls)
            assertEquals(zoneSaves, harness.cloud.zoneSaveCalls)
            assertEquals(1, harness.cloud.anchorCreateCalls)
            assertTrue(harness.sync.state.value.linked)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an expired token when fetching then the first page restarts without clearing accepted work`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            harness.recordDomainChanges(testPolicy(), testPolicy("one.example"))
            advanceUntilIdle()
            val before = harness.snapshot()
            harness.mailbox.pages.add(ChangeFetchResult.TokenExpired)
            harness.sync.syncNow()
            advanceUntilIdle()
            assertTrue(harness.mailbox.cursors.last().isFirstPage())
            assertEquals(before.acceptedBundles, harness.snapshot().acceptedBundles)
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a rejected bundle when fetching then the cursor is pinned`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            val before = harness.snapshot()
            val bundle = checkNotNull(MailboxBundle.fromParts(ByteArray(16), byteArrayOf(1)))
            harness.mailbox.pages.add(ChangeFetchResult.Page(ChangePage(bundle, false, testCursor(2))))
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(before, harness.snapshot())
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a running exchange when opportunities overlap then only one more exchange is queued`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        try {
            harness.establish()
            harness.mailbox.beforeFetch = { gate.await() }
            harness.sync.onForeground()
            runCurrent()
            repeat(10) { harness.sync.syncNow() }
            assertEquals(1, harness.mailbox.cursors.size)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, harness.mailbox.cursors.size)
        } finally {
            harness.close()
        }
    }
}
