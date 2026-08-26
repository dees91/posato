package app.posato.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.NO_VERSION_CHECK
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.O_RDONLY
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.open
import platform.posix.pread
import platform.posix.pwrite

@OptIn(ExperimentalForeignApi::class)
internal actual fun createLocalPolicyTestDatabase(name: String): LocalPolicyTestDatabase =
    IosLocalPolicyTestDatabase(
        name = name,
    )

@OptIn(ExperimentalForeignApi::class)
private class IosLocalPolicyTestDatabase(
    private val name: String,
) : LocalPolicyTestDatabase {
    private val directory: String = "${NSTemporaryDirectory().trimEnd('/')}/posato-model-001-tests"
    private val path: String = "$directory/$name"
    private val driverOutput = mutableListOf<String>()
    private val sqliterLogger =
        SilentSqliterLogger { message, exception ->
            driverOutput += message
            if (exception != null) {
                driverOutput += exception.toString()
            }
        }
    private var corruptedOffset: Long? = null

    init {
        delete()
    }

    override suspend fun open(
        databaseDispatcher: DatabaseDispatcher?,
        driverDecorator: (SqlDriver) -> SqlDriver,
    ): LocalPolicyResult<LocalExactDomainPolicyStore> =
        IosLocalPolicyStoreFactory(
            databaseName = name,
            directory = directory,
            sqliterLogger = sqliterLogger,
            databaseDispatcher =
                databaseDispatcher
                    ?: DatabaseDispatcher(
                        Dispatchers.Default.limitedParallelism(1, "PosatoTestDatabase"),
                    ),
            driverDecorator = driverDecorator,
        ).open()

    override fun withRawDriver(block: (SqlDriver) -> Unit) {
        check(exists())
        val driver = NativeSqliteDriver(existingConfiguration())
        try {
            block(driver)
        } finally {
            driver.close()
        }
    }

    override fun createUnsupportedSchema() {
        check(!exists())
        ensureDirectory()
        val configuration =
            DatabaseConfiguration(
                name = name,
                version = 2,
                create = { connection ->
                    wrapConnection(connection) { driver ->
                        initializeUnsupportedPolicySchema(driver)
                    }
                },
                journalMode = JournalMode.DELETE,
                extendedConfig =
                    DatabaseConfiguration.Extended(
                        foreignKeyConstraints = true,
                        basePath = directory,
                    ),
                loggingConfig = silentSqliterLogging(sqliterLogger),
            )
        val driver = NativeSqliteDriver(configuration)
        try {
            driver
                .executeQuery(
                    identifier = null,
                    sql = "SELECT 1",
                    mapper = { cursor ->
                        check(cursor.next().value)
                        QueryResult.Value(Unit)
                    },
                    parameters = 0,
                ).value
        } finally {
            driver.close()
        }
    }

    override fun corruptPolicyTablePage() {
        val pageSize = withRawLongQuery("PRAGMA page_size")
        val rootPage =
            withRawLongQuery(
                "SELECT rootpage FROM sqlite_master WHERE type = 'table' AND name = 'exact_domain_policy'",
            )
        val offset = (rootPage - 1) * pageSize
        check(readByteAt(offset) > 0)
        writeZeroAt(offset)
        corruptedOffset = offset
    }

    override fun corruptionMarkerIsPresent(): Boolean {
        return readByteAt(checkNotNull(corruptedOffset)) == 0.toByte()
    }

    override fun capturedDriverOutput(): List<String> {
        return driverOutput.toList()
    }

    override fun exists(): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    override fun delete() {
        val fileManager = NSFileManager.defaultManager
        listOf(path, "$path-journal", "$path-shm", "$path-wal").forEach { candidate ->
            if (fileManager.fileExistsAtPath(candidate)) {
                check(fileManager.removeItemAtPath(candidate, error = null))
            }
        }
    }

    private fun existingConfiguration(): DatabaseConfiguration =
        DatabaseConfiguration(
            name = name,
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

    private fun withRawLongQuery(sql: String): Long {
        var value: Long? = null
        withRawDriver { driver ->
            value = driver.queryRequiredLong(sql)
        }
        return checkNotNull(value)
    }

    private fun readByteAt(offset: Long): Byte {
        val descriptor = open(path, O_RDONLY)
        check(descriptor >= 0)
        return try {
            val value = ByteArray(1)
            val bytesRead =
                value.usePinned { pinned ->
                    pread(descriptor, pinned.addressOf(0), 1u, offset)
                }
            check(bytesRead == 1L)
            value.single()
        } finally {
            check(close(descriptor) == 0)
        }
    }

    private fun writeZeroAt(offset: Long) {
        val descriptor = open(path, O_WRONLY)
        check(descriptor >= 0)
        try {
            val value = byteArrayOf(0)
            val bytesWritten =
                value.usePinned { pinned ->
                    pwrite(descriptor, pinned.addressOf(0), 1u, offset)
                }
            check(bytesWritten == 1L)
        } finally {
            check(close(descriptor) == 0)
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
