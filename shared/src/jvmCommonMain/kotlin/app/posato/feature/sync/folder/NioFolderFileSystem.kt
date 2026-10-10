package app.posato.feature.sync.folder

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

internal object NioFolderFileSystem : FolderFileSystem {
    override fun isDirectory(path: String): Boolean {
        return path.toPathOrNull()?.let(Files::isDirectory) == true
    }

    override fun isFile(path: String): Boolean {
        return path.toPathOrNull()?.let(Files::isRegularFile) == true
    }

    override fun read(
        path: String,
        maximumBytes: Int,
    ): ByteArray? {
        val file = path.toPathOrNull() ?: return null
        return try {
            if (!Files.isRegularFile(file) || Files.size(file) > maximumBytes) null else Files.readAllBytes(file)
        } catch (_: IOException) {
            null
        }
    }

    override fun writeExclusive(
        path: String,
        bytes: ByteArray,
    ): ExclusiveWrite {
        val file = path.toPathOrNull() ?: return ExclusiveWrite.FAILED
        val temporary = file.resolveSibling(temporaryName())
        return try {
            Files.createDirectories(file.parent)
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

    override fun delete(path: String): Boolean {
        return path.toPathOrNull()?.let(::deleteQuietly) == true
    }

    override fun deleteTree(path: String): Boolean {
        val directory = path.toPathOrNull() ?: return false
        return try {
            if (Files.exists(directory)) {
                Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
            }
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

    override fun restrictToOwner(path: String) {
        path.toPathOrNull()?.let { setPermissions(it, "rw-------") }
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

private fun String.toPathOrNull(): Path? {
    return try {
        Paths.get(this).takeIf { it.isAbsolute }
    } catch (_: InvalidPathException) {
        null
    }
}
