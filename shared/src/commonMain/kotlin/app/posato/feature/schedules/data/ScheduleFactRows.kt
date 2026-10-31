package app.posato.feature.schedules.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleBreak
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleFacts
import app.posato.feature.schedules.domain.scheduleIdOf
import app.posato.feature.schedules.domain.toBytes

/** Adds this device's break facts to [facts]: each occurrence's break, the breaks ended early, and those seen end. */
internal suspend fun PosatoDatabase.readBreakFacts(facts: ScheduleFacts): ScheduleFacts {
    val breaks = scheduleQueries.selectBreaks { id, year, month, day, start, minutes ->
        occurrenceKeyOf(id, year, month, day) to ScheduleBreak(start, minutes.toInt())
    }.awaitAsList().toMap()
    val breakEnds = scheduleQueries.selectBreakEnds(::occurrenceKeyOf).awaitAsList().toSet()
    val breaksOver = scheduleQueries.selectBreaksOver(::occurrenceKeyOf).awaitAsList().toSet()
    return facts.copy(breaks = breaks, breakEnds = breakEnds, breaksOver = breaksOver)
}

/** Records [fact] for [key] in this device's own facts. */
internal suspend fun PosatoDatabase.writeOccurrenceFact(
    key: OccurrenceKey,
    fact: OccurrenceFact,
) {
    when (fact) {
        OccurrenceFact.Skip -> writeFact(FACT_SKIP, key)
        OccurrenceFact.End -> writeFact(FACT_END, key)
        is OccurrenceFact.Break -> writeBreak(key, fact.taken)
        OccurrenceFact.BreakEnd -> writeBreakEnd(key)
    }
}

/** The sync intent that publishes [this] fact for [key]. */
internal fun OccurrenceFact.intentFor(
    key: OccurrenceKey,
    authorDate: ScheduleDate,
): ScheduleIntent {
    return when (this) {
        OccurrenceFact.Skip -> ScheduleIntent.Skip(key, authorDate)
        OccurrenceFact.End -> ScheduleIntent.End(key, authorDate)
        is OccurrenceFact.Break -> ScheduleIntent.Break(key, authorDate, taken)
        OccurrenceFact.BreakEnd -> ScheduleIntent.BreakEnd(key, authorDate)
    }
}

/**
 * Records [taken] for [key]; the earliest start known wins, and [shared] also takes a tie so replicas agree. A break
 * not [shared] was taken on this device, which keeps it running here after unlinking.
 */
internal suspend fun PosatoDatabase.writeBreak(
    key: OccurrenceKey,
    taken: ScheduleBreak,
    shared: Boolean = false,
) {
    val id = key.schedule.toBytes()
    val (year, month, day) = key.date.columns()
    val minutes = taken.minutes.toLong()
    scheduleQueries.insertBreak(id, year, month, day, taken.startEpochMillis, minutes)
    if (shared) {
        scheduleQueries.takeSharedBreak(
            startEpochMillis = taken.startEpochMillis,
            minutes = minutes,
            scheduleId = id,
            year = year,
            month = month,
            day = day,
        )
    } else {
        scheduleQueries.lowerBreakStart(
            startEpochMillis = taken.startEpochMillis,
            minutes = minutes,
            scheduleId = id,
            year = year,
            month = month,
            day = day,
        )
    }
}

internal suspend fun PosatoDatabase.writeBreakEnd(key: OccurrenceKey) {
    val (year, month, day) = key.date.columns()
    scheduleQueries.insertBreakEnd(key.schedule.toBytes(), year, month, day)
}

private fun ScheduleDate.columns(): Triple<Long, Long, Long> {
    return Triple(year.toLong(), month.toLong(), day.toLong())
}

internal suspend fun PosatoDatabase.writeFact(
    kind: String,
    key: OccurrenceKey,
) {
    scheduleQueries.insertFact(key.schedule.toBytes(), kind, key.date.year.toLong(), key.date.month.toLong(), key.date.day.toLong())
}

internal suspend fun PosatoDatabase.deleteBreakRows(scheduleId: ByteArray) {
    scheduleQueries.deleteBreaksForSchedule(scheduleId)
    scheduleQueries.deleteBreakEndsForSchedule(scheduleId)
    scheduleQueries.deleteBreaksOverForSchedule(scheduleId)
}

/** Records the breaks the host saw end and drops those of dates it no longer needs. */
internal suspend fun PosatoDatabase.writeBreaksOver(update: ScheduleHostUpdate) {
    update.breaksOver.forEach { key ->
        val (year, month, day) = key.date.columns()
        scheduleQueries.insertBreakOver(key.schedule.toBytes(), year, month, day)
    }
    update.breaksOverBefore?.let { date ->
        scheduleQueries.deleteBreaksOverBefore(date.year * DATE_YEAR_FACTOR + date.month * DATE_MONTH_FACTOR + date.day.toLong())
    }
}

private fun occurrenceKeyOf(
    id: ByteArray,
    year: Long,
    month: Long,
    day: Long,
): OccurrenceKey {
    return OccurrenceKey(scheduleIdOf(id), ScheduleDate(year.toInt(), month.toInt(), day.toInt()))
}

private const val DATE_YEAR_FACTOR: Long = 10_000
private const val DATE_MONTH_FACTOR: Long = 100

/** Runs inside the unlink transaction: a break received from the workspace ends here at once. */
internal suspend fun PosatoDatabase.endReceivedBreaksAfterUnlink() {
    scheduleQueries.endReceivedBreaks()
}
