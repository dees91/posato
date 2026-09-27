package app.posato.feature.schedules.data

import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import kotlinx.coroutines.flow.Flow

/**
 * The schedules this device shows and will run. Every change that happens while a workspace is linked
 * records its sync intent in the same transaction, so sync can never lose or invent a change.
 */
internal interface LocalScheduleStore {
    val changes: Flow<Unit>

    suspend fun <T> withWriteGate(block: suspend () -> T): T

    suspend fun read(): ScheduleResult<ScheduleSnapshot>

    /** Saves [plan] with its name trimmed and in NFC; [workspaceId] is the linked workspace, or null when unlinked. */
    suspend fun save(
        plan: SchedulePlan,
        workspaceId: ByteArray?,
    ): ScheduleResult<SchedulePlan>

    suspend fun remove(
        id: ScheduleId,
        workspaceId: ByteArray?,
    ): ScheduleResult<Unit>

    /** Skips or ends [keys]: one fact each, and one intent each while linked. */
    suspend fun stop(
        keys: Set<OccurrenceKey>,
        kind: OccurrenceStop,
        authorDate: ScheduleDate,
        workspaceId: ByteArray?,
    ): ScheduleResult<Unit>

    /** Records what the host saw; pins keep their notice bits, and a finished occurrence becomes terminal here. */
    suspend fun recordHost(update: ScheduleHostUpdate): ScheduleResult<Unit>
}

/** What the shared workspace says, already converted to schedule types. */
internal data class SyncedSchedules(
    val live: List<SchedulePlan> = emptyList(),
    val refused: List<SchedulePlan> = emptyList(),
    val removed: Set<ScheduleId> = emptySet(),
    val skips: Set<OccurrenceKey> = emptySet(),
    val ends: Set<OccurrenceKey> = emptySet(),
) {
    override fun toString(): String {
        return "SyncedSchedules(redacted)"
    }
}

/** The side of the store that only sync uses. */
internal interface ScheduleSyncStore : LocalScheduleStore {
    suspend fun readIntents(): ScheduleResult<List<SequencedScheduleIntent>>

    suspend fun deleteIntent(sequence: Long): ScheduleResult<Unit>

    /**
     * Publishes this device's plans and recent facts once per workspace, in one transaction with its
     * marker, and drops local plans the workspace already removed. Returns false when already seeded.
     */
    suspend fun seedOnce(
        workspaceId: ByteArray,
        synced: SyncedSchedules,
        today: ScheduleDate,
    ): ScheduleResult<Boolean>

    /** Shows the shared plans overlaid by this device's changes that have not been published yet. */
    suspend fun materialize(
        workspaceId: ByteArray,
        synced: SyncedSchedules,
    ): ScheduleResult<Unit>
}
