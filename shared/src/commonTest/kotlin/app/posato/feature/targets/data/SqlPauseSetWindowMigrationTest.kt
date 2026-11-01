package app.posato.feature.targets.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.WebsiteWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Migration 16 on a database a released 1.6-pre build left behind (schema version 15). Failure modes: rebuilding
 * the policy intent queue for kind 22 drops a website change not yet published, or a rule is not kept by
 * set and domain, so it survives the website's removal or lands in another set.
 */
class SqlPauseSetWindowMigrationTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val news = checkNotNull(ExactDomain.restore("news.example"))
    private val mail = checkNotNull(ExactDomain.restore("mail.example"))
    private val hourly = checkNotNull(WebsiteWindow.of(60, 5))
    private val workspace = ByteArray(16) { 9 }

    @Test
    fun `given a released version fifteen database with pending intents when migrated then they survive and rules can be queued`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("pause-set-window-migration-15.db")
        val seeding = testDatabase.openDriverAt(15)
        seeding.executeSql("INSERT INTO local_pause_set_domain(set_id, canonical_domain) VALUES (zeroblob(16), 'news.example')")
        seeding.executeSql(
            "INSERT INTO sync_policy_intent(workspace_id, kind, set_id, canonical_domain, set_name) " +
                "VALUES (X'09090909090909090909090909090909', 'domain_present', zeroblob(16), 'news.example', NULL)",
        )
        seeding.close()

        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), Dispatchers.Default)
            val present = StoredPolicyIntent.PresentDomain(news, PauseSetId.FIRST)
            assertEquals(listOf<StoredPolicyIntent>(present), store.readIntents().intents())

            val rule = StoredPolicyIntent.SetWindow(news, PauseSetId.FIRST, hourly)
            val clear = StoredPolicyIntent.SetWindow(news, PauseSetId.FIRST, null)
            assertIs<LocalPolicyResult.Success<Unit>>(store.recordIntents(PolicySyncWrite(workspace, listOf(rule, clear))))

            assertEquals(listOf(present, rule, clear), store.readIntents().intents())
            assertEquals(listOf(news), assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value.sets.domainsOf(PauseSetId.FIRST))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given sets with rules when replaced then each rule reads back for its own set and website only`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("pause-set-window-store.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), Dispatchers.Default)
            val sets = checkNotNull(
                PauseSets.of(
                    listOf(
                        LocalPauseSet(PauseSetId.FIRST, null, listOf(mail, news)),
                        LocalPauseSet(work, "Work", listOf(news), windows = mapOf(news to hourly)),
                    ),
                    null,
                ),
            )

            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceSets(0, sets))
            val read = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value.sets

            assertEquals(sets, read)
            assertEquals(mapOf(news to hourly), read.sets.single { it.id == work }.windows)
            assertEquals(emptyMap(), read.sets.single { it.id == PauseSetId.FIRST }.windows)
            assertEquals(listOf("1"), driver.rows("SELECT count(*) FROM local_pause_set_window"))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a rule for a website that is not in its set when the sets are built then they are refused`() {
        assertNull(PauseSets.of(listOf(LocalPauseSet(work, "Work", listOf(mail), windows = mapOf(news to hourly))), null))
    }
}

private fun LocalPolicyResult<List<SequencedPolicyIntent>>.intents(): List<StoredPolicyIntent> {
    return assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(this).value.map { it.intent }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).value
}

private fun SqlDriver.rows(sql: String): List<String> {
    return executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor -> QueryResult.Value(buildList { while (cursor.next().value) add(cursor.getString(0) ?: "-") }) },
        parameters = 0,
    ).value
}
