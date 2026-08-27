package app.posato.persistence

import app.cash.sqldelight.db.SqlDriver
import java.nio.file.Files
import java.nio.file.Path

private const val INVALID_DATABASE_MARKER: String = "not a sqlite database"

internal actual fun createLocalPolicyTestDatabase(name: String): LocalPolicyTestDatabase {
    return JvmLocalPolicyTestDatabase(name)
}

private class JvmLocalPolicyTestDatabase(
    name: String,
) : LocalPolicyTestDatabase {
    private val directory: Path = Files.createTempDirectory("posato-model-001-")
    private val path: Path = directory.resolve(name)

    override fun openDriver(): SqlDriver {
        return createDesktopDatabaseDriver(path.toString())
    }

    override fun writeInvalidDatabase() {
        Files.writeString(path, INVALID_DATABASE_MARKER)
    }

    override fun invalidDatabaseMarkerIsPresent(): Boolean {
        return Files.readString(path) == INVALID_DATABASE_MARKER
    }

    override fun delete() {
        listOf(
            path,
            path.resolveSibling("${path.fileName}-journal"),
            path.resolveSibling("${path.fileName}-shm"),
            path.resolveSibling("${path.fileName}-wal"),
        ).forEach(Files::deleteIfExists)
        Files.deleteIfExists(directory)
    }
}
