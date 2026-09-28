package app.posato.feature.schedules.host

import app.posato.feature.schedules.data.ScheduleHostUpdate
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.domain.OccurrencePin
import app.posato.feature.schedules.domain.ScheduleOccurrence
import app.posato.feature.schedules.domain.ScheduleOccurrences
import app.posato.feature.schedules.domain.ScheduleZone

internal data class HostStep(
    val running: List<ScheduleOccurrence>,
    val update: ScheduleHostUpdate,
    val pins: List<OccurrencePin>,
)

internal object ScheduleHostPolicy {
    /**
     * Running occurrences with pins as the engine sees them. A running occurrence seen for the first
     * time is pinned with its start; a pinned one that no longer runs (it ended, was turned off,
     * deleted, skipped or ended early, or the clock went back) becomes terminal here.
     */
    fun step(
        snapshot: ScheduleSnapshot,
        nowEpochMillis: Long,
        zone: ScheduleZone,
    ): HostStep {
        val running = ScheduleOccurrences.active(snapshot.runnable, snapshot.facts, nowEpochMillis, zone, snapshot.pins)
        val runningKeys = running.map { it.key }.toSet()
        val pinnedKeys = snapshot.pins.map { it.key }.toSet()
        val newPins = running.filter { it.key !in pinnedKeys }.map { OccurrencePin(it.key, it.startEpochMillis) }
        val finished = pinnedKeys - runningKeys
        val pins = snapshot.pins.filter { it.key in runningKeys } + newPins
        return HostStep(running, ScheduleHostUpdate(pins = newPins, finished = finished), pins)
    }

    /**
     * The pause Session shows. Start notices go out once the restrictions hold; a setup notice goes out
     * only on a device that had the consent, so a Mac that was never set up is not reminded every day.
     */
    fun pause(
        running: List<ScheduleOccurrence>,
        pins: List<OccurrencePin>,
        state: ScheduledPauseState,
        hadConsent: Boolean,
    ): ScheduledPause? {
        val first = running.firstOrNull() ?: return null
        val keys = running.map { it.key }.toSet()
        val bits = pins.associate { it.key to it.notices }

        fun without(bit: Int) = keys.filter { (bits[it] ?: 0) and bit == 0 }.toSet()
        return ScheduledPause(
            name = first.name,
            startEpochMillis = first.startEpochMillis,
            endEpochMillis = running.maxOf { it.endEpochMillis },
            keys = keys,
            state = state,
            unannounced = if (state == ScheduledPauseState.APPLIED) without(ScheduleNotices.STARTED) else emptySet(),
            setupUnannounced = if (state == ScheduledPauseState.SETUP_REQUIRED && hadConsent) without(ScheduleNotices.SETUP_REQUIRED) else emptySet(),
        )
    }
}
