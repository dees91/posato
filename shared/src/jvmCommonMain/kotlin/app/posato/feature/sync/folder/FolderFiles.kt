package app.posato.feature.sync.folder

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

internal const val TEMPORARY_PREFIX: String = ".tmp-"

internal enum class ExclusiveWrite {
    CREATED,
    EXISTS,
    FAILED,
}

internal object FolderFiles {
    private val random = SecureRandom()

    fun readOrNull(
        file: Path,
        maximumBytes: Int,
    ): ByteArray? {
        return try {
            if (!Files.isRegularFile(file) || Files.size(file) > maximumBytes) null else Files.readAllBytes(file)
        } catch (_: IOException) {
            null
        }
    }

    fun writeExclusive(
        file: Path,
        bytes: ByteArray,
    ): ExclusiveWrite {
        val directory = file.parent
        val temporary = directory.resolve(TEMPORARY_PREFIX + random.nextLong().toULong().toString(16))
        return try {
            Files.createDirectories(directory)
            Files.write(temporary, bytes)
            if (Files.exists(file)) {
                ExclusiveWrite.EXISTS
            } else {
                moveIntoPlace(temporary, file)
                ExclusiveWrite.CREATED
            }
        } catch (_: FileAlreadyExistsException) {
            ExclusiveWrite.EXISTS
        } catch (_: IOException) {
            ExclusiveWrite.FAILED
        } finally {
            deleteQuietly(temporary)
        }
    }

    fun replace(
        file: Path,
        bytes: ByteArray,
    ): Boolean {
        val temporary = file.resolveSibling(TEMPORARY_PREFIX + random.nextLong().toULong().toString(16))
        return try {
            Files.createDirectories(file.parent)
            Files.write(temporary, bytes)
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } catch (_: IOException) {
            false
        } finally {
            deleteQuietly(temporary)
        }
    }

    fun names(directory: Path): List<String>? {
        return try {
            if (!Files.isDirectory(directory)) {
                emptyList()
            } else {
                Files.list(directory).use { entries -> entries.map { it.fileName.toString() }.toList() }
            }
        } catch (_: IOException) {
            null
        }
    }

    fun deleteQuietly(file: Path): Boolean {
        return try {
            Files.deleteIfExists(file)
            true
        } catch (_: IOException) {
            false
        }
    }

    fun deleteTree(directory: Path): Boolean {
        return try {
            if (Files.exists(directory)) {
                Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun moveIntoPlace(
        temporary: Path,
        file: Path,
    ) {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file)
        }
    }
}

internal fun ByteArray.toHex(): String {
    return joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

internal fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0 || any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
