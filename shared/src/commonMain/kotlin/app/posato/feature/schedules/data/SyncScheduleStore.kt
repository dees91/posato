package app.posato.feature.schedules.data

import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.bootstrap.BootstrapStoreResult
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Where a schedule change learns whether a workspace is linked, and how it asks for an exchange. */
internal interface ScheduleSyncLink {
    suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?>

    fun syncNow()
}

/**
 * The store the UI uses. It captures the linked workspace under the write gate, so a change either
 * records its intent or happens before linking; if the workspace cannot be read, the change is
 * refused rather than written without an intent that a later exchange would silently undo.
 */
internal class SyncScheduleStore(
    private val local: LocalScheduleStore,
    private val link: ScheduleSyncLink,
) : LocalScheduleStore {
    override val changes: Flow<Unit>
        get() = local.changes

    override suspend fun <T> withWriteGate(block: suspend () -> T): T {
        return local.withWriteGate(block)
    }

    override suspend fun read(): ScheduleResult<ScheduleSnapshot> {
        return local.read()
    }

    override suspend fun save(
        plan: SchedulePlan,
        workspaceId: ByteArray?,
    ): ScheduleResult<SchedulePlan> {
        return recorded { workspace -> local.save(plan, workspace) }
    }

    override suspend fun remove(
        id: ScheduleId,
        workspaceId: ByteArray?,
    ): ScheduleResult<Unit> {
        return recorded { workspace -> local.remove(id, workspace) }
    }

    override suspend fun stop(
        keys: Set<OccurrenceKey>,
        kind: OccurrenceStop,
        authorDate: ScheduleDate,
        workspaceId: ByteArray?,
    ): ScheduleResult<Unit> {
        return recorded { workspace -> local.stop(keys, kind, authorDate, workspace) }
    }

    override suspend fun recordHost(update: ScheduleHostUpdate): ScheduleResult<Unit> {
        return local.recordHost(update)
    }

    private suspend fun <T> recorded(change: suspend (ByteArray?) -> ScheduleResult<T>): ScheduleResult<T> {
        return local.withWriteGate {
            val workspace = when (val captured = link.captureWorkspace()) {
                is BootstrapStoreResult.Failure -> return@withWriteGate ScheduleResult.Failure(ScheduleStoreFailure.WORKSPACE_UNKNOWN)
                is BootstrapStoreResult.Success -> captured.value?.context?.workspaceId?.value?.copyBytes()
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val result = change(workspace)
                if (result is ScheduleResult.Success && workspace != null) {
                    link.syncNow()
                }
                result
            }
        }
    }
}
