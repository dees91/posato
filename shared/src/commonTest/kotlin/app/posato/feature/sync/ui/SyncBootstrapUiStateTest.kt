package app.posato.feature.sync.ui

import app.posato.feature.sync.bootstrap.AppleSyncTestHarness
import app.posato.feature.sync.bootstrap.SyncStatus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncBootstrapUiStateTest {
    @Test
    fun `given a fresh holder when sync runs twice rapidly then only one workspace is created`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            val holder = SyncBootstrapUiState(harness.sync, this)
            holder.sync()
            holder.sync()
            advanceUntilIdle()
            assertEquals(1, harness.cloud.anchorCreateCalls)
            assertEquals(true, holder.syncState.value.linked)
            assertEquals(SyncStatus.COMPLETED, holder.syncState.value.status)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an established workspace when foreground arrives then the linked device exchanges`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler))
        try {
            harness.establish()
            val holder = SyncBootstrapUiState(harness.sync, this)
            holder.onForeground()
            advanceUntilIdle()
            assertEquals(true, holder.syncState.value.linked)
            assertEquals(1, harness.mailbox.cursors.size)
        } finally {
            harness.close()
        }
    }
}
