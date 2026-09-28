package app.posato.feature.schedules.data

import app.posato.feature.schedules.domain.OccurrenceExpiry
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.OccurrencePin
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleFacts
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan

/** A plan as this device shows it; a refused one was not accepted by the shared workspace's cap. */
internal data class StoredSchedule(
    val plan: SchedulePlan,
    val refused: Boolean = false,
)

/** A skip or an early end: both are one fact per occurrence and stop it on every device. */
internal enum class OccurrenceStop {
    SKIP,
    END,
}

/** What a host saw: occurrences it saw start, notice bits it posted, and occurrences that ended here. */
internal data class ScheduleHostUpdate(
    val pins: List<OccurrencePin> = emptyList(),
    val notices: Map<OccurrenceKey, Int> = emptyMap(),
    val finished: Set<OccurrenceKey> = emptySet(),
    val expired: List<OccurrenceExpiry> = emptyList(),
) {
    val isEmpty: Boolean
        get() {
            return pins.isEmpty() && notices.isEmpty() && finished.isEmpty() && expired.isEmpty()
        }

    override fun toString(): String {
        return "ScheduleHostUpdate(redacted)"
    }
}

internal data class ScheduleSnapshot(
    val schedules: List<StoredSchedule> = emptyList(),
    val facts: ScheduleFacts = ScheduleFacts(),
    val pins: List<OccurrencePin> = emptyList(),
) {
    val runnable: List<SchedulePlan>
        get() {
            return schedules.filterNot(StoredSchedule::refused).map(StoredSchedule::plan)
        }

    override fun toString(): String {
        return "ScheduleSnapshot(redacted)"
    }
}

internal sealed interface ScheduleIntent {
    val scheduleId: ScheduleId

    data class Put(
        val plan: SchedulePlan,
    ) : ScheduleIntent {
        override val scheduleId: ScheduleId
            get() {
                return plan.id
            }
    }

    data class Remove(
        override val scheduleId: ScheduleId,
    ) : ScheduleIntent

    data class Skip(
        val key: OccurrenceKey,
        val authorDate: ScheduleDate,
    ) : ScheduleIntent {
        override val scheduleId: ScheduleId
            get() {
                return key.schedule
            }
    }

    data class End(
        val key: OccurrenceKey,
        val authorDate: ScheduleDate,
    ) : ScheduleIntent {
        override val scheduleId: ScheduleId
            get() {
                return key.schedule
            }
    }
}

internal class SequencedScheduleIntent(
    val sequence: Long,
    workspaceId: ByteArray,
    val intent: ScheduleIntent,
) {
    private val workspace = workspaceId.copyOf()

    fun belongsTo(workspaceId: ByteArray): Boolean {
        return workspace.contentEquals(workspaceId)
    }

    override fun toString(): String {
        return "SequencedScheduleIntent(redacted)"
    }
}

internal enum class ScheduleStoreFailure {
    STORAGE_FAILURE,
    INVALID_SCHEDULE,
    CAPACITY,
    WORKSPACE_UNKNOWN,
}

internal sealed interface ScheduleResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ScheduleResult<T>

    data class Failure(
        val reason: ScheduleStoreFailure,
    ) : ScheduleResult<Nothing>
}
