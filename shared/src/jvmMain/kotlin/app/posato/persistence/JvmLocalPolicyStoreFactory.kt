package app.posato.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException

private const val SQLITE_CORRUPT_CODE: Int = 11
private const val SQLITE_NOT_A_DATABASE_CODE: Int = 26

internal class JvmLocalPolicyStoreFactory(
    databasePath: String,
    private val databaseDispatcher: DatabaseDispatcher,
    private val driverDecorator: (SqlDriver) -> SqlDriver = { driver -> driver },
    private val corruptionClassifier: LocalPolicyCorruptionClassifier? = null,
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
                    OpenedLocalPolicyDriver(
                        driver = driver,
                        existedBeforeOpen = existed,
                        corruptionClassifier =
                            corruptionClassifier ?: LocalPolicyCorruptionClassifier(::isJvmSqliteCorruption),
                    )
                },
            databaseDispatcher = databaseDispatcher,
        )
    }
}

private fun isJvmSqliteCorruption(failure: Exception): Boolean {
    val sqlFailure = failure as? SQLException ?: return false
    val primaryCode = sqlFailure.errorCode and 0xff
    return primaryCode == SQLITE_CORRUPT_CODE ||
        primaryCode == SQLITE_NOT_A_DATABASE_CODE
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
