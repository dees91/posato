package app.posato.feature.targets.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Migration 13 on a database a released 1.2 build left behind (schema version 12), seeded with synthetic
 * rows in every table the migration rewrites. Expectations come from the pause set rules: everything
 * lands in the all-zero first set, nothing queued for kind 4 survives, and the one-time upgrade step is
 * pending.
 */
class SqlPauseSetMigrationTest {
    @Test
    fun `given a released version twelve database when migrated then every row lands in the first set`() {
        val testDatabase = createLocalPolicyTestDatabase("pause-set-migration.db")
        val seeding = testDatabase.openDriverAt(RELEASED_VERSION)
        SEEDS.forEach(seeding::executeSql)
        seeding.close()

        val driver = testDatabase.openDriver()
        try {
            assertEquals(listOf("$FIRST|-|0"), driver.rows("SELECT hex(set_id) || '|' || ifnull(name, '-') || '|' || refused FROM local_pause_set"))
            assertEquals(emptyList(), driver.rows("SELECT hex(set_id) FROM local_pause_set_default"))
            assertEquals(
                listOf("$FIRST|a.example", "$FIRST|b.example"),
                driver.rows("SELECT hex(set_id) || '|' || canonical_domain FROM local_pause_set_domain ORDER BY canonical_domain"),
            )
            assertEquals(emptyList(), driver.rows("SELECT name FROM sqlite_master WHERE name = 'exact_domain_policy'"))
            assertEquals(listOf("5"), driver.rows("SELECT revision FROM local_policy_metadata"))
            assertEquals(listOf("Applications"), driver.rows("SELECT canonical_name FROM application_policy"))
            assertEquals(
                listOf("1|domain_present|$FIRST|c.example", "3|domain_absent|$FIRST|b.example"),
                driver.rows(
                    "SELECT sequence || '|' || kind || '|' || hex(set_id) || '|' || canonical_domain " +
                        "FROM sync_policy_intent ORDER BY sequence",
                ),
            )
            assertEquals(listOf("$FIRST|a.example"), driver.rows("SELECT hex(set_id) || '|' || canonical_domain FROM sync_policy_base_domain"))
            assertEquals(listOf("1"), driver.rows("SELECT singleton FROM sync_policy_base"))
            assertEquals(listOf("$FOCUS|$FIRST"), driver.rows("SELECT hex(schedule_id) || '|' || hex(set_id) FROM local_schedule"))
            assertEquals(
                listOf("put|$FIRST", "skip|$FIRST"),
                driver.rows(
                    "SELECT kind || '|' || hex(set_id) " +
                        "FROM sync_schedule_intent ORDER BY sequence",
                ),
            )
            assertEquals(
                listOf("session_start|$FIRST", "session_end|$FIRST"),
                driver.rows(
                    "SELECT kind || '|' || hex(set_id) " +
                        "FROM sync_session_intent ORDER BY sequence",
                ),
            )
            assertEquals(
                listOf("$SESSION|$FIRST|[\"a.example\"]"),
                driver.rows(
                    "SELECT hex(session_id) || '|' || hex(set_id) || '|' || frozen_domains " +
                        "FROM local_session",
                ),
            )
            assertEquals(listOf("skip"), driver.rows("SELECT kind FROM local_schedule_fact"))
            assertEquals(listOf("$FOCUS"), driver.rows("SELECT hex(schedule_id) FROM local_schedule_pin"))
            assertEquals(listOf("1"), driver.rows("SELECT singleton FROM local_pause_set_upgrade"))
            assertEquals(emptyList(), driver.rows("SELECT hex(workspace_id) FROM sync_pause_sets_enabled"))
            assertEquals(emptyList(), driver.rows("SELECT part_kind FROM local_retained_domain"))
            assertEquals(listOf("14"), driver.rows("PRAGMA user_version"))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a fresh install when created then the first set exists and no upgrade step is pending`() {
        val testDatabase = createLocalPolicyTestDatabase("pause-set-fresh.db")
        val driver = testDatabase.openDriver()
        try {
            assertEquals(listOf("$FIRST|-|0"), driver.rows("SELECT hex(set_id) || '|' || ifnull(name, '-') || '|' || refused FROM local_pause_set"))
            assertEquals(emptyList(), driver.rows("SELECT singleton FROM local_pause_set_upgrade"))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given migration thirteen fails partway when opened then the database keeps its version and rows`() {
        val testDatabase = createLocalPolicyTestDatabase("pause-set-atomic.db")
        val seeding = testDatabase.openDriverAt(PREVIOUS_VERSION)
        seeding.executeSql("INSERT INTO exact_domain_policy(canonical_domain) VALUES ('a.example')")
        seeding.executeSql("CREATE TABLE sync_policy_base_domain_v2 (conflict INTEGER)")
        seeding.close()

        // The JVM driver migrates when it opens and the iOS driver on its first statement; either way it fails.
        assertFails {
            val failing = testDatabase.openDriver()
            try {
                failing.rows("SELECT revision FROM local_policy_metadata")
            } finally {
                failing.close()
            }
        }

        val reopened = testDatabase.openDriverAt(PREVIOUS_VERSION)
        try {
            assertEquals(listOf("13"), reopened.rows("PRAGMA user_version"))
            assertEquals(listOf("a.example"), reopened.rows("SELECT canonical_domain FROM exact_domain_policy"))
            assertEquals(emptyList(), reopened.rows("SELECT name FROM sqlite_master WHERE name = 'local_pause_set'"))
        } finally {
            reopened.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val RELEASED_VERSION: Long = 12
        const val PREVIOUS_VERSION: Long = 13
        const val FIRST: String = "00000000000000000000000000000000"
        const val FOCUS: String = "000000000000400080000000000000A1"
        const val SESSION: String = "000102030405060708090A0B0C0D0E0F"
        const val WORKSPACE: String = "0000000000004000800000000000000B"
        val SEEDS: List<String> = listOf(
            "UPDATE local_policy_metadata SET revision = 5 WHERE singleton = 1",
            "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('a.example'), ('b.example')",
            "INSERT INTO application_policy(singleton, canonical_name) VALUES (1, 'Applications')",
            "INSERT INTO sync_policy_base(singleton) VALUES (1)",
            "INSERT INTO sync_policy_base_domain(canonical_domain) VALUES ('a.example')",
            "INSERT INTO sync_policy_base_application(singleton, canonical_name) VALUES (1, 'Applications')",
            "INSERT INTO sync_policy_intent(sequence, workspace_id, kind, canonical_domain, canonical_name) VALUES " +
                "(1, X'$WORKSPACE', 'domain_present', 'c.example', NULL), " +
                "(2, X'$WORKSPACE', 'application_present', NULL, 'Applications'), " +
                "(3, X'$WORKSPACE', 'domain_absent', 'b.example', NULL)",
            "INSERT INTO local_schedule(schedule_id, name, weekdays, start_minute, end_minute, enabled, refused) " +
                "VALUES (X'$FOCUS', 'Focus', 1, 540, 600, 1, 0)",
            "INSERT INTO local_schedule_fact VALUES (X'$FOCUS', 'skip', 2026, 10, 5)",
            "INSERT INTO local_schedule_pin VALUES (X'$FOCUS', 2026, 9, 21, 1790000000000, 1)",
            "INSERT INTO sync_schedule_intent(workspace_id, kind, schedule_id, name, weekdays, start_minute, end_minute, enabled) " +
                "VALUES (X'$WORKSPACE', 'put', X'$FOCUS', 'Focus', 1, 540, 600, 1)",
            "INSERT INTO sync_schedule_intent(workspace_id, kind, schedule_id, year, month, day, author_year, author_month, author_day) " +
                "VALUES (X'$WORKSPACE', 'skip', X'$FOCUS', 2026, 10, 5, 2026, 9, 30)",
            "INSERT INTO sync_session_intent(workspace_id, kind, session_id, start_epoch_millis, end_epoch_millis) " +
                "VALUES (X'$WORKSPACE', 'session_start', X'$SESSION', 1700000000000, 1700000150000)",
            "INSERT INTO sync_session_intent(workspace_id, kind, session_id) VALUES (X'$WORKSPACE', 'session_end', X'$SESSION')",
            "INSERT INTO local_session(singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early, frozen_domains, " +
                "frozen_application_count, origin) VALUES (1, X'$SESSION', 1700000000000, 1700000150000, 0, '[\"a.example\"]', 0, 'local')",
        )
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).value
}

private fun SqlDriver.rows(sql: String): List<String> {
    return executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            QueryResult.Value(buildList { while (cursor.next().value) add(cursor.getString(0) ?: "-") })
        },
        parameters = 0,
    ).value
}
