package app.posato.desktop.mappings

import app.posato.desktop.mappings.database.MacOsApplicationMappingsDatabase
import app.posato.feature.targets.data.ApplicationChoiceSet
import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import java.security.MessageDigest

/** [set]'s choices as stored, after which [files] are secured again. */
internal fun MacOsApplicationMappingsDatabase.snapshotOf(
    set: ApplicationChoiceSet,
    files: ApplicationMappingFiles,
): LocalApplicationMappingsSnapshot {
    val mappings = applicationMappingsQueries
        .selectForSet(set.hex.hexToByteArray(), ::restoreStoredMapping)
        .executeAsList()
    files.secureDatabaseArtifacts()

    return LocalApplicationMappingsSnapshot.restore(mappings) ?: throw CorruptApplicationMappingsException()
}

internal const val SHA256_BYTES: Int = 32
internal const val MAXIMUM_REQUIREMENT_BYTES: Int = 4_096

private fun restoreStoredMapping(
    mappingId: ByteArray,
    displayName: ByteArray,
    designatedRequirement: ByteArray,
): LocalApplicationMapping {
    if (mappingId.size != SHA256_BYTES || designatedRequirement.isEmpty() || designatedRequirement.size > MAXIMUM_REQUIREMENT_BYTES) {
        corruptApplicationMappings()
    }
    if (!mappingId.contentEquals(designatedRequirement.sha256())) {
        corruptApplicationMappings()
    }
    val restoredId = LocalApplicationMappingId.restore(mappingId.toHex()) ?: corruptApplicationMappings()
    val restoredName = try {
        displayName.decodeToString(throwOnInvalidSequence = true)
    } catch (_: Exception) {
        corruptApplicationMappings()
    }

    return LocalApplicationMapping.restore(restoredId, restoredName) ?: corruptApplicationMappings()
}

internal class CorruptApplicationMappingsException : Exception()

internal fun corruptApplicationMappings(): Nothing {
    throw CorruptApplicationMappingsException()
}

internal fun ByteArray.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this)
}

internal fun ByteArray.toHex(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun String.hexToByteArray(): ByteArray {
    return chunked(HEX_PAIR_LENGTH).map { pair -> pair.toInt(HEX_RADIX).toByte() }.toByteArray()
}

private const val HEX_PAIR_LENGTH: Int = 2
private const val HEX_RADIX: Int = 16
