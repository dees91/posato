package app.posato.feature.targets.data

import app.cash.sqldelight.db.SqlDriver

internal expect fun createLocalPolicyTestDatabase(name: String): LocalPolicyTestDatabase

internal interface LocalPolicyTestDatabase {
    fun openDriver(): SqlDriver

    fun writeInvalidDatabase()

    fun invalidDatabaseMarkerIsPresent(): Boolean

    fun delete()
}
