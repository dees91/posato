package app.posato.feature.session.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.PauseItems
import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.session.data.PART_SESSION
import app.posato.feature.session.data.RetainedPart
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.KeptApplication
import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A running manual session pauses its set's current items and keeps everything it already paused. */
class SessionCompositionTest {
    private val record = SessionRecord(SessionId(testIdentifier(21)), NOW, NOW + SessionLimits.MIN_DURATION_MILLIS)

    @Test
    fun `given a website removed from the running session's set when composed then it stays paused`() = runTest {
        withComposition("session-composition-removed.db") { composition ->
            setDomains = listOf("kept.example", "work.example")
            composition.compose(record)

            setDomains = listOf("work.example")
            val composed = composition.compose(record)

            assertEquals(listOf("kept.example", "work.example"), composed.policy?.domains?.map { it.canonicalValue })
        }
    }

    @Test
    fun `given a website added to the running session's set when composed then it is paused at once`() = runTest {
        withComposition("session-composition-added.db") { composition ->
            setDomains = listOf("work.example")
            composition.compose(record)

            setDomains = listOf("new.example", "work.example")
            val composed = composition.compose(record)

            assertEquals(listOf("new.example", "work.example"), composed.policy?.domains?.map { it.canonicalValue })
        }
    }

    @Test
    fun `given an app removed from the running session's set when composed then it stays paused`() = runTest {
        withComposition("session-composition-app.db") { composition ->
            setDomains = listOf("work.example")
            setApps = listOf(mail)
            composition.compose(record)

            setApps = emptyList()
            val composed = composition.compose(record)

            val mappings = (composed.mappings as LocalApplicationMappingsLoadResult.Success).snapshot.mappings
            assertEquals(listOf(mail.id), mappings.map { mapping -> mapping.id })
        }
    }

    @Test
    fun `given an app added to the running session's set when checked then the session is applied again`() = runTest {
        withComposition("session-composition-app-added.db") { composition ->
            setDomains = listOf("work.example")
            composition.compose(record)

            setApps = listOf(mail)

            assertTrue(composition.differs(record))
        }
    }

    @Test
    fun `given a schedule pausing items when the session joins past the limit then it pauses only what still fits`() = runTest {
        withComposition("session-composition-occupied.db", PauseLimits(maxDomainCost = 3, maxApps = 2) { it.size }) { composition ->
            composition.occupied = { PauseItems(setOf("evening1.example", "evening2.example")) }
            setDomains = listOf("b.example", "a.example")

            val composed = composition.compose(record)

            assertEquals(listOf("a.example"), composed.policy?.domains?.map { it.canonicalValue })
        }
    }

    private var setDomains: List<String> = emptyList()
    private var setApps: List<LocalApplicationMapping> = emptyList()
    private val mail = checkNotNull(LocalApplicationMapping.restore(checkNotNull(LocalApplicationMappingId.restore("ab".repeat(32))), "Mail"))

    private fun targetsOf(domains: List<String>): SessionTargetsState {
        val policy = (TargetPolicy.fromStoredValues(domains, null) as TargetPolicyValidationResult.Success).policy
        val snapshot = checkNotNull(LocalApplicationMappingsSnapshot.restore(setApps))
        return SessionTargetsState(policy, LocalApplicationMappingsLoadResult.Success(snapshot, LocalApplicationMappingsAccess.READY), record.setId)
    }

    private suspend fun withComposition(
        name: String,
        limits: PauseLimits = PauseLimits.MAC,
        block: suspend (SessionComposition) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            SqlLocalSessionStore(database, Dispatchers.Default)
                .start(record.sessionId, NOW, record.endEpochMillis, NOW, FrozenStartSet(persistentListOf(), 0))
            block(
                SessionComposition(
                    SqlPartRetentionStore(database, Dispatchers.Default),
                    limits,
                    { targetsOf(setDomains) },
                    { ids ->
                        listOf(KeptApplication("ab".repeat(32).hexToByteArray(), "Mail".encodeToByteArray(), byteArrayOf(9))).takeIf {
                            mail.id.canonicalValue in
                                ids
                        }.orEmpty()
                    },
                ),
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val NOW: Long = 1_790_000_000_000L
    }
}
