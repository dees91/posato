package app.posato.feature.schedules.domain

import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.SyncIdentifier
import kotlin.random.Random

internal fun interface ScheduleIdGenerator {
    fun create(): ScheduleId
}

/** New schedules get random UUIDv4 identifiers, like sessions, so they are valid on the wire. */
internal object RandomScheduleIdGenerator : ScheduleIdGenerator {
    override fun create(): ScheduleId {
        val bytes = Random.Default.nextBytes(ByteArray(ID_BYTES))
        bytes[VERSION_INDEX] = (bytes[VERSION_INDEX].toInt() and VERSION_MASK or VERSION_VALUE).toByte()
        bytes[VARIANT_INDEX] = (bytes[VARIANT_INDEX].toInt() and VARIANT_MASK or VARIANT_VALUE).toByte()
        return scheduleIdOf(bytes)
    }
}

internal fun scheduleIdOf(bytes: ByteArray): ScheduleId {
    return ScheduleId(bytes.joinToString("") { byte -> (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') })
}

internal fun ScheduleId.toBytes(): ByteArray {
    return ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(HEX_RADIX).toByte() }
}

/** The wire identifier, or null when this identifier is not a UUIDv4 and so can never be synchronized. */
internal fun ScheduleId.toSync(): ScheduleSyncId? {
    return SyncIdentifier.fromUuidV4Bytes(toBytes())?.let(::ScheduleSyncId)
}

internal fun ScheduleSyncId.toScheduleId(): ScheduleId {
    return ScheduleId(hex)
}

private const val ID_BYTES: Int = 16
private const val VERSION_INDEX: Int = 6
private const val VARIANT_INDEX: Int = 8
private const val VERSION_MASK: Int = 0x0F
private const val VERSION_VALUE: Int = 0x40
private const val VARIANT_MASK: Int = 0x3F
private const val VARIANT_VALUE: Int = 0x80
private const val BYTE_MASK: Int = 0xFF
private const val HEX_RADIX: Int = 16
