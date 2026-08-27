package app.posato.persistence

import app.cash.sqldelight.db.SqlDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.O_RDONLY
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.open
import platform.posix.pread
import platform.posix.pwrite

private const val INVALID_DATABASE_MARKER: String = "not a sqlite database"

@OptIn(ExperimentalForeignApi::class)
internal actual fun createLocalPolicyTestDatabase(name: String): LocalPolicyTestDatabase {
    return IosLocalPolicyTestDatabase(name)
}

@OptIn(ExperimentalForeignApi::class)
private class IosLocalPolicyTestDatabase(
    private val name: String,
) : LocalPolicyTestDatabase {
    private val directory: String = "${NSTemporaryDirectory().trimEnd('/')}/posato-model-001-tests"
    private val path: String = "$directory/$name"

    init {
        delete()
    }

    override fun openDriver(): SqlDriver {
        return createIosDatabaseDriver(
            databaseName = name,
            directory = directory,
        )
    }

    override fun writeInvalidDatabase() {
        ensureDirectory()
        check(
            NSFileManager.defaultManager.createFileAtPath(
                path,
                contents = null,
                attributes = null,
            ),
        )
        val marker = INVALID_DATABASE_MARKER.encodeToByteArray()
        val descriptor = open(path, O_WRONLY)
        check(descriptor >= 0)
        try {
            val bytesWritten =
                marker.usePinned { pinned ->
                    pwrite(descriptor, pinned.addressOf(0), marker.size.toULong(), 0)
                }
            check(bytesWritten == marker.size.toLong())
        } finally {
            check(close(descriptor) == 0)
        }
    }

    override fun invalidDatabaseMarkerIsPresent(): Boolean {
        val marker = INVALID_DATABASE_MARKER.encodeToByteArray()
        val actual = ByteArray(marker.size + 1)
        val descriptor = open(path, O_RDONLY)
        check(descriptor >= 0)
        return try {
            val bytesRead =
                actual.usePinned { pinned ->
                    pread(descriptor, pinned.addressOf(0), actual.size.toULong(), 0)
                }
            bytesRead == marker.size.toLong() && actual.copyOf(marker.size).contentEquals(marker)
        } finally {
            check(close(descriptor) == 0)
        }
    }

    override fun delete() {
        val fileManager = NSFileManager.defaultManager
        listOf(path, "$path-journal", "$path-shm", "$path-wal").forEach { candidate ->
            if (fileManager.fileExistsAtPath(candidate)) {
                check(fileManager.removeItemAtPath(candidate, error = null))
            }
        }
    }

    private fun ensureDirectory() {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(directory)) {
            check(
                fileManager.createDirectoryAtPath(
                    directory,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                ),
            )
        }
    }
}
