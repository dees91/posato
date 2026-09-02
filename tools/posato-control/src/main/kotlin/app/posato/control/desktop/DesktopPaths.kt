package app.posato.control.desktop

import java.nio.file.Path

object DesktopPaths {
    private val supportDirectory: Path = Path.of(System.getProperty("user.home"), "Library", "Application Support", "Posato")

    val policyDatabase: Path = supportDirectory.resolve("posato-policy.db")
    val applicationMappingsDatabase: Path = supportDirectory.resolve("macos-application-mappings.db")

    val databases: List<Path> = listOf(policyDatabase, applicationMappingsDatabase)

    val sidecarSuffixes: List<String> = listOf("-wal", "-shm", "-journal")
}
