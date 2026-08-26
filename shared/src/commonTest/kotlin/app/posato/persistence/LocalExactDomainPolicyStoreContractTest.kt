package app.posato.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.posato.policy.ExactDomainPolicy
import app.posato.policy.ExactDomainPolicyLimits
import app.posato.policy.ExactDomainPolicyValidationFailure
import app.posato.policy.ExactDomainPolicyValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalExactDomainPolicyStoreContractTest {
    @Test
    fun createsReplacesReopensAndRemovesThePolicy() =
        runTest {
            withTestDatabase("lifecycle.db") { testDatabase ->
                val store = testDatabase.openStore()
                assertState(store.read(), revision = 0, domains = emptyList())

                val policy = policy("xn--bcher-kva.example", "alpha.example", "alpha.example")
                assertState(
                    store.replace(expectedRevision = 0, policy = policy),
                    revision = 1,
                    domains = listOf("alpha.example", "xn--bcher-kva.example"),
                )
                store.close()

                val reopened = testDatabase.openStore()
                assertState(
                    reopened.read(),
                    revision = 1,
                    domains = listOf("alpha.example", "xn--bcher-kva.example"),
                )
                assertState(
                    reopened.replace(expectedRevision = 1, policy = ExactDomainPolicy.empty()),
                    revision = 2,
                    domains = emptyList(),
                )
                reopened.close()

                val afterRemoval = testDatabase.openStore()
                assertState(afterRemoval.read(), revision = 2, domains = emptyList())
                afterRemoval.close()
            }
        }

    @Test
    fun rejectsAStaleRevisionWithoutChangingState() =
        runTest {
            withTestDatabase("revision-conflict.db") { testDatabase ->
                val store = testDatabase.openStore()
                assertState(store.replace(0, policy("first.example")), 1, listOf("first.example"))

                val failure =
                    assertIs<LocalPolicyResult.Failure>(
                        store.replace(0, policy("second.example")),
                    )
                assertEquals(LocalPolicyFailure.REVISION_CONFLICT, failure.reason)
                assertState(store.read(), 1, listOf("first.example"))
                store.close()
            }
        }

    @Test
    fun rollsBackTheCompleteReplacementWhenAnInsertFails() =
        runTest {
            withTestDatabase("rollback.db") { testDatabase ->
                val store = testDatabase.openStore()
                assertState(store.replace(0, policy("first.example")), 1, listOf("first.example"))
                testDatabase.withRawDriver { driver ->
                    driver
                        .execute(
                            identifier = null,
                            sql =
                                """
                                CREATE TRIGGER reject_synthetic_domain
                                BEFORE INSERT ON exact_domain_policy
                                WHEN NEW.canonical_domain = 'reject.example'
                                BEGIN
                                  SELECT RAISE(ABORT, 'synthetic failure');
                                END
                                """.trimIndent(),
                            parameters = 0,
                        ).value
                }

                val failure =
                    assertIs<LocalPolicyResult.Failure>(
                        store.replace(1, policy("reject.example", "second.example")),
                    )
                assertEquals(LocalPolicyFailure.STORAGE_FAILURE, failure.reason)
                assertState(store.read(), 1, listOf("first.example"))
                assertTrue(testDatabase.capturedDriverOutput().isEmpty())
                store.close()
            }
        }

    @Test
    fun rejectsAndPreservesAnUnsupportedExistingSchema() =
        runTest {
            withTestDatabase("unsupported.db") { testDatabase ->
                testDatabase.createUnsupportedSchema()

                val failure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.UNSUPPORTED_SCHEMA, failure.reason)
                assertTrue(testDatabase.exists())
                testDatabase.withRawDriver { driver ->
                    assertEquals(2, driver.querySingleLong("SELECT schema_version FROM policy_schema"))
                }
            }
        }

    @Test
    fun rejectsAndPreservesInvalidStoredPolicyData() =
        runTest {
            withTestDatabase("corrupt-policy.db") { testDatabase ->
                val store = testDatabase.openStore()
                assertState(store.replace(0, policy("valid.example")), 1, listOf("valid.example"))
                store.close()
                testDatabase.withRawDriver { driver ->
                    driver
                        .execute(
                            identifier = null,
                            sql = "UPDATE exact_domain_policy SET canonical_domain = 'UPPER.example'",
                            parameters = 0,
                        ).value
                }

                val failure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.CORRUPTION, failure.reason)
                assertTrue(testDatabase.exists())
                testDatabase.withRawDriver { driver ->
                    assertEquals(
                        "UPPER.example",
                        driver.querySingleString("SELECT canonical_domain FROM exact_domain_policy"),
                    )
                }
            }
        }

    @Test
    fun rejectsAnOverLimitStoredPolicyBeforeMaterializingIt() =
        runTest {
            withTestDatabase("over-limit-policy.db") { testDatabase ->
                val store = testDatabase.openStore()
                store.close()
                val storedDomainCount = ExactDomainPolicyLimits.MAX_DOMAIN_COUNT + 1L
                testDatabase.withRawDriver { driver ->
                    driver
                        .execute(
                            identifier = null,
                            sql =
                                """
                                WITH RECURSIVE domain_numbers(value) AS (
                                  VALUES(0)
                                  UNION ALL
                                  SELECT value + 1 FROM domain_numbers WHERE value < ${ExactDomainPolicyLimits.MAX_DOMAIN_COUNT}
                                )
                                INSERT INTO exact_domain_policy(canonical_domain)
                                SELECT 'domain' || value || '.example' FROM domain_numbers
                                """.trimIndent(),
                            parameters = 0,
                        ).value
                    assertEquals(
                        storedDomainCount,
                        driver.queryRequiredLong("SELECT COUNT(*) FROM exact_domain_policy"),
                    )
                }

                val failure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.CORRUPTION, failure.reason)
                assertTrue(testDatabase.exists())
                testDatabase.withRawDriver { driver ->
                    assertEquals(
                        storedDomainCount,
                        driver.queryRequiredLong("SELECT COUNT(*) FROM exact_domain_policy"),
                    )
                }
            }
        }

    @Test
    fun rejectsAndPreservesPhysicallyCorruptStorageWithoutDriverOutput() =
        runTest {
            withTestDatabase("physical-corruption.db") { testDatabase ->
                val store = testDatabase.openStore()
                assertState(store.replace(0, policy("private.example")), 1, listOf("private.example"))
                store.close()
                testDatabase.corruptPolicyTablePage()

                val failure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.CORRUPTION, failure.reason)
                assertTrue(testDatabase.exists())
                assertTrue(testDatabase.corruptionMarkerIsPresent())
                assertTrue(testDatabase.capturedDriverOutput().isEmpty())
            }
        }

    @Test
    fun mapsValidationExecutionFailuresToStorageFailure() =
        runTest {
            withTestDatabase("validation-execution-failure.db") { testDatabase ->
                val store = testDatabase.openStore()
                assertState(store.replace(0, policy("preserved.example")), 1, listOf("preserved.example"))
                store.close()

                val validationQueries =
                    listOf(
                        "PRAGMA quick_check",
                        "SELECT COUNT(*) FROM sqlite_master",
                        "FROM policy_schema",
                    )
                validationQueries.forEach { failingSqlFragment ->
                    val failure =
                        assertIs<LocalPolicyResult.Failure>(
                            testDatabase.open(
                                driverDecorator = { driver ->
                                    FailingQuerySqlDriver(driver, failingSqlFragment)
                                },
                            ),
                        )
                    assertEquals(LocalPolicyFailure.STORAGE_FAILURE, failure.reason)
                    assertTrue(testDatabase.exists())
                }

                val reopened = testDatabase.openStore()
                assertState(reopened.read(), 1, listOf("preserved.example"))
                reopened.close()
            }
        }

    @Test
    fun rollsBackInterruptedFreshSchemaAndRecoversOnNextOpen() =
        runTest {
            withTestDatabase("interrupted-schema.db") { testDatabase ->
                val failure =
                    assertIs<LocalPolicyResult.Failure>(
                        testDatabase.open(
                            driverDecorator = { driver ->
                                FailingExecuteSqlDriver(driver, "CREATE TABLE local_policy_metadata")
                            },
                        ),
                    )
                assertEquals(LocalPolicyFailure.STORAGE_FAILURE, failure.reason)
                assertTrue(testDatabase.exists())
                testDatabase.withRawDriver { driver ->
                    assertEquals(
                        emptyList(),
                        driver.queryStrings(
                            "SELECT name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name",
                        ),
                    )
                }

                val recovered = testDatabase.openStore()
                assertState(recovered.read(), 0, emptyList())
                recovered.close()
            }
        }

    @Test
    fun mapsInvalidFreshSchemaMetadataToStorageFailure() =
        runTest {
            withTestDatabase("invalid-fresh-schema-metadata.db") { testDatabase ->
                val failure =
                    assertIs<LocalPolicyResult.Failure>(
                        testDatabase.open(
                            driverDecorator = { driver ->
                                SkippingExecuteSqlDriver(driver, "INSERT INTO policy_schema")
                            },
                        ),
                    )
                assertEquals(LocalPolicyFailure.STORAGE_FAILURE, failure.reason)
                assertTrue(testDatabase.exists())
            }
        }

    @Test
    fun rejectsAndPreservesAViewOnlyExistingDatabase() =
        runTest {
            withTestDatabase("view-only.db") { testDatabase ->
                testDatabase.createViewOnlySchema()

                val failure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.UNSUPPORTED_SCHEMA, failure.reason)
                assertTrue(testDatabase.exists())
                testDatabase.withRawDriver { driver ->
                    assertEquals(
                        listOf("orphan_view"),
                        driver.queryStrings(
                            "SELECT name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name",
                        ),
                    )
                }
            }
        }

    @Test
    fun mapsPostOpenQueryFailuresAndRollsBackReplacement() =
        runTest {
            withTestDatabase("post-open-query-failure.db") { testDatabase ->
                var failingDriver: FailingQuerySqlDriver? = null
                val store =
                    testDatabase.openStore(
                        driverDecorator = { driver ->
                            FailingQuerySqlDriver(driver).also { decorated -> failingDriver = decorated }
                        },
                    )
                assertState(store.replace(0, policy("preserved.example")), 1, listOf("preserved.example"))

                checkNotNull(failingDriver).failingSqlFragment = "FROM local_policy_metadata"
                val readFailure = assertIs<LocalPolicyResult.Failure>(store.read())
                assertEquals(LocalPolicyFailure.STORAGE_FAILURE, readFailure.reason)

                checkNotNull(failingDriver).failingSqlFragment = null
                assertState(store.read(), 1, listOf("preserved.example"))

                checkNotNull(failingDriver).failingSqlFragment = "FROM exact_domain_policy"
                val replaceFailure =
                    assertIs<LocalPolicyResult.Failure>(
                        store.replace(1, policy("discarded.example")),
                    )
                assertEquals(LocalPolicyFailure.STORAGE_FAILURE, replaceFailure.reason)

                checkNotNull(failingDriver).failingSqlFragment = null
                assertState(store.read(), 1, listOf("preserved.example"))
                store.close()
            }
        }

    @Test
    fun rejectsInvalidRevisionCardinality() =
        runTest {
            withTestDatabase("invalid-revision-cardinality.db") { testDatabase ->
                val store = testDatabase.openStore()
                store.close()
                testDatabase.withRawDriver { driver ->
                    driver
                        .execute(
                            identifier = null,
                            sql = "DELETE FROM local_policy_metadata",
                            parameters = 0,
                        ).value
                }

                val missingFailure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.CORRUPTION, missingFailure.reason)

                testDatabase.withRawDriver { driver ->
                    driver
                        .execute(
                            identifier = null,
                            sql = "DROP TABLE local_policy_metadata",
                            parameters = 0,
                        ).value
                    driver
                        .execute(
                            identifier = null,
                            sql = "CREATE TABLE local_policy_metadata (singleton INTEGER, revision INTEGER)",
                            parameters = 0,
                        ).value
                    driver
                        .execute(
                            identifier = null,
                            sql =
                                "INSERT INTO local_policy_metadata(singleton, revision) " +
                                    "VALUES (1, 0), (1, 1), (1, 2)",
                            parameters = 0,
                        ).value
                }

                val duplicateFailure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.CORRUPTION, duplicateFailure.reason)
            }
        }

    @Test
    fun rejectsANullableStoredDomain() =
        runTest {
            withTestDatabase("nullable-domain.db") { testDatabase ->
                val store = testDatabase.openStore()
                store.close()
                testDatabase.withRawDriver { driver ->
                    driver
                        .execute(
                            identifier = null,
                            sql = "DROP TABLE exact_domain_policy",
                            parameters = 0,
                        ).value
                    driver
                        .execute(
                            identifier = null,
                            sql = "CREATE TABLE exact_domain_policy (canonical_domain TEXT)",
                            parameters = 0,
                        ).value
                    driver
                        .execute(
                            identifier = null,
                            sql = "INSERT INTO exact_domain_policy(canonical_domain) VALUES (NULL)",
                            parameters = 0,
                        ).value
                }

                val failure = assertIs<LocalPolicyResult.Failure>(testDatabase.open())
                assertEquals(LocalPolicyFailure.CORRUPTION, failure.reason)
                assertTrue(testDatabase.exists())
            }
        }

    @Test
    fun enforcesCanonicalBoundsAndRedactsDomainValues() =
        runTest {
            val invalidValues =
                listOf(
                    "singlelabel",
                    "UPPER.example",
                    "https://valid.example",
                    "*.example.com",
                    "127.0.0.1",
                    "-invalid.example",
                    "invalid-.example",
                    "ab--invalid.example",
                    "invalid_example.com",
                )
            invalidValues.forEach { value ->
                val failure =
                    assertIs<ExactDomainPolicyValidationResult.Failure>(
                        ExactDomainPolicy.fromCanonicalValues(listOf(value)),
                    )
                assertEquals(ExactDomainPolicyValidationFailure.INVALID_CANONICAL_DOMAIN, failure.reason)
            }

            val tooMany = (0..ExactDomainPolicyLimits.MAX_DOMAIN_COUNT).map { index -> "domain$index.example" }
            val failure =
                assertIs<ExactDomainPolicyValidationResult.Failure>(
                    ExactDomainPolicy.fromCanonicalValues(tooMany),
                )
            assertEquals(ExactDomainPolicyValidationFailure.TOO_MANY_DOMAINS, failure.reason)

            val policy = policy("private.example")
            assertFalse(policy.toString().contains("private.example"))
            assertFalse(
                policy.domains
                    .single()
                    .toString()
                    .contains("private.example"),
            )
            val state = LocalExactDomainPolicyState(7, policy)
            assertFalse(state.toString().contains("private.example"))
            assertTrue(state.toString().contains("domainCount=1"))
        }

    @Test
    fun keepsTheV1SchemaLimitedToPolicyMetadataAndDomains() =
        runTest {
            withTestDatabase("schema.db") { testDatabase ->
                val store = testDatabase.openStore()
                store.close()
                testDatabase.withRawDriver { driver ->
                    val tables =
                        driver.queryStrings(
                            "SELECT name FROM sqlite_master " +
                                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                        )
                    assertEquals(
                        listOf("exact_domain_policy", "local_policy_metadata", "policy_schema"),
                        tables,
                    )
                    assertEquals(
                        listOf("canonical_domain"),
                        driver.queryStrings("SELECT name FROM pragma_table_info('exact_domain_policy') ORDER BY cid"),
                    )
                    assertEquals(
                        listOf("singleton", "revision"),
                        driver.queryStrings("SELECT name FROM pragma_table_info('local_policy_metadata') ORDER BY cid"),
                    )
                    assertEquals(1, driver.querySingleLong("SELECT schema_version FROM policy_schema"))
                }
            }
        }

    @Test
    fun returnsTypedFailuresForInvalidRevisionAndClosedStore() =
        runTest {
            withTestDatabase("typed-failures.db") { testDatabase ->
                val store = testDatabase.openStore()
                val invalidRevision =
                    assertIs<LocalPolicyResult.Failure>(
                        store.replace(-1, ExactDomainPolicy.empty()),
                    )
                assertEquals(LocalPolicyFailure.INVALID_REVISION, invalidRevision.reason)
                store.close()
                val closedRead = assertIs<LocalPolicyResult.Failure>(store.read())
                assertEquals(LocalPolicyFailure.CLOSED, closedRead.reason)
            }
        }

    @Test
    fun dispatchesOpenReadWriteTransactionAndCloseToTheDatabaseContext() =
        runTest {
            withTestDatabase("dispatcher.db") { testDatabase ->
                val probe = DatabaseDispatcherProbe()
                val store =
                    testDatabase.openStore(
                        databaseDispatcher = probe.dispatcher(),
                        driverDecorator = { driver -> ProbingSqlDriver(driver, probe) },
                    )

                assertState(store.read(), 0, emptyList())
                assertState(store.replace(0, policy("dispatch.example")), 1, listOf("dispatch.example"))
                store.close()

                assertTrue(probe.operations.any { operation -> operation == "transaction:begin" })
                assertTrue(probe.operations.any { operation -> operation == "context:DRIVER_OPEN" })
                assertTrue(probe.operations.any { operation -> operation.startsWith("query:PRAGMA quick_check") })
                assertTrue(
                    probe.operations.any { operation ->
                        operation.startsWith("execute:UPDATE local_policy_metadata")
                    },
                )
                assertTrue(probe.operations.any { operation -> operation == "close" })
                val transactionStart = probe.operations.indexOf("transaction:begin")
                val transactionCompletion = probe.operations.indexOf("context:TRANSACTION_COMPLETED")
                assertTrue(transactionCompletion > transactionStart)
                assertTrue(probe.violations.isEmpty())
            }
        }

    @Test
    fun propagatesCancellationAfterRollingBackTheCompleteReplacement() =
        runTest {
            withTestDatabase("cancellation-rollback.db") { testDatabase ->
                val probe = DatabaseDispatcherProbe()
                var probingDriver: ProbingSqlDriver? = null
                val store =
                    testDatabase.openStore(
                        databaseDispatcher = probe.dispatcher(),
                        driverDecorator = { driver ->
                            ProbingSqlDriver(driver, probe).also { decorated -> probingDriver = decorated }
                        },
                    )
                assertState(store.replace(0, policy("preserved.example")), 1, listOf("preserved.example"))
                val completedTransactions =
                    probe.operations.count { operation ->
                        operation == "context:TRANSACTION_COMPLETED"
                    }
                checkNotNull(probingDriver).cancellationSqlFragment = "INSERT INTO exact_domain_policy"

                var cancellationPropagated = false
                try {
                    store.replace(1, policy("cancelled.example"))
                } catch (expectedCancellation: CancellationException) {
                    cancellationPropagated = true
                }
                assertTrue(cancellationPropagated)
                assertEquals(
                    completedTransactions + 1,
                    probe.operations.count { operation -> operation == "context:TRANSACTION_COMPLETED" },
                )
                checkNotNull(probingDriver).cancellationSqlFragment = null
                assertState(store.read(), 1, listOf("preserved.example"))
                store.close()
                assertTrue(probe.violations.isEmpty())
            }
        }

    @Test
    fun closesResourcesWhenOpenOrCallerIsCancelled() =
        runTest {
            withTestDatabase("open-cancellation.db") { testDatabase ->
                val openProbe = DatabaseDispatcherProbe()
                var openDriver: ProbingSqlDriver? = null

                var openCancellationPropagated = false
                try {
                    testDatabase.open(
                        databaseDispatcher = openProbe.dispatcher(),
                        driverDecorator = { driver ->
                            ProbingSqlDriver(driver, openProbe).also { decorated ->
                                decorated.cancellationSqlFragment = "PRAGMA quick_check"
                                openDriver = decorated
                            }
                        },
                    )
                } catch (expectedCancellation: CancellationException) {
                    openCancellationPropagated = true
                }
                assertTrue(openCancellationPropagated)
                assertTrue(checkNotNull(openDriver).cancellationSqlFragment != null)
                assertTrue(openProbe.operations.any { operation -> operation == "close" })
                assertTrue(openProbe.violations.isEmpty())

                val closeProbe = DatabaseDispatcherProbe()
                val store =
                    testDatabase.openStore(
                        databaseDispatcher = closeProbe.dispatcher(),
                        driverDecorator = { driver -> ProbingSqlDriver(driver, closeProbe) },
                    )
                val closeJob =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        currentCoroutineContext().job.cancel()
                        store.close()
                    }
                closeJob.join()
                assertTrue(closeProbe.operations.any { operation -> operation == "close" })
                assertTrue(closeProbe.violations.isEmpty())
            }
        }

    @Test
    fun closesTheDriverWhenCancellationWinsTheOpenResultHandoff() =
        runTest {
            withTestDatabase("handoff-cancellation.db") { testDatabase ->
                val probe = DatabaseDispatcherProbe()
                lateinit var opening: Deferred<LocalPolicyResult<LocalExactDomainPolicyStore>>
                val databaseDispatcher =
                    probe.dispatcher { operation ->
                        if (operation == DatabaseContextOperation.STORE_OPENED_FOR_HANDOFF) {
                            opening.cancel(CancellationException("Synthetic handoff cancellation"))
                        }
                    }
                opening =
                    async(start = CoroutineStart.LAZY) {
                        testDatabase.open(
                            databaseDispatcher = databaseDispatcher,
                            driverDecorator = { driver -> ProbingSqlDriver(driver, probe) },
                        )
                    }

                opening.start()
                var cancellationPropagated = false
                try {
                    opening.await()
                } catch (expectedCancellation: CancellationException) {
                    cancellationPropagated = true
                }
                opening.join()

                assertTrue(cancellationPropagated)
                assertTrue(probe.operations.any { operation -> operation == "context:STORE_OPENED_FOR_HANDOFF" })
                assertTrue(probe.operations.any { operation -> operation == "close" })
                assertTrue(probe.violations.isEmpty())
            }
        }
}

private fun policy(vararg domains: String): ExactDomainPolicy =
    when (val result = ExactDomainPolicy.fromCanonicalValues(domains.asList())) {
        is ExactDomainPolicyValidationResult.Success -> result.policy
        is ExactDomainPolicyValidationResult.Failure -> error(result.reason.name)
    }

private suspend fun LocalPolicyTestDatabase.openStore(
    databaseDispatcher: DatabaseDispatcher? = null,
    driverDecorator: (SqlDriver) -> SqlDriver = { driver -> driver },
): LocalExactDomainPolicyStore =
    when (val result = open(databaseDispatcher, driverDecorator)) {
        is LocalPolicyResult.Success -> result.value
        is LocalPolicyResult.Failure -> error(result.reason.name)
    }

private fun DatabaseDispatcherProbe.dispatcher(observe: (DatabaseContextOperation) -> Unit = {}): DatabaseDispatcher {
    return DatabaseDispatcher(this) { operation ->
        record("context:${operation.name}")
        observe(operation)
    }
}

private fun assertState(
    result: LocalPolicyResult<LocalExactDomainPolicyState>,
    revision: Long,
    domains: List<String>,
) {
    val state = assertIs<LocalPolicyResult.Success<LocalExactDomainPolicyState>>(result).value
    assertEquals(revision, state.revision)
    assertEquals(domains, state.policy.domains.map { domain -> domain.canonicalValue })
}

private suspend fun withTestDatabase(
    name: String,
    block: suspend (LocalPolicyTestDatabase) -> Unit,
) {
    val testDatabase = createLocalPolicyTestDatabase(name)
    try {
        block(testDatabase)
    } finally {
        testDatabase.delete()
    }
}

private fun SqlDriver.querySingleLong(sql: String): Long =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            check(cursor.next().value)
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        },
        parameters = 0,
    ).value

private fun SqlDriver.querySingleString(sql: String): String =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            check(cursor.next().value)
            QueryResult.Value(checkNotNull(cursor.getString(0)))
        },
        parameters = 0,
    ).value

private fun SqlDriver.queryStrings(sql: String): List<String> =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val values = mutableListOf<String>()
            while (cursor.next().value) {
                values += checkNotNull(cursor.getString(0))
            }
            QueryResult.Value(values)
        },
        parameters = 0,
    ).value

private class FailingQuerySqlDriver(
    private val delegate: SqlDriver,
    var failingSqlFragment: String? = null,
) : SqlDriver by delegate {
    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        val fragment = failingSqlFragment
        if (fragment != null && sql.contains(fragment)) {
            error("Synthetic validation execution failure")
        }
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }
}

private class FailingExecuteSqlDriver(
    private val delegate: SqlDriver,
    private val failingSqlFragment: String,
) : SqlDriver by delegate {
    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        if (sql.contains(failingSqlFragment)) {
            error("Synthetic schema creation failure")
        }
        return delegate.execute(identifier, sql, parameters, binders)
    }
}

private class SkippingExecuteSqlDriver(
    private val delegate: SqlDriver,
    private val skippedSqlFragment: String,
) : SqlDriver by delegate {
    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        return if (sql.contains(skippedSqlFragment)) {
            QueryResult.Value(0L)
        } else {
            delegate.execute(identifier, sql, parameters, binders)
        }
    }
}
