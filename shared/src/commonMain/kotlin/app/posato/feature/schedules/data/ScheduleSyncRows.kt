package app.posato.feature.schedules.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleFacts

/** Runs inside the store's transaction: publish this device's plans and recent facts once per workspace. */
internal suspend fun PosatoDatabase.seedWorkspace(
    workspaceId: ByteArray,
    synced: SyncedSchedules,
    today: ScheduleDate,
): Boolean {
    if (scheduleQueries.selectSeed(workspaceId).awaitAsOneOrNull() != null) {
        return false
    }
    val snapshot = readSnapshot()
    val shared = (synced.live + synced.refused).map { it.id }.toSet()
    snapshot.schedules.forEach { stored ->
        when (stored.plan.id) {
            in synced.removed -> deleteScheduleEverywhere(stored.plan.id)
            !in shared -> writeIntent(workspaceId, ScheduleIntent.Put(stored.plan))
            else -> Unit
        }
    }
    val kept = snapshot.schedules.map { it.plan.id }.toSet() - synced.removed
    seedFacts(workspaceId, snapshot.facts, synced, today) { key -> key.schedule in kept }
    scheduleQueries.insertSeed(workspaceId)
    return true
}

/**
 * Publishes this device's facts from yesterday on that the workspace does not hold yet, for the plans it keeps; a
 * break is held only when the workspace's break for the occurrence starts no later.
 */
private suspend fun PosatoDatabase.seedFacts(
    workspaceId: ByteArray,
    facts: ScheduleFacts,
    synced: SyncedSchedules,
    today: ScheduleDate,
    kept: (OccurrenceKey) -> Boolean,
) {
    val yesterday = today.plusDays(-1)
    val recent = { key: OccurrenceKey -> kept(key) && key.date >= yesterday }
    val pending = facts.skipped.filter { recent(it) && it !in synced.skips }.map { key -> ScheduleIntent.Skip(key, today) } +
        facts.ended.filter { recent(it) && it !in synced.ends }.map { key -> ScheduleIntent.End(key, today) } +
        facts.breaks.filter { (key, taken) -> recent(key) && synced.breaks[key]?.prevailsOver(taken) != true }
            .map { (key, taken) -> ScheduleIntent.Break(key, today, taken) } +
        facts.breakEnds.filter { recent(it) && it !in synced.breakEnds }.map { key -> ScheduleIntent.BreakEnd(key, today) }
    pending.forEach { intent -> writeIntent(workspaceId, intent) }
}

/**
 * Runs inside the store's transaction: the shared plans, overlaid by this device's pending changes for
 * the same workspace. A pending change for a removed schedule is dropped for good.
 */
internal suspend fun PosatoDatabase.applySynced(
    workspaceId: ByteArray,
    synced: SyncedSchedules,
) {
    val pending = readIntentRows().filter { it.belongsTo(workspaceId) }
    pending.filter { it.intent.scheduleId in synced.removed }.forEach { scheduleQueries.deleteScheduleIntent(it.sequence) }
    val overlay = pending.filterNot { it.intent.scheduleId in synced.removed }.map { it.intent }
    val rows = linkedMapOf<app.posato.feature.schedules.domain.ScheduleId, StoredSchedule>()
    synced.live.forEach { rows[it.id] = StoredSchedule(it) }
    synced.refused.forEach { rows[it.id] = StoredSchedule(it, refused = true) }
    overlay.forEach { intent ->
        when (intent) {
            is ScheduleIntent.Put -> rows[intent.plan.id] = StoredSchedule(intent.plan, refused = rows[intent.plan.id]?.refused == true)
            is ScheduleIntent.Remove -> rows.remove(intent.scheduleId)
            else -> writePendingFact(intent)
        }
    }
    val before = readSnapshot().schedules.map { it.plan.id }
    before.filterNot { it in rows }.forEach { deleteScheduleEverywhere(it) }
    rows.values.forEach { writeSchedule(it.plan, it.refused) }
    writeSyncedFacts(synced) { key -> key.schedule in rows }
}

private suspend fun PosatoDatabase.writePendingFact(intent: ScheduleIntent) {
    when (intent) {
        is ScheduleIntent.Skip -> writeFact(FACT_SKIP, intent.key)
        is ScheduleIntent.End -> writeFact(FACT_END, intent.key)
        is ScheduleIntent.Break -> writeBreak(intent.key, intent.taken)
        is ScheduleIntent.BreakEnd -> writeBreakEnd(intent.key)
        is ScheduleIntent.Put, is ScheduleIntent.Remove -> Unit
    }
}

/** The workspace's facts for the plans shown here; a shared break also takes a tie, so replicas agree. */
private suspend fun PosatoDatabase.writeSyncedFacts(
    synced: SyncedSchedules,
    shown: (OccurrenceKey) -> Boolean,
) {
    synced.skips.filter(shown).forEach { writeFact(FACT_SKIP, it) }
    synced.ends.filter(shown).forEach { writeFact(FACT_END, it) }
    synced.breaks.filter { (key) -> shown(key) }.forEach { (key, taken) -> writeBreak(key, taken, shared = true) }
    synced.breakEnds.filter(shown).forEach { writeBreakEnd(it) }
}
