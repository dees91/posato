package app.posato.feature.schedules.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.domain.toSync
import app.posato.feature.session.data.sweepRetention
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.targets.domain.normalizeApplicationPolicyNameNfc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SqlScheduleStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : ScheduleSyncStore {
    private val writeGate = Mutex()
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val changes: Flow<Unit>
        get() = mutableChanges

    override suspend fun <T> withWriteGate(block: suspend () -> T): T {
        return writeGate.withLock { block() }
    }

    override suspend fun read(): ScheduleResult<ScheduleSnapshot> {
        return transact(changed = false, writes = false) { readSnapshot() }
    }

    override suspend fun save(
        plan: SchedulePlan,
        workspaceId: ByteArray?,
    ): ScheduleResult<SchedulePlan> {
        val normalized = plan.copy(name = normalizeApplicationPolicyNameNfc(plan.name.trim()))
        if (!normalized.isStorable()) {
            return ScheduleResult.Failure(ScheduleStoreFailure.INVALID_SCHEDULE)
        }
        return transact {
            val existing = readSnapshot().schedules.any { it.plan.id == normalized.id }
            if (!existing && liveScheduleCount() >= ScheduleLimits.MAX_SCHEDULES) {
                throw ScheduleStoreException(ScheduleStoreFailure.CAPACITY)
            }
            writeSchedule(normalized, refused = false)
            workspaceId?.let { writeIntent(it, ScheduleIntent.Put(normalized)) }
            normalized
        }
    }

    override suspend fun remove(
        id: ScheduleId,
        workspaceId: ByteArray?,
    ): ScheduleResult<Unit> {
        return transact {
            deleteScheduleEverywhere(id)
            if (workspaceId == null) {
                scheduleQueries.promoteRefusedSchedules()
            } else {
                writeIntent(workspaceId, ScheduleIntent.Remove(id))
            }
        }
    }

    override suspend fun stop(
        keys: Set<OccurrenceKey>,
        kind: OccurrenceStop,
        authorDate: ScheduleDate,
        workspaceId: ByteArray?,
    ): ScheduleResult<Unit> {
        return transact {
            keys.forEach { key ->
                writeFact(if (kind == OccurrenceStop.SKIP) FACT_SKIP else FACT_END, key)
                val intent = if (kind == OccurrenceStop.SKIP) ScheduleIntent.Skip(key, authorDate) else ScheduleIntent.End(key, authorDate)
                workspaceId?.let { writeIntent(it, intent) }
            }
        }
    }

    override suspend fun recordHost(update: ScheduleHostUpdate): ScheduleResult<Unit> {
        // The host's own writes never wake the host again.
        return transact(changed = false) { writeHostUpdate(update) }
    }

    override suspend fun readIntents(): ScheduleResult<List<SequencedScheduleIntent>> {
        return transact(changed = false, writes = false) { readIntentRows() }
    }

    override suspend fun deleteIntent(sequence: Long): ScheduleResult<Unit> {
        return transact(changed = false) { scheduleQueries.deleteScheduleIntent(sequence) }
    }

    override suspend fun seedOnce(
        workspaceId: ByteArray,
        synced: SyncedSchedules,
        today: ScheduleDate,
    ): ScheduleResult<Boolean> {
        return transact { seedWorkspace(workspaceId, synced, today) }
    }

    override suspend fun materialize(
        workspaceId: ByteArray,
        synced: SyncedSchedules,
    ): ScheduleResult<Unit> {
        return transact { applySynced(workspaceId, synced) }
    }

    private suspend fun <T> transact(
        changed: Boolean = true,
        writes: Boolean = true,
        block: suspend PosatoDatabase.() -> T,
    ): ScheduleResult<T> {
        val result: ScheduleResult<T> = withContext(databaseDispatcher) {
            try {
                // Only a write sweeps what ended parts kept: a read that also wrote would fail while another
                // connection writes.
                ScheduleResult.Success(
                    database.transactionWithResult<T> { database.block().also { if (writes) database.sweepRetention() } },
                )
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (failure: ScheduleStoreException) {
                ScheduleResult.Failure(failure.reason)
            } catch (_: Exception) {
                ScheduleResult.Failure(ScheduleStoreFailure.STORAGE_FAILURE)
            }
        }
        if (changed && result is ScheduleResult.Success) {
            mutableChanges.tryEmit(Unit)
        }
        return result
    }
}

/** A plan the store may keep: valid for the engine, for the wire, and with a synchronizable identifier. */
internal fun SchedulePlan.isStorable(): Boolean {
    return problem() == null && ScheduleWireRules.isValidName(name) && id.toSync() != null
}
