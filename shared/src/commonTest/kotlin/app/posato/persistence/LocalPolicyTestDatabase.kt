package app.posato.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

internal expect fun createLocalPolicyTestDatabase(name: String): LocalPolicyTestDatabase

internal interface LocalPolicyTestDatabase {
    suspend fun open(
        databaseDispatcher: DatabaseDispatcher? = null,
        driverDecorator: (SqlDriver) -> SqlDriver = { driver -> driver },
        corruptionClassifier: LocalPolicyCorruptionClassifier? = null,
    ): LocalPolicyResult<LocalExactDomainPolicyStore>

    fun withRawDriver(block: (SqlDriver) -> Unit)

    fun createUnsupportedSchema()

    fun createViewOnlySchema()

    fun corruptPolicyTablePage()

    fun corruptionMarkerIsPresent(): Boolean

    fun capturedDriverOutput(): List<String>

    fun exists(): Boolean

    fun delete()
}

internal fun SqlDriver.queryRequiredLong(sql: String): Long {
    return executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            check(cursor.next().value)
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        },
        parameters = 0,
    ).value
}

internal fun initializeUnsupportedPolicySchema(driver: SqlDriver) {
    driver
        .execute(
            identifier = null,
            sql = "CREATE TABLE policy_schema (singleton INTEGER PRIMARY KEY, schema_version INTEGER NOT NULL)",
            parameters = 0,
        ).value
    driver
        .execute(
            identifier = null,
            sql = "INSERT INTO policy_schema(singleton, schema_version) VALUES (1, 2)",
            parameters = 0,
        ).value
}
