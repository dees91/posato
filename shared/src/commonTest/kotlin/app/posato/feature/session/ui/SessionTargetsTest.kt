package app.posato.feature.session.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Session setup offers every set that can pause something here, including one with only apps chosen. */
class SessionTargetsTest {
    @Test
    fun `given a set with only apps that is not the default when targets load then its choice counts its apps`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-targets-apps.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), Dispatchers.Default)
            val work = checkNotNull(PauseSetId.of(testIdentifier(80)))
            val initial = (store.read() as LocalPolicyResult.Success<LocalTargetPolicyState>).value
            val sets = PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(work, "Work", emptyList())), null)
            store.replaceSets(initial.revision, checkNotNull(sets))
            val mail = checkNotNull(LocalApplicationMapping.restore(checkNotNull(LocalApplicationMappingId.restore("ab".repeat(32))), "Mail"))
            val mappings = FakeSessionMappings(
                LocalApplicationMappingsLoadResult.Success(
                    checkNotNull(LocalApplicationMappingsSnapshot.restore(listOf(mail))),
                    LocalApplicationMappingsAccess.READY,
                ),
            )

            val targets = loadSessionTargets(store, mappings)

            assertEquals(1, targets.sets.choices(targets.applicationCounts).first { row -> row.id == work }.applicationCount)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }
}
