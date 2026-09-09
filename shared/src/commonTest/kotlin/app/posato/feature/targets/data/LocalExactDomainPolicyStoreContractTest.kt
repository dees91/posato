package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
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

            assertState(store.replace(1, TargetPolicy.empty()), revision = 2, domains = emptyList())
            assertState(store.read(), revision = 2, domains = emptyList())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given domains and an application group when replaced reopened and removed then the aggregate persists atomically`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("application-policy.db")
        var driver = testDatabase.openDriver()
        try {
            var store = driver.createStore()
            val policy = policyOf("stable.example", applicationPolicyName = "Social feeds")

            assertState(store.replace(0, policy), revision = 1, domains = listOf("stable.example"), applicationPolicyName = "Social feeds")
            driver.close()
            driver = testDatabase.openDriver()
            store = driver.createStore()
            assertState(store.read(), revision = 1, domains = listOf("stable.example"), applicationPolicyName = "Social feeds")

            val withoutApplicationPolicy = policyOf("stable.example")
            assertState(store.replace(1, withoutApplicationPolicy), revision = 2, domains = listOf("stable.example"))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a version one database when reopened then migration preserves revision and domains without adding a group`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("migration.db")
        var driver = testDatabase.openDriver()
        try {
            driver.executeSql("UPDATE local_policy_metadata SET revision = 7 WHERE singleton = 1")
            driver.executeSql("INSERT INTO exact_domain_policy(canonical_domain) VALUES ('stable.example')")
            driver.executeSql("DROP TABLE local_session_expiry")
            driver.executeSql("DROP TABLE local_session")
            driver.executeSql("DROP TABLE sync_terminal_expiry")
            driver.executeSql("DROP TABLE sync_staged_bundle")
            driver.executeSql("DROP TABLE sync_pending_bundle")
            driver.executeSql("DROP TABLE sync_accepted_bundle")
            driver.executeSql("DROP TABLE sync_replica_state")
            driver.executeSql("DROP TABLE sync_bootstrap_state")
            driver.executeSql("DROP TABLE application_policy")
            driver.executeSql("DROP TABLE local_setup_state")
            driver.executeSql("PRAGMA user_version = 1")
            driver.close()

            driver = testDatabase.openDriver()

            assertState(driver.createStore().read(), revision = 7, domains = listOf("stable.example"))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a version two database when reopened then sync storage is added without changing policy`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-migration.db")
        var driver = testDatabase.openDriver()
        try {
            driver.executeSql("UPDATE local_policy_metadata SET revision = 9 WHERE singleton = 1")
            driver.executeSql("INSERT INTO exact_domain_policy(canonical_domain) VALUES ('stable.example')")
            driver.executeSql("INSERT INTO application_policy(singleton, canonical_name) VALUES (1, 'Stable group')")
            driver.executeSql("DROP TABLE local_session_expiry")
            driver.executeSql("DROP TABLE local_session")
            driver.executeSql("DROP TABLE sync_terminal_expiry")
            driver.executeSql("DROP TABLE sync_staged_bundle")
            driver.executeSql("DROP TABLE sync_pending_bundle")
            driver.executeSql("DROP TABLE sync_accepted_bundle")
            driver.executeSql("DROP TABLE sync_replica_state")
            driver.executeSql("DROP TABLE sync_bootstrap_state")
            driver.executeSql("DROP TABLE local_setup_state")
            driver.executeSql("PRAGMA user_version = 2")
            driver.close()

            driver = testDatabase.openDriver()

            assertState(
                driver.createStore().read(),
                revision = 9,
                domains = listOf("stable.example"),
                applicationPolicyName = "Stable group",
            )
            assertEquals(emptyList(), PosatoDatabase(driver).syncReplicaQueries.selectSyncReplicaState().awaitAsList())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given invalid stale or missing revisions when replacing then typed failures preserve stored state`() =
        withStore("revision.db") { store, driver ->
            val policy = policyOf("stable.example")
            assertState(store.replace(0, policy), revision = 1, domains = policy.canonicalValues())

            val invalid = assertFailure(store.replace(-1, TargetPolicy.empty()))
            assertEquals(LocalPolicyFailure.INVALID_REVISION, invalid.reason)
            val exhausted = assertFailure(store.replace(Long.MAX_VALUE, TargetPolicy.empty()))
            assertEquals(LocalPolicyFailure.REVISION_EXHAUSTED, exhausted.reason)
            val stale = assertFailure(store.replace(0, TargetPolicy.empty()))
            assertEquals(LocalPolicyFailure.REVISION_CONFLICT, stale.reason)
            assertState(store.read(), revision = 1, domains = policy.canonicalValues())

            driver.executeSql("DELETE FROM local_policy_metadata")
            val missing = assertFailure(store.replace(1, TargetPolicy.empty()))
            assertEquals(LocalPolicyFailure.CORRUPTION, missing.reason)
            val preserved = assertFailure(store.read())
            assertEquals(LocalPolicyFailure.CORRUPTION, preserved.reason)
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
    fun `given a BLOB or oversized application policy when written then schema rejects it and state remains unchanged`() =
        withStore("invalid-application-policy-type.db") { store, driver ->
            val policy = policyOf("stable.example")
            assertState(store.replace(0, policy), revision = 1, domains = listOf("stable.example"))

            assertFails {
                driver.executeSql("INSERT INTO application_policy(singleton, canonical_name) VALUES (1, x'626c6f62')")
            }
            assertFails {
                driver.executeSql("INSERT INTO application_policy(singleton, canonical_name) VALUES (1, '${"a".repeat(81)}')")
            }
            assertState(store.read(), revision = 1, domains = listOf("stable.example"))
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
    fun `given an application policy insert failure when replacing then domains revision and prior group roll back`() =
        withStore("application-rollback.db") { store, driver ->
            val originalPolicy = policyOf("original.example", applicationPolicyName = "Original group")
            assertState(
                store.replace(0, originalPolicy),
                revision = 1,
                domains = listOf("original.example"),
                applicationPolicyName = "Original group",
            )
            driver.executeSql(
                """
                CREATE TRIGGER fail_application_policy_insert
                BEFORE INSERT ON application_policy
                BEGIN
                  SELECT RAISE(ABORT, 'synthetic insert failure');
                END
                """.trimIndent(),
            )

            val failure = assertFailure(store.replace(1, policyOf("replacement.example", applicationPolicyName = "Replacement group")))

            assertEquals(LocalPolicyFailure.STORAGE_FAILURE, failure.reason)
            assertState(
                store.read(),
                revision = 1,
                domains = listOf("original.example"),
                applicationPolicyName = "Original group",
            )
        }

    @Test
    fun `given a noncanonical stored application policy when read or replaced then corruption preserves stored state`() =
        withStore("invalid-application-policy.db") { store, driver ->
            driver.executeSql("INSERT INTO application_policy(singleton, canonical_name) VALUES (1, 'Cafe' || char(769))")

            assertEquals(LocalPolicyFailure.CORRUPTION, assertFailure(store.read()).reason)
            assertEquals(LocalPolicyFailure.CORRUPTION, assertFailure(store.replace(0, policyOf("replacement.example"))).reason)
            assertEquals(LocalPolicyFailure.CORRUPTION, assertFailure(store.read()).reason)
        }

    @Test
    fun `given malformed UTF-8 application policy when read then corruption is returned`() =
        withStore("malformed-application-policy.db") { store, driver ->
            driver.executeSql(
                "INSERT INTO application_policy(singleton, canonical_name) VALUES (1, CAST(x'80' AS TEXT))",
            )

            assertEquals(LocalPolicyFailure.CORRUPTION, assertFailure(store.read()).reason)
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
            val failure = try {
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
        val state = LocalTargetPolicyState(7, policy)

        assertEquals("ExactDomain(redacted)", policy.domains.single().toString())
        assertEquals("TargetPolicy(redacted)", policy.toString())
        assertEquals("LocalTargetPolicyState(redacted)", state.toString())
    }
}

private fun withStore(
    name: String,
    block: suspend (LocalTargetPolicyStore, SqlDriver) -> Unit,
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

private fun SqlDriver.createStore(databaseDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default): LocalTargetPolicyStore {
    return SqlLocalTargetPolicyStore(
        database = PosatoDatabase(this),
        databaseDispatcher = databaseDispatcher,
    )
}

private fun policyOf(
    vararg canonicalValues: String,
    applicationPolicyName: String? = null,
): TargetPolicy {
    val result = TargetPolicy.fromStoredValues(canonicalValues.asList(), applicationPolicyName)

    return assertIs<TargetPolicyValidationResult.Success>(result).policy
}

private fun TargetPolicy.canonicalValues(): List<String> {
    return domains.map { domain -> domain.canonicalValue }
}

private fun assertState(
    result: LocalPolicyResult<LocalTargetPolicyState>,
    revision: Long,
    domains: List<String>,
    applicationPolicyName: String? = null,
) {
    val state = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(result).value
    assertEquals(revision, state.revision)
    assertEquals(domains, state.policy.canonicalValues())
    assertEquals(applicationPolicyName, state.policy.applicationPolicyName?.canonicalValue)
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
