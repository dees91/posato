package app.posato.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path

internal class JvmLocalPolicyStoreFactory(
    databasePath: String,
    private val databaseDispatcher: DatabaseDispatcher,
    private val driverDecorator: (SqlDriver) -> SqlDriver = { driver -> driver },
) : LocalExactDomainPolicyStoreFactory {
    private val path: Path = Path.of(databasePath).toAbsolutePath().normalize()

    init {
        require(path.fileName != null && path.parent != null)
    }

    override suspend fun open(): LocalPolicyResult<LocalExactDomainPolicyStore> {
        return SqlLocalExactDomainPolicyStore.open(
            factory =
                LocalPolicyDriverFactory {
                    Files.createDirectories(path.parent)
                    val existed = Files.exists(path)
                    val driver = driverDecorator(JdbcSqliteDriver("jdbc:sqlite:$path"))
                    OpenedLocalPolicyDriver(driver, existed)
                },
            databaseDispatcher = databaseDispatcher,
        )
    }
}

internal fun defaultDesktopPolicyDatabasePath(): String {
    val homeDirectory = checkNotNull(System.getProperty("user.home"))
    return Path
        .of(
            homeDirectory,
            "Library",
            "Application Support",
            "Posato",
            "posato-policy.db",
        ).toString()
}
