package app.posato.feature.targets.data

import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Storage of several pause sets: one website limit over the unique domains of all sets, and edits of one set keep the others. */
class PauseSetStoreTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))

    @Test
    fun `given two sets sharing a website when replaced then both read back with their names and the default`() = runTest {
        withStore("pause-sets-roundtrip.db") { store, _ ->
            val sets = checkNotNull(
                PauseSets.of(
                    listOf(
                        LocalPauseSet(PauseSetId.FIRST, null, domains("a", "shared")),
                        LocalPauseSet(work, "Work", domains("shared", "b")),
                    ),
                    work,
                ),
            )

            val replaced = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceSets(0, sets))
            val read = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read())

            assertEquals(sets, replaced.value.sets)
            assertEquals(sets, read.value.sets)
            assertEquals(1, read.value.revision)
        }
    }

    @Test
    fun `given the first set deleted when the application group is named then the name is saved and the sets are kept`() = runTest {
        withStore("pause-sets-group-without-first.db") { store, _ ->
            val onlyWork = checkNotNull(PauseSets.of(listOf(LocalPauseSet(work, "Work", domains("b"))), work))
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceSets(0, onlyWork))
            val named = checkNotNull((TargetPolicy.fromStoredValues(emptyList(), "Applications") as? TargetPolicyValidationResult.Success)?.policy)

            val replaced = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(1, named))

            assertEquals("Applications", replaced.value.applicationPolicyName?.canonicalValue)
            assertEquals(onlyWork, replaced.value.sets)
        }
    }

    @Test
    fun `given stored sets when read then the limit counts unique websites across sets`() = runTest {
        withStore("pause-sets-unique.db") { store, driver ->
            driver.executeSql("INSERT INTO local_pause_set(set_id, name, refused) VALUES (X'${work.hex()}', 'Work', 0)")
            (0 until 600).forEach { index ->
                driver.executeSql("INSERT INTO local_pause_set_domain VALUES (zeroblob(16), 'd$index.example')")
                driver.executeSql("INSERT INTO local_pause_set_domain VALUES (X'${work.hex()}', 'd$index.example')")
            }

            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read())

            (600 until 1_025).forEach { index ->
                driver.executeSql("INSERT INTO local_pause_set_domain VALUES (X'${work.hex()}', 'd$index.example')")
            }

            assertEquals(LocalPolicyFailure.CORRUPTION, assertIs<LocalPolicyResult.Failure>(store.read()).reason)
        }
    }

    @Test
    fun `given a second set when the first set's websites are replaced then the second set is kept`() = runTest {
        withStore("pause-sets-first-edit.db") { store, _ ->
            val sets = checkNotNull(
                PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, null, domains("a")), LocalPauseSet(work, "Work", domains("b"))), null),
            )
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceSets(0, sets))

            val replaced = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(1, policy("c")))

            assertEquals(domains("c"), replaced.value.sets.domainsOf(PauseSetId.FIRST))
            assertEquals(domains("b"), replaced.value.sets.domainsOf(work))
        }
    }

    @Test
    fun `given another set holding websites when the first set would pass the unique limit then nothing changes`() = runTest {
        withStore("pause-sets-capacity.db") { store, _ ->
            val others = (0 until 1_000).map { index -> "o$index" }
            val sets = checkNotNull(
                PauseSets.of(
                    listOf(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(work, "Work", domains(*others.toTypedArray()))),
                    null,
                ),
            )
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceSets(0, sets))

            val refused = store.replace(1, policy(*(0 until 25).map { index -> "f$index" }.toTypedArray()))

            assertEquals(LocalPolicyFailure.CAPACITY, assertIs<LocalPolicyResult.Failure>(refused).reason)
            assertEquals(sets, assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value.sets)
        }
    }

    private fun domains(vararg labels: String): List<ExactDomain> {
        return labels.map { label -> checkNotNull(ExactDomain.restore("$label.example")) }.sortedBy(ExactDomain::canonicalValue)
    }

    private fun policy(vararg labels: String): TargetPolicy {
        val validation = TargetPolicy.fromStoredValues(domains(*labels).map(ExactDomain::canonicalValue), null)
        return assertIs<TargetPolicyValidationResult.Success>(validation).policy
    }

    private suspend fun withStore(
        name: String,
        block: suspend (SqlLocalTargetPolicyStore, SqlDriver) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            block(SqlLocalTargetPolicyStore(PosatoDatabase(driver), Dispatchers.Default), driver)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }
}

private fun PauseSetId.hex(): String {
    return value.copyBytes().joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).value
}
