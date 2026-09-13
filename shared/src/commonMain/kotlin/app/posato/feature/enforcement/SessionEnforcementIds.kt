package app.posato.feature.enforcement

import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncIdentifier

internal fun SessionId.reconciliationId(): String {
    val bytes = value.copyBytes()
    val digits = CharArray(bytes.size * 2)
    bytes.forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and BYTE_MASK
        digits[index * 2] = HEX_DIGITS[unsigned shr NIBBLE_BITS]
        digits[index * 2 + 1] = HEX_DIGITS[unsigned and NIBBLE_MASK]
    }
    return digits.concatToString()
}

internal fun sessionIdFromReconciliationId(value: String): SessionId? {
    if (value.length != RECONCILIATION_ID_LENGTH || value.any { char -> char.digitToIntOrNull(RADIX) == null }) {
        return null
    }
    val bytes = ByteArray(value.length / 2) { index ->
        ((value[index * 2].digitToInt(RADIX) shl NIBBLE_BITS) or value[index * 2 + 1].digitToInt(RADIX)).toByte()
    }
    return SyncIdentifier.fromUuidV4Bytes(bytes)?.let(::SessionId)
}

private const val RECONCILIATION_ID_LENGTH: Int = 32
private const val RADIX: Int = 16

private const val HEX_DIGITS: String = "0123456789abcdef"
private const val NIBBLE_BITS: Int = 4
private const val NIBBLE_MASK: Int = 0x0F
private const val BYTE_MASK: Int = 0xFF
