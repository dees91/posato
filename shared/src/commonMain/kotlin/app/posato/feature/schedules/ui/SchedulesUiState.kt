package app.posato.feature.schedules.ui

import androidx.compose.runtime.Immutable
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.schedules.domain.MINUTES_PER_DAY
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.domain.SchedulePlanProblem
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet

internal enum class ScheduleDay(
    val label: String,
) {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday"),
    ;

    /** Bit 0 is Monday, as on the wire and in the engine. */
    val bit: Int
        get() {
            return 1 shl ordinal
        }
}

/** One schedule as a row shows it; every text is ready to display. */
@Immutable
internal data class ScheduleRowModel(
    val id: ScheduleId,
    val name: String,
    val daysLabel: String,
    val hoursLabel: String,
    val enabled: Boolean,
    val refused: Boolean,
    val nextRunLabel: String?,
    val skippedLabel: String?,
    val canSkip: Boolean,
) {
    override fun toString(): String {
        return "ScheduleRowModel(redacted)"
    }
}

internal enum class ScheduleEditorError {
    NAME,
    NO_DAY,
    TOO_SHORT,
    SAME_TIMES,
    FULL,
    NOT_SAVED,
}

/** The editor's form. A null [id] is a new schedule. */
@Immutable
internal data class ScheduleDraft(
    val id: ScheduleId? = null,
    val name: String = "",
    val days: ImmutableSet<ScheduleDay> = persistentSetOf(
        ScheduleDay.MONDAY,
        ScheduleDay.TUESDAY,
        ScheduleDay.WEDNESDAY,
        ScheduleDay.THURSDAY,
        ScheduleDay.FRIDAY,
    ),
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 11,
    val endMinute: Int = 0,
    val enabled: Boolean = true,
) {
    val startOfDay: Int
        get() {
            return startHour * MINUTES_PER_HOUR + startMinute
        }

    val endOfDay: Int
        get() {
            return endHour * MINUTES_PER_HOUR + endMinute
        }

    val endsNextDay: Boolean
        get() {
            return endOfDay <= startOfDay
        }

    /** The same checks the store and the wire apply, so a draft that passes here also saves. */
    fun problem(): ScheduleEditorError? {
        val probe = SchedulePlan(ScheduleId(""), name.trim(), days.fold(0) { mask, day -> mask or day.bit }, startOfDay, endOfDay, enabled)
        return when {
            !ScheduleWireRules.isValidName(probe.name) -> ScheduleEditorError.NAME

            else -> when (probe.problem()) {
                SchedulePlanProblem.NAME_LENGTH -> ScheduleEditorError.NAME
                SchedulePlanProblem.NO_WEEKDAY -> ScheduleEditorError.NO_DAY
                SchedulePlanProblem.SAME_TIMES -> ScheduleEditorError.SAME_TIMES
                SchedulePlanProblem.TOO_SHORT -> ScheduleEditorError.TOO_SHORT
                SchedulePlanProblem.MINUTE_RANGE, null -> null
            }
        }
    }

    fun toPlan(id: ScheduleId): SchedulePlan {
        return SchedulePlan(id, name.trim(), days.fold(0) { mask, day -> mask or day.bit }, startOfDay, endOfDay, enabled)
    }

    override fun toString(): String {
        return "ScheduleDraft(redacted)"
    }

    companion object {
        const val MINUTES_PER_HOUR: Int = 60

        fun of(plan: SchedulePlan): ScheduleDraft {
            return ScheduleDraft(
                id = plan.id,
                name = plan.name,
                days = ScheduleDay.entries.filter { plan.runsOn(it.ordinal) }.toPersistentSet(),
                startHour = plan.startMinute / MINUTES_PER_HOUR,
                startMinute = plan.startMinute % MINUTES_PER_HOUR,
                endHour = plan.endMinute / MINUTES_PER_HOUR,
                endMinute = plan.endMinute % MINUTES_PER_HOUR,
                enabled = plan.enabled,
            )
        }
    }
}

@Immutable
internal data class SchedulesUiState(
    val loaded: Boolean = false,
    val schedules: ImmutableList<ScheduleRowModel> = persistentListOf(),
    val editor: ScheduleDraft? = null,
    val editorError: ScheduleEditorError? = null,
    val saving: Boolean = false,
    val confirmingDelete: ScheduleId? = null,
    val showingSetup: Boolean = false,
    val showUpdateNote: Boolean = false,
    val changeFailed: Boolean = false,
) {
    val atCapacity: Boolean
        get() {
            return schedules.count { !it.refused } >= ScheduleLimits.MAX_SCHEDULES
        }
}

internal fun timeLabel(
    hour: Int,
    minute: Int,
): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

internal fun minuteLabel(minuteOfDay: Int): String {
    val minute = minuteOfDay.mod(MINUTES_PER_DAY)
    return timeLabel(minute / ScheduleDraft.MINUTES_PER_HOUR, minute % ScheduleDraft.MINUTES_PER_HOUR)
}

/**
 * Whether Screen Time lets schedules pause apps here. Only a known missing authorization asks for it;
 * a platform without Screen Time or an unreadable state never raises the card.
 */
internal fun LocalApplicationMappingsLoadResult.allowsScheduledStarts(): Boolean {
    return (this as? LocalApplicationMappingsLoadResult.Success)?.access?.let { it == LocalApplicationMappingsAccess.READY } ?: true
}

/** Where a Mac stands for schedules; an unread state raises neither a card nor Add schedule. */
internal enum class MacScheduleReadiness {
    UNKNOWN,
    SETUP,
    CONSENT,
    READY,
}

internal fun MacSetupPresentation.scheduleReadiness(): MacScheduleReadiness {
    return when {
        readyForSchedules -> MacScheduleReadiness.READY
        readiness == null -> MacScheduleReadiness.UNKNOWN
        schedulesNeedConsent -> MacScheduleReadiness.CONSENT
        else -> MacScheduleReadiness.SETUP
    }
}
