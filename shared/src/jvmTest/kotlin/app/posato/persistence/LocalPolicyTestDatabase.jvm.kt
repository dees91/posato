package app.posato.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

internal actual fun createLocalPolicyTestDatabase(name: String): LocalPolicyTestDatabase =
    JvmLocalPolicyTestDatabase(
        name = name,
    )

private class JvmLocalPolicyTestDatabase(
    name: String,
) : LocalPolicyTestDatabase {
    private val directory: Path = Files.createTempDirectory("posato-model-001-")
    private val path: Path = directory.resolve(name)
    private val url: String = "jdbc:sqlite:${path.toAbsolutePath()}"
    private var corruptedOffset: Long? = null

    override suspend fun open(
        databaseDispatcher: DatabaseDispatcher?,
        driverDecorator: (app.cash.sqldelight.db.SqlDriver) -> app.cash.sqldelight.db.SqlDriver,
        corruptionClassifier: LocalPolicyCorruptionClassifier?,
    ): LocalPolicyResult<LocalExactDomainPolicyStore> =
        JvmLocalPolicyStoreFactory(
            databasePath = path.toString(),
            databaseDispatcher =
                databaseDispatcher ?: DatabaseDispatcher(Dispatchers.IO.limitedParallelism(1, "PosatoTestDatabase")),
            driverDecorator = driverDecorator,
            corruptionClassifier = corruptionClassifier,
        ).open()

    override fun withRawDriver(block: (app.cash.sqldelight.db.SqlDriver) -> Unit) {
        val driver = JdbcSqliteDriver(url)
        try {
            block(driver)
        } finally {
            driver.close()
        }
    }

    override fun createUnsupportedSchema() {
        check(!exists())
        withRawDriver(::initializeUnsupportedPolicySchema)
    }

    override fun createViewOnlySchema() {
        check(!exists())
        withRawDriver { driver ->
            driver
                .execute(
                    identifier = null,
                    sql = "CREATE VIEW orphan_view AS SELECT 1 AS value",
                    parameters = 0,
                ).value
        }
    }

    override fun corruptPolicyTablePage() {
        val pageSize = withRawLongQuery("PRAGMA page_size")
        val rootPage =
            withRawLongQuery(
                "SELECT rootpage FROM sqlite_master WHERE type = 'table' AND name = 'exact_domain_policy'",
            )
        val offset = (rootPage - 1) * pageSize
        RandomAccessFile(path.toFile(), "rw").use { file ->
            file.seek(offset)
            check(file.read() > 0)
            file.seek(offset)
            file.write(0)
        }
        corruptedOffset = offset
    }

    override fun corruptionMarkerIsPresent(): Boolean {
        val offset = checkNotNull(corruptedOffset)
        return RandomAccessFile(path.toFile(), "r").use { file ->
            file.seek(offset)
            file.read() == 0
        }
    }

    override fun capturedDriverOutput(): List<String> {
        return emptyList()
    }

    override fun exists(): Boolean = Files.exists(path)

    override fun delete() {
        listOf(
            path,
            path.resolveSibling("${path.fileName}-journal"),
            path.resolveSibling("${path.fileName}-shm"),
            path.resolveSibling("${path.fileName}-wal"),
        ).forEach(Files::deleteIfExists)
        Files.deleteIfExists(directory)
    }

    private fun withRawLongQuery(sql: String): Long {
        var value: Long? = null
        withRawDriver { driver ->
            value = driver.queryRequiredLong(sql)
        }
        return checkNotNull(value)
    }
}
