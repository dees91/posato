package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.ZoneDeleteResult
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
            harness.mailbox.deleteResult = ZoneDeleteResult.UnknownOutcome
            harness.sync.removeWorkspace()
            assertEquals(0, harness.keys.deleteCalls)
            assertIs<BootstrapState.Established>(assertIs<BootstrapStoreResult.Success<BootstrapState>>(harness.store.read()).value)
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
