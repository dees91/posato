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
     * Running occurrences with pins as the engine sees them. A run seen for the first time is pinned; a pinned
     * one that no longer runs is released, and when its plan's times have ended it records that natural end,
     * whatever the plan's days or on-off state, so the same interval never runs here again.
     */
    fun step(
        snapshot: ScheduleSnapshot,
        nowEpochMillis: Long,
        zone: ScheduleZone,
    ): HostStep {
        val running = ScheduleOccurrences.active(snapshot.runnable, snapshot.facts, nowEpochMillis, zone)
        val runningKeys = running.map { it.key }.toSet()
        val pinnedKeys = snapshot.pins.map { it.key }.toSet()
        val newPins = running.filter { it.key !in pinnedKeys }.map { OccurrencePin(it.key, it.startEpochMillis) }
        val released = pinnedKeys - runningKeys
        val plans = snapshot.schedules.associate { it.plan.id to it.plan }
        val expired = released.mapNotNull { key ->
            val end = plans[key.schedule]?.let { ScheduleOccurrences.planned(it, key.date, zone) }?.endEpochMillis
            end?.takeIf { nowEpochMillis >= it }?.let { key to it }
        }.toMap()
        val yesterday = zone.localAt(nowEpochMillis).date.plusDays(-1)
        val forgotten = snapshot.facts.expired.keys.filter { it.date < yesterday }.toSet()
        val pins = snapshot.pins.filter { it.key in runningKeys } + newPins
        val update = ScheduleHostUpdate(pins = newPins, released = released, expired = expired, forgotten = forgotten)
        return HostStep(running, update, pins)
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
