package app.posato.feature.enforcement

import app.posato.feature.sync.domain.SessionId

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

private const val HEX_DIGITS: String = "0123456789abcdef"
private const val NIBBLE_BITS: Int = 4
private const val NIBBLE_MASK: Int = 0x0F
private const val BYTE_MASK: Int = 0xFF
