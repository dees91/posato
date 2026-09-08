package app.posato.core.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path

internal fun createDesktopDatabaseDriver(databasePath: String = defaultDesktopPolicyDatabasePath()): SqlDriver {
    val path = Path.of(databasePath).toAbsolutePath().normalize()
    require(path.fileName != null && path.parent != null)
    Files.createDirectories(path.parent)

    return JdbcSqliteDriver(
        url = "jdbc:sqlite:$path",
        schema = PosatoDatabase.Schema.synchronous(),
    )
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
        )
        .toString()
}
