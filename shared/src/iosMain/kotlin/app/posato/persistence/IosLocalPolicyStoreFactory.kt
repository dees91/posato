package app.posato.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.interop.Logger
import co.touchlab.sqliter.interop.SQLiteExceptionErrorCode
import co.touchlab.sqliter.interop.SqliteErrorType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

@OptIn(ExperimentalForeignApi::class)
internal class IosLocalPolicyStoreFactory(
    private val databaseName: String = "posato-policy.db",
    private val directory: String =
        "${NSHomeDirectory().trimEnd('/')}/Library/Application Support/Posato",
    private val sqliterLogger: Logger = SilentSqliterLogger(),
    private val databaseDispatcher: DatabaseDispatcher,
    private val driverDecorator: (SqlDriver) -> SqlDriver = { driver -> driver },
    private val corruptionClassifier: LocalPolicyCorruptionClassifier? = null,
) : LocalExactDomainPolicyStoreFactory {
    init {
        require(databaseName.matches(Regex("[a-z0-9][a-z0-9.-]{0,79}")))
    }

    override suspend fun open(): LocalPolicyResult<LocalExactDomainPolicyStore> {
        return SqlLocalExactDomainPolicyStore.open(
            factory =
                LocalPolicyDriverFactory {
                    ensureDirectory()
                    val path = "$directory/$databaseName"
                    val existed = NSFileManager.defaultManager.fileExistsAtPath(path)
                    val configuration =
                        DatabaseConfiguration(
                            name = databaseName,
                            version = NO_VERSION_CHECK,
                            create = { _ -> },
                            journalMode = JournalMode.DELETE,
                            extendedConfig =
                                DatabaseConfiguration.Extended(
                                    foreignKeyConstraints = true,
                                    basePath = directory,
                                ),
                            loggingConfig = silentSqliterLogging(sqliterLogger),
                        )
                    val driver = driverDecorator(NativeSqliteDriver(configuration))
                    OpenedLocalPolicyDriver(
                        driver = driver,
                        existedBeforeOpen = existed,
                        corruptionClassifier =
                            corruptionClassifier ?: LocalPolicyCorruptionClassifier(::isIosSqliteCorruption),
                    )
                },
            databaseDispatcher = databaseDispatcher,
        )
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

private fun isIosSqliteCorruption(failure: Exception): Boolean {
    val error = failure as? SQLiteExceptionErrorCode ?: return false
    return error.errorType == SqliteErrorType.SQLITE_CORRUPT ||
        error.errorType == SqliteErrorType.SQLITE_NOTADB
}

internal class SilentSqliterLogger(
    private val attemptedWrite: ((String, Throwable?) -> Unit)? = null,
) : Logger {
    override val eActive: Boolean = false
    override val vActive: Boolean = false

    override fun eWrite(
        message: String,
        exception: Throwable?,
    ) {
        attemptedWrite?.invoke(message, exception)
    }

    override fun trace(message: String) {
        attemptedWrite?.invoke(message, null)
    }

    override fun vWrite(message: String) {
        attemptedWrite?.invoke(message, null)
    }
}

internal fun silentSqliterLogging(logger: Logger): DatabaseConfiguration.Logging {
    return DatabaseConfiguration.Logging(
        logger = logger,
        verboseDataCalls = false,
    )
}
