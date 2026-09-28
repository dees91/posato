package app.posato.feature.schedules.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.ScheduleDate

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
    val yesterday = today.plusDays(-1)
    val kept = snapshot.schedules.map { it.plan.id }.toSet() - synced.removed
    snapshot.facts.skipped.filter { it.schedule in kept && it.date >= yesterday && it !in synced.skips }
        .forEach { key -> writeIntent(workspaceId, ScheduleIntent.Skip(key, today)) }
    snapshot.facts.ended.filter { it.schedule in kept && it.date >= yesterday && it !in synced.ends }
        .forEach { key -> writeIntent(workspaceId, ScheduleIntent.End(key, today)) }
    scheduleQueries.insertSeed(workspaceId)
    return true
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
            is ScheduleIntent.Skip -> writeFact(FACT_SKIP, intent.key)
            is ScheduleIntent.End -> writeFact(FACT_END, intent.key)
        }
    }
    val before = readSnapshot().schedules.map { it.plan.id }
    before.filterNot { it in rows }.forEach { deleteScheduleEverywhere(it) }
    rows.values.forEach { writeSchedule(it.plan, it.refused) }
    synced.skips.filter { it.schedule in rows }.forEach { writeFact(FACT_SKIP, it) }
    synced.ends.filter { it.schedule in rows }.forEach { writeFact(FACT_END, it) }
}
