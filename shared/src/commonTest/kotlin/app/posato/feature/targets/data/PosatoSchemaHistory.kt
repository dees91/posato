package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.posato.core.database.PosatoDatabase

/**
 * The policy database as a released build left it at [version]: the tracked version-1 snapshot
 * (`databases/1.db`) followed by the real migrations up to that version, so a migration test seeds the
 * schema a person actually had rather than today's schema with tables removed.
 */
internal class PosatoSchemaHistory(
    override val version: Long,
) : SqlSchema<QueryResult.Value<Unit>> {
    init {
        require(version in 1..PosatoDatabase.Schema.version)
    }

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        VERSION_ONE_STATEMENTS.forEach { statement -> driver.execute(null, statement, 0).value }
        if (version > 1) {
            PosatoDatabase.Schema.synchronous().migrate(driver, 1, version).value
        }
        return QueryResult.Unit
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> {
        error("A historical schema is only created")
    }

    companion object {
        /** The DDL and seeded row of `shared/src/commonMain/sqldelight/databases/1.db`, checked by a JVM test. */
        val VERSION_ONE_STATEMENTS: List<String> = listOf(
            """
            CREATE TABLE local_policy_metadata (
              singleton INTEGER NOT NULL PRIMARY KEY CHECK (singleton = 1),
              revision INTEGER NOT NULL CHECK (
                typeof(revision) = 'integer' AND revision >= 0
              )
            )
            """.trimIndent(),
            """
            CREATE TABLE exact_domain_policy (
              canonical_domain TEXT NOT NULL PRIMARY KEY CHECK (
                typeof(canonical_domain) = 'text' AND
                length(CAST(canonical_domain AS BLOB)) BETWEEN 3 AND 253
              )
            )
            """.trimIndent(),
            "INSERT INTO local_policy_metadata(singleton, revision) VALUES (1, 0)",
        )
    }
}
