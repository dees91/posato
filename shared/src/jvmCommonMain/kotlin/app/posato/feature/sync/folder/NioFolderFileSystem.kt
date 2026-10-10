package app.posato.feature.sync.folder

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

internal object NioFolderFileSystem : FolderFileSystem, FolderReads by NioFolderReads, FolderWrites by NioFolderWrites

private object NioFolderReads : FolderReads {
    override fun isDirectory(path: String): Boolean {
        return path.toPathOrNull()?.let(Files::isDirectory) == true
    }

    override fun isFile(path: String): Boolean {
        return path.toPathOrNull()?.let(Files::isRegularFile) == true
    }

    override fun readFile(
        path: String,
        maximumBytes: Int,
    ): FileRead {
        val file = path.toPathOrNull() ?: return FileRead.Failed
        return try {
            when {
                !Files.isRegularFile(file) -> FileRead.Missing
                Files.size(file) > maximumBytes -> FileRead.Failed
                else -> FileRead.Found(Files.readAllBytes(file))
            }
        } catch (_: NoSuchFileException) {
            FileRead.Missing
        } catch (_: IOException) {
            FileRead.Failed
        }
    }

    override fun names(directory: String): List<String>? {
        val folder = directory.toPathOrNull() ?: return null
        return try {
            if (!Files.isDirectory(folder)) {
                emptyList()
            } else {
                Files.list(folder).use { entries -> entries.map { it.fileName.toString() }.toList() }
            }
        } catch (_: IOException) {
            null
        }
    }

    override fun canonical(path: String): String? {
        return try {
            path.toPathOrNull()?.toRealPath()?.toString()
        } catch (_: IOException) {
            null
        }
    }

    override fun isWritableDirectory(path: String): Boolean {
        val directory = path.toPathOrNull() ?: return false
        return Files.isDirectory(directory) && Files.isWritable(directory)
    }
}

private object NioFolderWrites : FolderWrites {
    override fun writeExclusive(
        path: String,
        bytes: ByteArray,
    ): ExclusiveWrite {
        val file = path.toPathOrNull() ?: return ExclusiveWrite.FAILED
        val temporary = file.resolveSibling(temporaryName())
        return try {
            Files.createDirectories(file.parent)
            Files.write(temporary, bytes)
            linkOrMoveIntoPlace(temporary, file)
        } catch (_: FileAlreadyExistsException) {
            ExclusiveWrite.EXISTS
        } catch (_: IOException) {
            ExclusiveWrite.FAILED
        } finally {
            deleteQuietly(temporary)
        }
    }

    override fun replace(
        path: String,
        bytes: ByteArray,
    ): Boolean {
        val file = path.toPathOrNull() ?: return false
        val temporary = file.resolveSibling(temporaryName())
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

    override fun delete(path: String): Boolean {
        return path.toPathOrNull()?.let(::deleteQuietly) == true
    }

    override fun deleteTree(path: String): Boolean {
        val directory = path.toPathOrNull() ?: return false
        return try {
            if (Files.exists(directory)) {
                Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
            true
        } catch (_: NoSuchFileException) {
            true
        } catch (_: IOException) {
            false
        }
    }

    override fun createPrivateDirectories(path: String): Boolean {
        val directory = path.toPathOrNull() ?: return false
        return try {
            Files.createDirectories(directory)
            setPermissions(directory, "rwx------")
            true
        } catch (_: IOException) {
            false
        }
    }

    override fun createDirectories(path: String): Boolean {
        val directory = path.toPathOrNull() ?: return false
        return try {
            Files.createDirectories(directory)
            true
        } catch (_: IOException) {
            false
        }
    }

    override fun restrictToOwner(path: String) {
        path.toPathOrNull()?.let { setPermissions(it, "rw-------") }
    }
}

/**
 * A hard link fails when [file] exists, so two local writers cannot both create it; a file system without hard links,
 * such as Android's shared storage, falls back to a check and a move.
 */
private fun linkOrMoveIntoPlace(
    temporary: Path,
    file: Path,
): ExclusiveWrite {
    val linked = try {
        Files.createLink(file, temporary)
        ExclusiveWrite.CREATED
    } catch (_: FileAlreadyExistsException) {
        ExclusiveWrite.EXISTS
    } catch (_: UnsupportedOperationException) {
        null
    } catch (_: IOException) {
        null
    }
    if (linked != null) return linked
    if (Files.exists(file)) return ExclusiveWrite.EXISTS
    try {
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary, file)
    }
    return ExclusiveWrite.CREATED
}

private fun setPermissions(
    path: Path,
    permissions: String,
) {
    try {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    } catch (_: UnsupportedOperationException) {
        return
    } catch (_: IOException) {
        return
    }
}

private fun deleteQuietly(file: Path): Boolean {
    return try {
        Files.deleteIfExists(file)
        true
    } catch (_: IOException) {
        false
    }
}

private fun String.toPathOrNull(): Path? {
    return try {
        Paths.get(this).takeIf { it.isAbsolute }
    } catch (_: InvalidPathException) {
        null
    }
}
