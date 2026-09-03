package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId

private const val IDENTIFIER_BYTES: Int = 16
private const val ACCOUNT_TEXT_BYTES: Int = 36
private const val CRC_BYTES: Int = 4
private const val UUID_SEGMENTS: Int = 5
private const val FIRST_SEGMENT_BYTES: Int = 4
private const val MIDDLE_SEGMENT_BYTES: Int = 2
private const val LAST_SEGMENT_BYTES: Int = 6
private const val MIDDLE_SEGMENTS: Int = 3
private const val UUID_VERSION_BYTE: Int = 6
private const val UUID_VARIANT_BYTE: Int = 8
private const val UUID_VERSION_MASK: Int = 0x0F
private const val UUID_VERSION_VALUE: Int = 0x40
private const val UUID_VARIANT_MASK: Int = 0x3F
private const val UUID_VARIANT_VALUE: Int = 0x80
private const val HEX_BYTE_MASK: Int = 0xFF
private const val HEX_NIBBLE_BITS: Int = 4
private const val HEX_NIBBLE_MASK: Int = 0x0F
private const val HEX_DIGITS: String = "0123456789abcdef"
private const val CRC_ROUNDS: Int = 8
private const val CRC_POLYNOMIAL: Int = -0x12477CE0
private const val CHECKSUM_BYTE_BITS: Int = 8
private const val CHECKSUM_MSB_SHIFT: Int = 24
private const val TRANSPORT_EPOCH_OFFSET: Int = IDENTIFIER_BYTES
private const val KEY_EPOCH_OFFSET: Int = 2 * IDENTIFIER_BYTES
private const val WORKSPACE_KEY_OFFSET: Int = 3 * IDENTIFIER_BYTES
private const val CHECKSUM_OFFSET: Int = WORKSPACE_KEY_OFFSET + WORKSPACE_KEY_BYTES

internal data class DecodedKeyItem(
    val workspaceId: WorkspaceId,
    val transportEpochId: TransportEpochId,
    val keyEpochId: KeyEpochId,
    val workspaceKey: ByteArray
) {
    fun clear() {
        workspaceKey.fill(0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedKeyItem) return false
        return workspaceId == other.workspaceId &&
            transportEpochId == other.transportEpochId &&
            keyEpochId == other.keyEpochId &&
            workspaceKey.contentEquals(other.workspaceKey)
    }

    override fun hashCode(): Int {
        var result = workspaceId.hashCode()
        result = 31 * result + transportEpochId.hashCode()
        result = 31 * result + keyEpochId.hashCode()
        result = 31 * result + workspaceKey.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "DecodedKeyItem(redacted)"
    }
}

internal object BootstrapEncoding {
    fun identifierToAccountText(identifier: SyncIdentifier): String {
        val bytes = identifier.copyBytes()
        return buildString(ACCOUNT_TEXT_BYTES) {
            appendSegment(bytes, 0, FIRST_SEGMENT_BYTES)
            var offset = FIRST_SEGMENT_BYTES
            repeat(MIDDLE_SEGMENTS) {
                append('-')
                appendSegment(bytes, offset, MIDDLE_SEGMENT_BYTES)
                offset += MIDDLE_SEGMENT_BYTES
            }
            append('-')
            appendSegment(bytes, offset, LAST_SEGMENT_BYTES)
        }
    }

    fun isCanonicalAccountText(text: String): Boolean {
        if (text.length != ACCOUNT_TEXT_BYTES) {
            return false
        }
        var segment = 0
        var segmentLength = 0
        text.forEach { char ->
            if (char == '-') {
                if (segmentLength == 0 || segment >= UUID_SEGMENTS - 1) {
                    return false
                }
                segment += 1
                segmentLength = 0
            } else if (char in '0'..'9' || char in 'a'..'f') {
                segmentLength += 1
            } else {
                return false
            }
        }
        return segment == UUID_SEGMENTS - 1 && segmentLength > 0
    }

    fun applyUuidVersionFour(bytes: ByteArray): SyncIdentifier? {
        if (bytes.size != IDENTIFIER_BYTES) {
            return null
        }
        val fixed = bytes.copyOf()
        fixed[UUID_VERSION_BYTE] = ((fixed[UUID_VERSION_BYTE].toInt() and UUID_VERSION_MASK) or UUID_VERSION_VALUE).toByte()
        fixed[UUID_VARIANT_BYTE] = ((fixed[UUID_VARIANT_BYTE].toInt() and UUID_VARIANT_MASK) or UUID_VARIANT_VALUE).toByte()
        return SyncIdentifier.fromUuidV4Bytes(fixed)
    }

    fun encodeKeyItem(
        workspaceId: WorkspaceId,
        transportEpochId: TransportEpochId,
        keyEpochId: KeyEpochId,
        workspaceKey: ByteArray
    ): WorkspaceKeyItem? {
        if (workspaceKey.size != WORKSPACE_KEY_BYTES) {
            return null
        }
        val result = ByteArray(KEYCHAIN_ITEM_BYTES)
        workspaceId.value.copyBytes().copyInto(result, 0)
        transportEpochId.value.copyBytes().copyInto(result, TRANSPORT_EPOCH_OFFSET)
        keyEpochId.value.copyBytes().copyInto(result, KEY_EPOCH_OFFSET)
        workspaceKey.copyInto(result, WORKSPACE_KEY_OFFSET)
        writeChecksum(result, CHECKSUM_OFFSET, crc32(result, CHECKSUM_OFFSET))
        return WorkspaceKeyItem.fromBytes(result)
    }

    fun decodeKeyItem(value: WorkspaceKeyItem): DecodedKeyItem? {
        val bytes = value.copyBytes()
        if (bytes.size != KEYCHAIN_ITEM_BYTES) {
            return null
        }
        if (readChecksum(bytes, CHECKSUM_OFFSET) != crc32(bytes, CHECKSUM_OFFSET)) {
            return null
        }
        val workspaceId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(0, IDENTIFIER_BYTES)) ?: return null
        val transportEpochId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(TRANSPORT_EPOCH_OFFSET, KEY_EPOCH_OFFSET))
            ?: return null
        val keyEpochId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(KEY_EPOCH_OFFSET, WORKSPACE_KEY_OFFSET))
            ?: return null
        return DecodedKeyItem(
            workspaceId = WorkspaceId(workspaceId),
            transportEpochId = TransportEpochId(transportEpochId),
            keyEpochId = KeyEpochId(keyEpochId),
            workspaceKey = bytes.copyOfRange(WORKSPACE_KEY_OFFSET, CHECKSUM_OFFSET),
        )
    }

    fun constantTimeEquals(
        left: ByteArray,
        right: ByteArray
    ): Boolean {
        if (left.size != right.size) {
            return false
        }
        var difference = 0
        left.indices.forEach { index ->
            difference = difference or (left[index].toInt() xor right[index].toInt())
        }
        return difference == 0
    }

    fun crc32(
        bytes: ByteArray,
        length: Int
    ): Int {
        var crc = -1
        for (index in 0 until length) {
            crc = crc xor (bytes[index].toInt() and HEX_BYTE_MASK)
            repeat(CRC_ROUNDS) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor CRC_POLYNOMIAL else crc ushr 1
            }
        }
        return crc.inv()
    }

    private fun writeChecksum(
        bytes: ByteArray,
        offset: Int,
        checksum: Int
    ) {
        repeat(CRC_BYTES) { index ->
            bytes[offset + index] = (checksum ushr (CHECKSUM_MSB_SHIFT - index * CHECKSUM_BYTE_BITS)).toByte()
        }
    }

    private fun readChecksum(
        bytes: ByteArray,
        offset: Int
    ): Int {
        var checksum = 0
        repeat(CRC_BYTES) { index ->
            checksum = (checksum shl CHECKSUM_BYTE_BITS) or (bytes[offset + index].toInt() and HEX_BYTE_MASK)
        }
        return checksum
    }

    private fun StringBuilder.appendSegment(
        bytes: ByteArray,
        offset: Int,
        length: Int
    ) {
        repeat(length) { position ->
            appendHexByte(bytes[offset + position])
        }
    }

    private fun StringBuilder.appendHexByte(byte: Byte) {
        val value = byte.toInt() and HEX_BYTE_MASK
        append(HEX_DIGITS[value ushr HEX_NIBBLE_BITS])
        append(HEX_DIGITS[value and HEX_NIBBLE_MASK])
    }
}
