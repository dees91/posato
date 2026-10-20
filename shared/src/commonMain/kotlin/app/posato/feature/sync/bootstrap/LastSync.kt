package app.posato.feature.sync.bootstrap

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.domain.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** When this device last completed an exchange with a workspace; kept on the device and never synchronized. */
internal interface LastSyncStore {
    /** The time, or null before the first completed exchange or when it cannot be read. */
    suspend fun read(workspaceId: WorkspaceId): Long?

    suspend fun record(
        workspaceId: WorkspaceId,
        completedEpochMillis: Long,
    )

    object None : LastSyncStore {
        override suspend fun read(workspaceId: WorkspaceId): Long? = null

        override suspend fun record(
            workspaceId: WorkspaceId,
            completedEpochMillis: Long,
        ) = Unit
    }
}

/** Removing the workspace clears these rows with the rest of its state (`SqlBootstrapStore.clearEstablished`). */
internal class SqlLastSyncStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : LastSyncStore {
    override suspend fun read(workspaceId: WorkspaceId): Long? {
        return quietly { database.syncBootstrapQueries.selectLastExchange(workspaceId.value.copyBytes()).awaitAsOneOrNull() }
    }

    override suspend fun record(
        workspaceId: WorkspaceId,
        completedEpochMillis: Long,
    ) {
        quietly { database.syncBootstrapQueries.recordLastExchange(workspaceId.value.copyBytes(), completedEpochMillis) }
    }

    /** A time that cannot be read or kept only leaves the line out; it never stops an exchange. */
    private suspend fun <T> quietly(block: suspend () -> T): T? {
        return withContext(databaseDispatcher) {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Shows the stored time once per linked workspace, and keeps the time of each completed pass, in [state]. */
internal class LastSyncTracker(
    private val store: LastSyncStore,
    private val clock: SyncWallClock,
    private val state: MutableStateFlow<AppleSyncState>,
) {
    private var shownFor: WorkspaceId? = null

    /** [workspaceId] is the linked workspace, or null when none is linked, which shows no time. */
    suspend fun show(workspaceId: WorkspaceId?) {
        if (workspaceId == shownFor) return
        shownFor = workspaceId
        val stored = workspaceId?.let { id -> store.read(id) }
        state.update { it.copy(lastSyncedEpochMillis = stored) }
    }

    suspend fun record(workspace: EstablishedWorkspace) {
        val now = clock.currentEpochMillis()
        shownFor = workspace.context.workspaceId
        store.record(workspace.context.workspaceId, now)
        state.update { it.copy(lastSyncedEpochMillis = now) }
    }
}
