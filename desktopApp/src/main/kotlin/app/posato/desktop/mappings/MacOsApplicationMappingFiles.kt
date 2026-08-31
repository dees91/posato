package app.posato.desktop.mappings

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal

internal interface ApplicationMappingFiles {
    val path: Path

    fun prepare()

    fun secureDatabaseArtifacts()
}

internal class MacOsApplicationMappingFiles(
    private val databasePath: Path = defaultDatabasePath(),
    private val expectedOwner: UserPrincipal = Files.getOwner(Path.of(checkNotNull(System.getProperty("user.home")))),
) : ApplicationMappingFiles {
    override val path: Path
        get() = databasePath

    override fun prepare() {
        val directory = checkNotNull(databasePath.parent)
        Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS))
        secureDirectory(directory)
        if (Files.notExists(databasePath, LinkOption.NOFOLLOW_LINKS)) {
            Files.createFile(databasePath, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
        }
        secureFile(databasePath)
    }

    override fun secureDatabaseArtifacts() {
        secureFile(databasePath)
        listOf("-journal", "-wal", "-shm").forEach { suffix ->
            val candidate = databasePath.resolveSibling("${databasePath.fileName}$suffix")
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                secureFile(candidate)
            }
        }
    }

    private fun secureDirectory(directory: Path) {
        check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        check(Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS) == expectedOwner)
        Files.setPosixFilePermissions(directory, DIRECTORY_PERMISSIONS)
        check(Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS) == DIRECTORY_PERMISSIONS)
    }

    private fun secureFile(file: Path) {
        check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
        check(Files.getOwner(file, LinkOption.NOFOLLOW_LINKS) == expectedOwner)
        Files.setPosixFilePermissions(file, FILE_PERMISSIONS)
        check(Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS) == FILE_PERMISSIONS)
    }

    private companion object {
        val DIRECTORY_PERMISSIONS: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")
        val FILE_PERMISSIONS: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")

        fun defaultDatabasePath(): Path {
            val homeDirectory = checkNotNull(System.getProperty("user.home"))

            return Path.of(
                homeDirectory,
                "Library",
                "Application Support",
                "Posato",
                "macos-application-mappings.db",
            )
        }
    }
}
