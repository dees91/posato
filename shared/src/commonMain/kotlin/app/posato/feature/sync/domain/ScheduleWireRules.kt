package app.posato.feature.sync.domain

import app.posato.feature.schedules.domain.MINUTES_PER_DAY
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleLimits

/** The format-1 invariants of kinds 8, 10 and 11, shared by the writer, the encoder and the decoder. */
internal object ScheduleWireRules {
    private const val FIRST_YEAR = 2000
    private const val LAST_YEAR = 2100
    private const val ALL_WEEKDAYS = 0b1111111
    private const val LAST_MONTH = 12
    private const val LAST_DAY = 31

    fun isValid(put: SyncOperationPayload.SchedulePut): Boolean {
        val length = (put.endMinute - put.startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return isValidName(put.name) &&
            put.weekdays in 1..ALL_WEEKDAYS &&
            put.startMinute in 0 until MINUTES_PER_DAY &&
            put.endMinute in 0 until MINUTES_PER_DAY &&
            length >= ScheduleLimits.MIN_MINUTES
    }

    /** 1-80 bytes of UTF-8 with no Unicode control character (Cc: U+0000-001F and U+007F-009F). NFC is the writer's job. */
    fun isValidName(name: String): Boolean {
        val size = try {
            name.encodeToByteArray(throwOnInvalidSequence = true).size
        } catch (_: CharacterCodingException) {
            return false
        }
        return size in 1..ScheduleLimits.MAX_NAME_BYTES && name.none { it in '\u0000'..'\u001F' || it in '\u007F'..'\u009F' }
    }

    fun isValidDate(date: ScheduleDate): Boolean {
        return date.year in FIRST_YEAR..LAST_YEAR &&
            date.month in 1..LAST_MONTH &&
            date.day in 1..LAST_DAY &&
            ScheduleDate.ofEpochDay(date.epochDay) == date
    }
}
