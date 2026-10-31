package app.posato.feature.schedules.host

import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.sync.domain.PauseSetId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a running scheduled pause does on this device. */
internal enum class ScheduledPauseState {
    /** Restrictions hold on this device. */
    APPLIED,

    /** This device cannot start it without setup; no password is ever asked for. */
    SETUP_REQUIRED,

    /** Another account has the console, or an update is being installed; it starts when that ends. */
    WAITING,

    /** The helper did not answer; the next minute tries again. */
    RETRYING,
}

/** One running occurrence as the pause summary names it: its schedule, its set, and its own end. */
internal data class ScheduledPart(
    val name: String,
    val setId: PauseSetId?,
    val endEpochMillis: Long,
) {
    override fun toString(): String {
        return "ScheduledPart(redacted)"
    }
}

/**
 * The one scheduled pause this device shows: the earliest-started name and the latest end, each running
 * occurrence, and how many items an edit added that the device's limits leave unpaused for now.
 */
internal data class ScheduledPause(
    val name: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val keys: Set<OccurrenceKey>,
    val state: ScheduledPauseState,
    val unannounced: Set<OccurrenceKey> = emptySet(),
    val setupUnannounced: Set<OccurrenceKey> = emptySet(),
    val parts: List<ScheduledPart> = emptyList(),
    val notPausedYet: Int = 0,
    /** Running occurrences whose break releases them now; their lifetime is unchanged. */
    val onBreak: Set<OccurrenceKey> = emptySet(),
    /** When the running break ends, or null while none runs. */
    val breakEndEpochMillis: Long? = null,
    /** Whether any running occurrence already used its break, so no break can be taken now. */
    val breakUsed: Boolean = false,
) {
    val restricts: Boolean
        get() {
            return state == ScheduledPauseState.APPLIED
        }

    override fun toString(): String {
        return "ScheduledPause(redacted)"
    }
}

/** What a press of Take a break did. */
internal enum class BreakStart {
    STARTED,

    /** No scheduled pause runs, a running occurrence already used its break, or the length is not offered. */
    UNAVAILABLE,

    /** The break could not be recorded; nothing was released. */
    FAILED,
}

/** The break actions of a running scheduled pause. */
internal interface ScheduledBreaks {
    /** Takes one break of [minutes] in every running occurrence (overlap option A). */
    suspend fun start(minutes: Int): BreakStart

    /** Ends the running break: restrictions return at once. */
    suspend fun end(): Boolean
}

internal object NoScheduledBreaks : ScheduledBreaks {
    override suspend fun start(minutes: Int): BreakStart {
        return BreakStart.UNAVAILABLE
    }

    override suspend fun end(): Boolean {
        return false
    }
}

/** Notice bits kept on each pin, so a relaunch never posts a notice twice. */
internal object ScheduleNotices {
    const val STARTED: Int = 1
    const val SETUP_REQUIRED: Int = 2
}

/** The running scheduled pause as other features see it; a device without a host never has one. */
internal interface ScheduledPauses {
    val pause: StateFlow<ScheduledPause?>

    suspend fun markAnnounced(
        keys: Set<OccurrenceKey>,
        bit: Int,
    )

    suspend fun endEarly(): Boolean

    /** Take a break and End break; a device without a host offers neither. */
    val breaks: ScheduledBreaks
        get() = NoScheduledBreaks

    /** Runs the host where this process hosts schedules; a device without a host returns at once. */
    suspend fun run() = Unit

    /** Evaluates now, such as when the app returns to the foreground. */
    fun refresh() = Unit

    /** False where another process owns the scheduled pause's notices, such as the iPhone's monitor extension. */
    val ownsNotices: Boolean
        get() = true
}

internal object NoScheduledPauses : ScheduledPauses {
    override val pause: StateFlow<ScheduledPause?> = MutableStateFlow<ScheduledPause?>(null).asStateFlow()

    override suspend fun markAnnounced(
        keys: Set<OccurrenceKey>,
        bit: Int,
    ) = Unit

    override suspend fun endEarly(): Boolean {
        return false
    }
}
