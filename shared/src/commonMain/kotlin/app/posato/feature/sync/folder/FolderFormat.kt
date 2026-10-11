package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.BootstrapEncoding
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId

internal const val BUNDLE_SUFFIX: String = ".pbundle"
private const val ANCHOR_VERSION: Int = 1
private const val ANCHOR_IDENTIFIERS: Int = 3
private const val BYTE_MASK: Int = 0xFF
private val anchorMagic = "PSW1".encodeToByteArray()

/** The bundle identifier a file name carries in the one form Posato writes, or null for any other file. */
internal fun bundleIdentifierOrNull(fileName: String): String? {
    if (!fileName.endsWith(BUNDLE_SUFFIX)) return null
    val name = fileName.removeSuffix(BUNDLE_SUFFIX)
    return name.takeIf { it.length == SyncFormatLimits.IDENTIFIER_BYTES * 2 && it.hexToBytesOrNull() != null }
}

/** The `workspace` file: magic, version, the workspace, transport epoch, and key epoch identifiers, and a CRC-32. */
internal fun encodeAnchor(anchor: WorkspaceAnchor): ByteArray {
    val body = anchorMagic + byteArrayOf(ANCHOR_VERSION.toByte()) + anchor.workspaceId.value.copyBytes() +
        anchor.transportEpochId.value.copyBytes() + anchor.keyEpochId.value.copyBytes()
    val checksum = BootstrapEncoding.crc32(body, body.size)
    return body + ByteArray(Int.SIZE_BYTES) { index -> (checksum ushr (Int.SIZE_BITS - Byte.SIZE_BITS * (index + 1))).toByte() }
}

internal fun decodeAnchor(bytes: ByteArray): WorkspaceAnchor? {
    val identifierBytes = SyncFormatLimits.IDENTIFIER_BYTES
    val start = anchorMagic.size + 1
    val bodySize = start + identifierBytes * ANCHOR_IDENTIFIERS
    if (bytes.size != bodySize + Int.SIZE_BYTES) return null
    if (!bytes.copyOf(anchorMagic.size).contentEquals(anchorMagic) || bytes[anchorMagic.size].toInt() != ANCHOR_VERSION) return null
    val stored = (0 until Int.SIZE_BYTES).fold(0) { value, index -> (value shl Byte.SIZE_BITS) or (bytes[bodySize + index].toInt() and BYTE_MASK) }
    if (BootstrapEncoding.crc32(bytes, bodySize) != stored) return null
    val identifiers = (0 until ANCHOR_IDENTIFIERS).map { index ->
        val offset = start + index * identifierBytes
        SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(offset, offset + identifierBytes)) ?: return null
    }
    return WorkspaceAnchor(WorkspaceId(identifiers[0]), TransportEpochId(identifiers[1]), KeyEpochId(identifiers[2]))
}
