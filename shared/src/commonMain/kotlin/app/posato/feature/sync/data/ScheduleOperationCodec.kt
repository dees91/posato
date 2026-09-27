package app.posato.feature.sync.data

import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.sync.domain.ScheduleOccurrenceRef
import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperationPayload

private const val SCHEDULE_PUT_TAG = 8
private const val SCHEDULE_REMOVE_TAG = 9
private const val SCHEDULE_SKIP_TAG = 10
private const val SCHEDULE_OCCURRENCE_END_TAG = 11

internal val SCHEDULE_TAGS: IntRange = SCHEDULE_PUT_TAG..SCHEDULE_OCCURRENCE_END_TAG

/** Kinds 8-11 and the optional kinds, in the ADR 0006 schedule amendment's layout. Returns false for a payload the rules refuse. */
internal fun CanonicalWriter.writeSchedulePayload(payload: SyncOperationPayload): Boolean {
    return when (payload) {
        is SyncOperationPayload.SchedulePut -> {
            writeSchedulePut(payload)
        }

        is SyncOperationPayload.ScheduleRemove -> {
            writeByte(SCHEDULE_REMOVE_TAG)
            writeOwnedBytes(payload.scheduleId.value.copyBytes())
            true
        }

        is SyncOperationPayload.ScheduleSkip -> {
            writeOccurrence(SCHEDULE_SKIP_TAG, payload.occurrence)
        }

        is SyncOperationPayload.ScheduleOccurrenceEnd -> {
            writeOccurrence(SCHEDULE_OCCURRENCE_END_TAG, payload.occurrence)
        }

        is SyncOperationPayload.OptionalExtension -> {
            writeByte(payload.kind)
            writeOwnedBytes(payload.tail.copyBytes())
            true
        }

        else -> {
            false
        }
    }
}

internal fun CanonicalReader.readSchedulePayload(tag: Int): SyncOperationPayload? {
    return when (tag) {
        SCHEDULE_PUT_TAG -> readSchedulePut()
        SCHEDULE_REMOVE_TAG -> readUuidIdentifier()?.let(::ScheduleSyncId)?.let(SyncOperationPayload::ScheduleRemove)
        SCHEDULE_SKIP_TAG -> readOccurrence()?.let(SyncOperationPayload::ScheduleSkip)
        SCHEDULE_OCCURRENCE_END_TAG -> readOccurrence()?.let(SyncOperationPayload::ScheduleOccurrenceEnd)
        in SyncFormatLimits.OPTIONAL_KINDS -> readOptionalExtension(tag)
        else -> null
    }
}

private fun CanonicalWriter.writeSchedulePut(payload: SyncOperationPayload.SchedulePut): Boolean {
    if (!ScheduleWireRules.isValid(payload)) {
        return false
    }
    writeByte(SCHEDULE_PUT_TAG)
    writeOwnedBytes(payload.scheduleId.value.copyBytes())
    writeString(payload.name)
    writeByte(payload.weekdays)
    writeU16(payload.startMinute)
    writeU16(payload.endMinute)
    writeByte(if (payload.enabled) 1 else 0)
    return true
}

private fun CanonicalWriter.writeOccurrence(
    tag: Int,
    occurrence: ScheduleOccurrenceRef,
): Boolean {
    if (!ScheduleWireRules.isValidDate(occurrence.date)) {
        return false
    }
    writeByte(tag)
    writeOwnedBytes(occurrence.scheduleId.value.copyBytes())
    writeU16(occurrence.date.year)
    writeByte(occurrence.date.month)
    writeByte(occurrence.date.day)
    return true
}

private fun CanonicalReader.readSchedulePut(): SyncOperationPayload.SchedulePut? {
    val scheduleId = readUuidIdentifier()?.let(::ScheduleSyncId)
    val name = readCanonicalString()
    val weekdays = readByte()
    val startMinute = readU16()
    val endMinute = readU16()
    val enabled = readByte()?.takeIf { value -> value == 0 || value == 1 }
    val complete = listOf(scheduleId, name, weekdays, startMinute, endMinute, enabled).all { it != null }
    return if (complete) {
        SyncOperationPayload.SchedulePut(
            checkNotNull(scheduleId),
            checkNotNull(name),
            checkNotNull(weekdays),
            checkNotNull(startMinute),
            checkNotNull(endMinute),
            enabled == 1,
        ).takeIf(ScheduleWireRules::isValid)
    } else {
        null
    }
}

private fun CanonicalReader.readOccurrence(): ScheduleOccurrenceRef? {
    val scheduleId = readUuidIdentifier()?.let(::ScheduleSyncId)
    val year = readU16()
    val month = readByte()
    val day = readByte()
    val complete = listOf(scheduleId, year, month, day).all { it != null }
    return if (complete) {
        ScheduleOccurrenceRef(checkNotNull(scheduleId), ScheduleDate(checkNotNull(year), checkNotNull(month), checkNotNull(day)))
            .takeIf { occurrence -> ScheduleWireRules.isValidDate(occurrence.date) }
    } else {
        null
    }
}

/** An optional kind keeps every remaining plaintext byte, so it re-encodes exactly and every replica ignores it alike. */
private fun CanonicalReader.readOptionalExtension(kind: Int): SyncOperationPayload.OptionalExtension? {
    return readBytes(remaining)?.let { tail -> SyncOperationPayload.OptionalExtension(kind, ImmutableBytes(tail)) }
}
