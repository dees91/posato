package app.posato.persistence

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.posato.persistence.db.PosatoDatabase
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.interop.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

@OptIn(ExperimentalForeignApi::class)
internal fun createIosDatabaseDriver(
    databaseName: String = "posato-policy.db",
    directory: String = "${NSHomeDirectory().trimEnd('/')}/Library/Application Support/Posato",
): SqlDriver {
    ensureDirectory(directory)
    return NativeSqliteDriver(
        schema = PosatoDatabase.Schema.synchronous(),
        name = databaseName,
        onConfiguration = { configuration ->
            configuration.copy(
                extendedConfig = configuration.extendedConfig.copy(basePath = directory),
                loggingConfig = DatabaseConfiguration.Logging(
                    logger = SilentSqliterLogger,
                    verboseDataCalls = false,
                ),
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun ensureDirectory(directory: String) {
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

private object SilentSqliterLogger : Logger {
    override val eActive: Boolean = false
    override val vActive: Boolean = false

    override fun eWrite(
        message: String,
        exception: Throwable?,
    ) {
        return
    }

    override fun trace(message: String) {
        return
    }

    override fun vWrite(message: String) {
        return
    }
}
