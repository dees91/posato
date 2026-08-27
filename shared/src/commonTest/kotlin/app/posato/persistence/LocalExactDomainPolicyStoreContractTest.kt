package app.posato.persistence

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.persistence.db.PosatoDatabase
import app.posato.policy.ExactDomainPolicy
import app.posato.policy.ExactDomainPolicyLimits
import app.posato.policy.ExactDomainPolicyValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalExactDomainPolicyStoreContractTest {
    @Test
    fun `given a fresh database when policy is replaced and reopened then committed and empty policies persist`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("lifecycle.db")
        var driver = testDatabase.openDriver()
        try {
            var store = driver.createStore()
            assertState(store.read(), revision = 0, domains = emptyList())

            val firstPolicy = policyOf("alpha.example", "beta.example")
            assertState(store.replace(0, firstPolicy), revision = 1, domains = firstPolicy.canonicalValues())

            driver.close()
            driver = testDatabase.openDriver()
            store = driver.createStore()
            assertState(store.read(), revision = 1, domains = firstPolicy.canonicalValues())

            assertState(store.replace(1, ExactDomainPolicy.empty()), revision = 2, domains = emptyList())
            assertState(store.read(), revision = 2, domains = emptyList())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given invalid or stale revisions when replacing then the committed policy remains unchanged`() = withStore("revision.db") { store, _ ->
        val policy = policyOf("stable.example")
        assertState(store.replace(0, policy), revision = 1, domains = policy.canonicalValues())

        val invalid = assertFailure(store.replace(-1, ExactDomainPolicy.empty()))
        assertEquals(LocalPolicyFailure.INVALID_REVISION, invalid.reason)
        val exhausted = assertFailure(store.replace(Long.MAX_VALUE, ExactDomainPolicy.empty()))
        assertEquals(LocalPolicyFailure.REVISION_EXHAUSTED, exhausted.reason)
        val stale = assertFailure(store.replace(0, ExactDomainPolicy.empty()))
        assertEquals(LocalPolicyFailure.REVISION_CONFLICT, stale.reason)
        assertState(store.read(), revision = 1, domains = policy.canonicalValues())
    }

    @Test
    fun `given a noninteger revision when written then the schema rejects it and committed state remains unchanged`() =
        withStore("noninteger-revision.db") { store, driver ->
            val policy = policyOf("stable.example")
            assertState(store.replace(0, policy), revision = 1, domains = policy.canonicalValues())

            assertFails {
                driver.executeSql("UPDATE local_policy_metadata SET revision = 'invalid' WHERE singleton = 1")
            }
            assertState(store.read(), revision = 1, domains = policy.canonicalValues())
        }

    @Test
    fun `given a BLOB domain when written then schema rejects it and state remains unchanged`() = withStore("blob-domain.db") { store, driver ->
        val policy = policyOf("stable.example")
        assertState(store.replace(0, policy), revision = 1, domains = policy.canonicalValues())

        assertFails {
            driver.executeSql("INSERT INTO exact_domain_policy(canonical_domain) VALUES (x'626c6f622e6578616d706c65')")
        }
        assertState(store.read(), revision = 1, domains = policy.canonicalValues())
    }

    @Test
    fun `given oversized NUL text when written then schema rejects it and state remains unchanged`() = withStore("nul-domain.db") { store, driver ->
        val policy = policyOf("stable.example")
        assertState(store.replace(0, policy), revision = 1, domains = policy.canonicalValues())

        assertFails {
            driver.executeSql(
                "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('aa.bb' || char(0) || printf('%.*c', 248, 'x'))",
            )
        }
        assertState(store.read(), revision = 1, domains = policy.canonicalValues())
    }

    @Test
    fun `given an insert failure when replacing then the complete transaction rolls back`() = withStore("rollback.db") { store, driver ->
        val originalPolicy = policyOf("original.example")
        assertState(store.replace(0, originalPolicy), revision = 1, domains = originalPolicy.canonicalValues())
        driver.executeSql(
            """
            CREATE TRIGGER fail_policy_insert
            BEFORE INSERT ON exact_domain_policy
            WHEN NEW.canonical_domain = 'blocked.example'
            BEGIN
              SELECT RAISE(ABORT, 'synthetic insert failure');
            END
            """.trimIndent(),
        )

        val failure = assertFailure(store.replace(1, policyOf("blocked.example")))
        assertEquals(LocalPolicyFailure.STORAGE_FAILURE, failure.reason)
        assertState(store.read(), revision = 1, domains = originalPolicy.canonicalValues())
    }

    @Test
    fun `given a noncanonical stored domain when reading or replacing then corruption is returned without disclosure`() =
        withStore("invalid-domain.db") { store, driver ->
            driver.executeSql(
                "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('Private.Example')",
            )

            val readFailure = assertFailure(store.read())
            assertEquals(LocalPolicyFailure.CORRUPTION, readFailure.reason)
            assertFalse(readFailure.toString().contains("Private.Example"))

            val replaceFailure = assertFailure(store.replace(0, policyOf("replacement.example")))
            assertEquals(LocalPolicyFailure.CORRUPTION, replaceFailure.reason)
            assertFalse(replaceFailure.toString().contains("Private.Example"))

            val preservedFailure = assertFailure(store.read())
            assertEquals(LocalPolicyFailure.CORRUPTION, preservedFailure.reason)
        }

    @Test
    fun `given a malformed IDNA A-label in storage when read then corruption is returned`() = withStore("invalid-a-label.db") { store, driver ->
        driver.executeSql(
            "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('xn--0.example')",
        )

        val failure = assertFailure(store.read())
        assertEquals(LocalPolicyFailure.CORRUPTION, failure.reason)
    }

    @Test
    fun `given too many stored domains when reading then corruption is returned`() = withStore("over-limit.db") { store, driver ->
        val database = PosatoDatabase(driver)
        database.transaction {
            repeat(ExactDomainPolicyLimits.MAX_DOMAIN_COUNT + 1) { index ->
                database.localExactDomainPolicyQueries.insertDomain("a$index.example")
            }
        }

        val failure = assertFailure(store.read())
        assertEquals(LocalPolicyFailure.CORRUPTION, failure.reason)
    }

    @Test
    fun `given an invalid database file when initialized then opening fails and the file remains unchanged`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("invalid-file.db")
        testDatabase.writeInvalidDatabase()
        var driver: SqlDriver? = null
        try {
            val failure =
                try {
                    driver = testDatabase.openDriver()
                    PosatoDatabase(checkNotNull(driver))
                        .localExactDomainPolicyQueries
                        .selectRevision()
                        .awaitAsList()
                    null
                } catch (expectedInitializationFailure: Exception) {
                    expectedInitializationFailure
                }
            assertNotNull(failure)
            assertTrue(testDatabase.invalidDatabaseMarkerIsPresent())
        } finally {
            driver?.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given an injected database dispatcher when reading and replacing then all database work uses it`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("dispatcher.db")
        val driver = testDatabase.openDriver()
        val probe = DatabaseDispatcherProbe()
        val store = ProbingSqlDriver(driver, probe).createStore(probe)
        try {
            assertState(store.read(), revision = 0, domains = emptyList())
            assertState(
                store.replace(0, policyOf("context.example")),
                revision = 1,
                domains = listOf("context.example"),
            )

            assertTrue("query" in probe.operations)
            assertTrue("execute" in probe.operations)
            assertTrue("transaction" in probe.operations)
            assertEquals(emptyList(), probe.violations)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a policy when rendered as text then canonical domain values stay redacted`() {
        val canonicalValue = "private.example"
        val policy = policyOf(canonicalValue)
        val state = LocalExactDomainPolicyState(7, policy)

        assertEquals("ExactDomain(redacted)", policy.domains.single().toString())
        assertEquals("ExactDomainPolicy(redacted)", policy.toString())
        assertEquals("LocalExactDomainPolicyState(redacted)", state.toString())
    }
}

private fun withStore(
    name: String,
    block: suspend (LocalExactDomainPolicyStore, SqlDriver) -> Unit,
) = runTest {
    val testDatabase = createLocalPolicyTestDatabase(name)
    val driver = testDatabase.openDriver()
    try {
        block(driver.createStore(), driver)
    } finally {
        driver.close()
        testDatabase.delete()
    }
}

private fun SqlDriver.createStore(databaseDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default): LocalExactDomainPolicyStore {
    return SqlLocalExactDomainPolicyStore(
        database = PosatoDatabase(this),
        databaseDispatcher = databaseDispatcher,
    )
}

private fun policyOf(vararg canonicalValues: String): ExactDomainPolicy {
    val result = ExactDomainPolicy.fromCanonicalValues(canonicalValues.asList())

    return assertIs<ExactDomainPolicyValidationResult.Success>(result).policy
}

private fun ExactDomainPolicy.canonicalValues(): List<String> {
    return domains.map { domain -> domain.canonicalValue }
}

private fun assertState(
    result: LocalPolicyResult<LocalExactDomainPolicyState>,
    revision: Long,
    domains: List<String>,
) {
    val state = assertIs<LocalPolicyResult.Success<LocalExactDomainPolicyState>>(result).value
    assertEquals(revision, state.revision)
    assertEquals(domains, state.policy.canonicalValues())
}

private fun assertFailure(result: LocalPolicyResult<*>): LocalPolicyResult.Failure {
    return assertIs<LocalPolicyResult.Failure>(result)
}

private fun SqlDriver.executeSql(sql: String) {
    execute(
        identifier = null,
        sql = sql,
        parameters = 0,
    ).value
}
