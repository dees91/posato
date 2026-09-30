package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.posato.core.database.PosatoDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

class PosatoSchemaHistoryTest {
    @Test
    fun `given the tracked version one snapshot when compared then the history starts from the same tables and row`() {
        val snapshot = Path.of("src/commonMain/sqldelight/databases/1.db").toAbsolutePath()
        val rebuilt = Files.createTempFile("posato-history-", ".db")
        try {
            JdbcSqliteDriver("jdbc:sqlite:$rebuilt", schema = PosatoSchemaHistory(1)).close()

            assertEquals(describe(snapshot), describe(rebuilt))
            assertEquals(listOf("1|0"), rows(snapshot, "SELECT singleton || '|' || revision FROM local_policy_metadata"))
            assertEquals(listOf("1|0"), rows(rebuilt, "SELECT singleton || '|' || revision FROM local_policy_metadata"))
        } finally {
            Files.deleteIfExists(rebuilt)
        }
    }

    @Test
    fun `given every migration from version one when compared with a fresh install then tables columns and keys match`() {
        val fresh = Files.createTempFile("posato-fresh-", ".db")
        val history = Files.createTempFile("posato-history-", ".db")
        try {
            JdbcSqliteDriver("jdbc:sqlite:$fresh", schema = PosatoDatabase.Schema.synchronous()).close()
            JdbcSqliteDriver("jdbc:sqlite:$history", schema = PosatoSchemaHistory(PosatoDatabase.Schema.version)).close()

            assertEquals(describe(fresh), describe(history))
        } finally {
            Files.deleteIfExists(fresh)
            Files.deleteIfExists(history)
        }
    }

    private fun describe(database: Path): List<String> {
        val tables = rows(database, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
        return tables.flatMap { table ->
            listOf("table $table") +
                rows(database, "SELECT $COLUMN_FACTS FROM pragma_table_info('$table') ORDER BY name") +
                rows(database, "SELECT name || ' ' || \"unique\" FROM pragma_index_list('$table') WHERE origin != 'pk' ORDER BY name")
        }
    }

    private companion object {
        const val COLUMN_FACTS: String = "name || ' ' || type || ' ' || \"notnull\" || ' ' || ifnull(dflt_value, '-') || ' ' || pk"
    }

    private fun rows(
        database: Path,
        sql: String,
    ): List<String> {
        return DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }
        }
    }
}
