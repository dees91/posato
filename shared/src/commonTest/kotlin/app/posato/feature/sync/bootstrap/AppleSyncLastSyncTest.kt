package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When this device last completed an exchange: shown in the sync section, kept on the device, never synchronized. */
class AppleSyncLastSyncTest {
    @Test
    fun `given a completed pass when a later pass fails then the time of the completed one stays`() = runTest {
        var now = 1_000L
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), name = "last-sync-kept.db", wallClock = SyncWallClock { now })
        try {
            harness.establish()
            assertNull(harness.sync.state.value.lastSyncedEpochMillis)
            harness.sync.onForeground()
            advanceUntilIdle()
            assertEquals(SyncStatus.COMPLETED, harness.sync.state.value.status)
            assertEquals(1_000L, harness.sync.state.value.lastSyncedEpochMillis)

            now = 2_000L
            repeat(FAILING_FETCHES) { harness.mailbox.pages.addLast(ChangeFetchResult.Retryable) }
            harness.sync.syncNow()
            // Only this pass: advancing time would run the retry, which the fake then lets complete.
            runCurrent()

            assertEquals(SyncStatus.RETRYABLE, harness.sync.state.value.status)
            assertEquals(1_000L, harness.sync.state.value.lastSyncedEpochMillis)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a completed pass when Posato starts again and its first pass fails then the earlier time is shown`() = runTest {
        val database = createLocalPolicyTestDatabase("last-sync-restart.db")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = AppleSyncTestHarness(dispatcher, wallClock = SyncWallClock { 5_000L }, testDatabase = database)
        first.establish()
        first.sync.onForeground()
        advanceUntilIdle()
        first.closeKeepingDatabase()

        val second = AppleSyncTestHarness(dispatcher, wallClock = SyncWallClock { 9_000L }, testDatabase = database)
        try {
            // The workspace is already established on disk; this only sets the fake key and zone again.
            second.establish()
            repeat(FAILING_FETCHES) { second.mailbox.pages.addLast(ChangeFetchResult.Retryable) }
            second.sync.onForeground()
            runCurrent()

            assertEquals(SyncStatus.RETRYABLE, second.sync.state.value.status)
            assertEquals(5_000L, second.sync.state.value.lastSyncedEpochMillis)
        } finally {
            second.close()
        }
    }

    private companion object {
        /** Enough failing fetches that every fetch of the pass, and its retry, fails. */
        const val FAILING_FETCHES = 5
    }
}
