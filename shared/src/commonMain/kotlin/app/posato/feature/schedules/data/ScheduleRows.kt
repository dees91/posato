package app.posato.feature.schedules.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.OccurrencePin
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleFacts
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.domain.scheduleIdOf
import app.posato.feature.schedules.domain.toBytes

internal const val FACT_SKIP: String = "skip"
internal const val FACT_END: String = "end"
private const val INTENT_PUT = "put"
private const val INTENT_REMOVE = "remove"

internal class ScheduleStoreException(
    val reason: ScheduleStoreFailure,
) : Exception()

internal suspend fun PosatoDatabase.readSnapshot(): ScheduleSnapshot {
    val schedules = scheduleQueries.selectSchedules { id, name, weekdays, start, end, enabled, refused ->
        StoredSchedule(SchedulePlan(scheduleIdOf(id), name, weekdays.toInt(), start.toInt(), end.toInt(), enabled == 1L), refused == 1L)
    }.awaitAsList()
    val skipped = mutableSetOf<OccurrenceKey>()
    val ended = mutableSetOf<OccurrenceKey>()
    scheduleQueries.selectFacts { id, kind, year, month, day ->
        (if (kind == FACT_SKIP) skipped else ended) += OccurrenceKey(scheduleIdOf(id), ScheduleDate(year.toInt(), month.toInt(), day.toInt()))
    }.awaitAsList()
    val pins = scheduleQueries.selectPins { id, year, month, day, start, notices ->
        OccurrencePin(OccurrenceKey(scheduleIdOf(id), ScheduleDate(year.toInt(), month.toInt(), day.toInt())), start, notices.toInt())
    }.awaitAsList()
    val expired = scheduleExpiryQueries.selectExpiries { id, year, month, day, end ->
        OccurrenceKey(scheduleIdOf(id), ScheduleDate(year.toInt(), month.toInt(), day.toInt())) to end
    }.awaitAsList().toMap()
    return ScheduleSnapshot(schedules, ScheduleFacts(skipped, ended, expired), pins)
}

/** Whether a scheduled pause runs on this device, so an update waits for it like for a manual session. */
internal suspend fun PosatoDatabase.hasRunningSchedule(nowEpochMillis: Long): Boolean {
    return scheduleQueries.countRunningPins(nowEpochMillis).awaitAsOne() > 0
}

internal suspend fun PosatoDatabase.liveScheduleCount(): Long {
    return scheduleQueries.countLiveSchedules().awaitAsOne()
}

internal suspend fun PosatoDatabase.writeSchedule(
    plan: SchedulePlan,
    refused: Boolean,
) {
    scheduleQueries.upsertSchedule(
        plan.id.toBytes(),
        plan.name,
        plan.weekdays.toLong(),
        plan.startMinute.toLong(),
        plan.endMinute.toLong(),
        if (plan.enabled) 1L else 0L,
        if (refused) 1L else 0L,
    )
}

/** Deletes a schedule with everything recorded about it on this device. */
internal suspend fun PosatoDatabase.deleteScheduleEverywhere(id: ScheduleId) {
    val bytes = id.toBytes()
    scheduleQueries.deleteSchedule(bytes)
    scheduleQueries.deleteFactsForSchedule(bytes)
    scheduleQueries.deletePinsForSchedule(bytes)
    scheduleExpiryQueries.deleteExpiriesForSchedule(bytes)
}

internal suspend fun PosatoDatabase.writeFact(
    kind: String,
    key: OccurrenceKey,
) {
    scheduleQueries.insertFact(key.schedule.toBytes(), kind, key.date.year.toLong(), key.date.month.toLong(), key.date.day.toLong())
}

internal suspend fun PosatoDatabase.writeHostUpdate(update: ScheduleHostUpdate) {
    update.pins.forEach { pin ->
        val date = pin.key.date
        scheduleQueries.insertPinIfAbsent(
            pin.key.schedule.toBytes(),
            date.year.toLong(),
            date.month.toLong(),
            date.day.toLong(),
            pin.startEpochMillis,
        )
    }
    update.notices.forEach { (key, bits) ->
        scheduleQueries.addPinNotices(bits.toLong(), key.schedule.toBytes(), key.date.year.toLong(), key.date.month.toLong(), key.date.day.toLong())
    }
    update.expired.forEach { (key, end) ->
        scheduleExpiryQueries.recordExpiry(key.schedule.toBytes(), key.date.year.toLong(), key.date.month.toLong(), key.date.day.toLong(), end)
    }
    update.released.forEach { key ->
        scheduleQueries.deletePin(key.schedule.toBytes(), key.date.year.toLong(), key.date.month.toLong(), key.date.day.toLong())
    }
    update.forgotten.forEach { key ->
        scheduleExpiryQueries.deleteExpiry(key.schedule.toBytes(), key.date.year.toLong(), key.date.month.toLong(), key.date.day.toLong())
    }
}

internal suspend fun PosatoDatabase.writeIntent(
    workspaceId: ByteArray,
    intent: ScheduleIntent,
) {
    val plan = (intent as? ScheduleIntent.Put)?.plan
    val (key, author) = when (intent) {
        is ScheduleIntent.Skip -> intent.key to intent.authorDate
        is ScheduleIntent.End -> intent.key to intent.authorDate
        else -> null to null
    }
    scheduleQueries.insertScheduleIntent(
        workspaceId = workspaceId,
        kind = intent.kind(),
        scheduleId = intent.scheduleId.toBytes(),
        name = plan?.name,
        weekdays = plan?.weekdays?.toLong(),
        startMinute = plan?.startMinute?.toLong(),
        endMinute = plan?.endMinute?.toLong(),
        enabled = plan?.let { if (it.enabled) 1L else 0L },
        year = key?.date?.year?.toLong(),
        month = key?.date?.month?.toLong(),
        day = key?.date?.day?.toLong(),
        authorYear = author?.year?.toLong(),
        authorMonth = author?.month?.toLong(),
        authorDay = author?.day?.toLong(),
    )
}

internal suspend fun PosatoDatabase.readIntentRows(): List<SequencedScheduleIntent> {
    return scheduleQueries.selectScheduleIntents {
        sequence,
        workspace,
        kind,
        id,
        name,
        weekdays,
        start,
        end,
        enabled,
        year,
        month,
        day,
        authorYear,
        authorMonth,
        authorDay,
        ->
        val scheduleId = scheduleIdOf(id)
        val date = if (year != null && month != null && day != null) ScheduleDate(year.toInt(), month.toInt(), day.toInt()) else null
        val author = if (authorYear != null && authorMonth != null && authorDay != null) {
            ScheduleDate(authorYear.toInt(), authorMonth.toInt(), authorDay.toInt())
        } else {
            null
        }
        val intent = when (kind) {
            INTENT_PUT -> ScheduleIntent.Put(
                SchedulePlan(
                    scheduleId,
                    checkNotNull(name),
                    checkNotNull(weekdays).toInt(),
                    checkNotNull(start).toInt(),
                    checkNotNull(end).toInt(),
                    enabled == 1L,
                ),
            )

            INTENT_REMOVE -> ScheduleIntent.Remove(scheduleId)

            FACT_SKIP -> ScheduleIntent.Skip(OccurrenceKey(scheduleId, checkNotNull(date)), checkNotNull(author))

            else -> ScheduleIntent.End(OccurrenceKey(scheduleId, checkNotNull(date)), checkNotNull(author))
        }
        SequencedScheduleIntent(sequence, workspace, intent)
    }.awaitAsList()
}

private fun ScheduleIntent.kind(): String {
    return when (this) {
        is ScheduleIntent.Put -> INTENT_PUT
        is ScheduleIntent.Remove -> INTENT_REMOVE
        is ScheduleIntent.Skip -> FACT_SKIP
        is ScheduleIntent.End -> FACT_END
    }
}
