package app.posato.feature.sync.bootstrap

import app.posato.feature.schedules.data.ScheduleIntent
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleStoreFailure
import app.posato.feature.schedules.data.ScheduleSyncStore
import app.posato.feature.schedules.data.SequencedScheduleIntent
import app.posato.feature.schedules.data.SyncedSchedules
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.domain.toScheduleId
import app.posato.feature.schedules.domain.toSync
import app.posato.feature.sync.domain.LocalMutationFailure
import app.posato.feature.sync.domain.LocalMutationResult
import app.posato.feature.sync.domain.LocalSyncMutation
import app.posato.feature.sync.domain.ScheduleOccurrenceRef
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.sync.domain.SynchronizedSchedule

internal sealed interface ScheduleSyncResult {
    /** [refused] is true while the shared workspace refuses one of the schedules at its cap. */
    data class Done(
        val refused: Boolean,
    ) : ScheduleSyncResult

    data class Halted(
        val status: SyncStatus,
    ) : ScheduleSyncResult
}

/**
 * One schedule pass per exchange: publish this device's plans once per workspace, author pending
 * changes, publish them, then show the shared plans overlaid by whatever is still pending.
 */
internal class ScheduleSync(
    private val store: ScheduleSyncStore,
    private val today: () -> ScheduleDate,
) {
    suspend fun pass(
        writer: SyncWriter,
        workspace: EstablishedWorkspace,
        publishPending: suspend () -> SyncStatus?,
    ): ScheduleSyncResult {
        val workspaceId = workspace.context.workspaceId.value.copyBytes()
        val seeded = store.withWriteGate { store.seedOnce(workspaceId, writer.projection().toSynced(), today()) }
        if (seeded is ScheduleResult.Failure) {
            return ScheduleSyncResult.Halted(seeded.reason.toSyncStatus())
        }
        val drained = drain(writer, workspaceId)
        if (drained != null) {
            return ScheduleSyncResult.Halted(drained)
        }
        publishPending()?.let { status -> return ScheduleSyncResult.Halted(status) }
        val projection = writer.projection()
        val materialized = store.withWriteGate { store.materialize(workspaceId, projection.toSynced()) }
        if (materialized is ScheduleResult.Failure) {
            return ScheduleSyncResult.Halted(materialized.reason.toSyncStatus())
        }
        return ScheduleSyncResult.Done(refused = projection.refusedSchedules.isNotEmpty())
    }

    /** Returns a halting status, or null once every intent was authored, found already reflected, or discarded. */
    private suspend fun drain(
        writer: SyncWriter,
        workspaceId: ByteArray,
    ): SyncStatus? {
        val intents = when (val read = store.readIntents()) {
            is ScheduleResult.Failure -> return read.reason.toSyncStatus()
            is ScheduleResult.Success -> read.value
        }
        intents.forEach { row ->
            val authored = author(writer, workspaceId, row)
            if (authored != null) {
                return authored
            }
            if (store.deleteIntent(row.sequence) is ScheduleResult.Failure) {
                return SyncStatus.ACTION_REQUIRED
            }
        }
        return null
    }

    /** Null when the row may be deleted: authored, already reflected, for another workspace, or refused as invalid. */
    private suspend fun author(
        writer: SyncWriter,
        workspaceId: ByteArray,
        row: SequencedScheduleIntent,
    ): SyncStatus? {
        val mutation = row.intent.toMutation()
        if (!row.belongsTo(workspaceId) || mutation == null || writer.projection().reflects(row.intent)) {
            return null
        }
        val result = writer.mutate(mutation)
        val refusedAsInvalid = result is LocalMutationResult.Failure && result.reason == LocalMutationFailure.INVALID_MUTATION
        return if (result is LocalMutationResult.Success || refusedAsInvalid) null else SyncStatus.ACTION_REQUIRED
    }
}

internal fun SyncProjection.toSynced(): SyncedSchedules {
    return SyncedSchedules(
        live = schedules.map(SynchronizedSchedule::toPlan),
        refused = refusedSchedules.map(SynchronizedSchedule::toPlan),
        removed = removedScheduleIds.map { it.toScheduleId() }.toSet(),
        skips = scheduleSkips.map(ScheduleOccurrenceRef::toKey).toSet(),
        ends = scheduleEnds.map(ScheduleOccurrenceRef::toKey).toSet(),
    )
}

private fun SynchronizedSchedule.toPlan(): SchedulePlan {
    return SchedulePlan(scheduleId.toScheduleId(), name, weekdays, startMinute, endMinute, enabled, setId)
}

private fun ScheduleOccurrenceRef.toKey(): OccurrenceKey {
    return OccurrenceKey(scheduleId.toScheduleId(), date)
}

private fun OccurrenceKey.toRef(): ScheduleOccurrenceRef? {
    return schedule.toSync()?.let { id -> ScheduleOccurrenceRef(id, date) }
}

private fun ScheduleIntent.toMutation(): LocalSyncMutation? {
    return when (this) {
        is ScheduleIntent.Put -> plan.id.toSync()?.let { id ->
            LocalSyncMutation.PutSchedule(id, plan.name, plan.weekdays, plan.startMinute, plan.endMinute, plan.enabled, plan.setId)
        }

        is ScheduleIntent.Remove -> scheduleId.toSync()?.let(LocalSyncMutation::RemoveSchedule)

        is ScheduleIntent.Skip -> key.toRef()?.let { ref -> LocalSyncMutation.SkipOccurrence(ref, authorDate) }

        is ScheduleIntent.End -> key.toRef()?.let { ref -> LocalSyncMutation.EndOccurrence(ref, authorDate) }
    }
}

private fun SyncProjection.reflects(intent: ScheduleIntent): Boolean {
    return when (intent) {
        is ScheduleIntent.Put -> schedules.any { it.toPlan() == intent.plan }
        is ScheduleIntent.Remove -> removedScheduleIds.any { it.toScheduleId() == intent.scheduleId }
        is ScheduleIntent.Skip -> scheduleSkips.any { it.toKey() == intent.key }
        is ScheduleIntent.End -> scheduleEnds.any { it.toKey() == intent.key }
    }
}

private fun ScheduleStoreFailure.toSyncStatus(): SyncStatus {
    return if (this == ScheduleStoreFailure.STORAGE_FAILURE) SyncStatus.RETRYABLE else SyncStatus.ACTION_REQUIRED
}

/** The schedule store's view of this sync: whether a workspace is linked, and how to ask for an exchange. */
internal fun AppleSync.scheduleLink(): app.posato.feature.schedules.data.ScheduleSyncLink {
    val sync = this
    return object : app.posato.feature.schedules.data.ScheduleSyncLink {
        override suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?> {
            return sync.captureWorkspace()
        }

        override fun syncNow() {
            sync.syncNow()
        }
    }
}
