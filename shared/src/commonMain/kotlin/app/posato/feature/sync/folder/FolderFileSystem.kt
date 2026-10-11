package app.posato.feature.sync.folder

import kotlin.random.Random

internal const val TEMPORARY_PREFIX: String = ".tmp-"

internal enum class ExclusiveWrite {
    CREATED,
    EXISTS,
    FAILED,
}

internal sealed interface FileRead {
    class Found(
        val bytes: ByteArray,
    ) : FileRead

    data object Missing : FileRead

    data object Failed : FileRead
}

/**
 * The few file operations the folder transport needs, on absolute `/` paths:
 * `java.nio` on the JVM and Android, Foundation on iOS.
 */
internal interface FolderFileSystem :
    FolderReads,
    FolderWrites

internal interface FolderReads {
    fun isDirectory(path: String): Boolean

    fun isFile(path: String): Boolean

    /** Reads a whole file; a file that cannot be opened because it is gone is [FileRead.Missing], even when metadata still lists it. */
    fun readFile(
        path: String,
        maximumBytes: Int,
    ): FileRead

    fun read(
        path: String,
        maximumBytes: Int,
    ): ByteArray? {
        return (readFile(path, maximumBytes) as? FileRead.Found)?.bytes
    }

    fun names(directory: String): List<String>?

    /** The resolved absolute path, or null when it is not reachable now. */
    fun canonical(path: String): String?

    fun isWritableDirectory(path: String): Boolean
}

internal interface FolderWrites {
    /** Writes a temporary sibling and moves it into place only when [path] is absent. */
    fun writeExclusive(
        path: String,
        bytes: ByteArray,
    ): ExclusiveWrite

    fun replace(
        path: String,
        bytes: ByteArray,
    ): Boolean

    fun delete(path: String): Boolean

    fun deleteTree(path: String): Boolean

    fun createPrivateDirectories(path: String): Boolean

    fun createDirectories(path: String): Boolean

    fun restrictToOwner(path: String)
}

internal fun String.child(name: String): String {
    return if (endsWith("/")) this + name else "$this/$name"
}

internal fun String.parentPath(): String {
    return substringBeforeLast('/', "/").ifEmpty { "/" }
}

internal fun ByteArray.toHex(): String {
    return toHexString()
}

/** Only the lowercase form [toHex] writes is accepted, so one identifier has one file name. */
internal fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0 || any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return hexToByteArray()
}

internal fun temporaryName(): String {
    return TEMPORARY_PREFIX + Random.nextLong().toULong().toHexString()
}
