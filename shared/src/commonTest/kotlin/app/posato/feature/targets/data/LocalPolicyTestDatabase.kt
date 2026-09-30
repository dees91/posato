package app.posato.feature.targets.data

import app.cash.sqldelight.db.SqlDriver

internal expect fun createLocalPolicyTestDatabase(name: String): LocalPolicyTestDatabase

internal interface LocalPolicyTestDatabase {
    fun openDriver(): SqlDriver

    /** Creates the database as [PosatoSchemaHistory] builds it at [version]; a later [openDriver] migrates it to today. */
    fun openDriverAt(version: Long): SqlDriver

    fun writeInvalidDatabase()

    fun invalidDatabaseMarkerIsPresent(): Boolean

    fun delete()
}
