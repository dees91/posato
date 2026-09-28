package app.posato.feature.schedules.host

import app.posato.feature.schedules.domain.OccurrenceKey
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

/** The one scheduled pause this device shows: the earliest-started name and the latest end. */
internal data class ScheduledPause(
    val name: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val keys: Set<OccurrenceKey>,
    val state: ScheduledPauseState,
    val unannounced: Set<OccurrenceKey> = emptySet(),
    val setupUnannounced: Set<OccurrenceKey> = emptySet(),
) {
    val restricts: Boolean
        get() {
            return state == ScheduledPauseState.APPLIED
        }

    override fun toString(): String {
        return "ScheduledPause(redacted)"
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
