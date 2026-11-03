package app.posato.feature.sync.domain

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.WebsiteWindow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Writer validation for kind 22: a rule only for a website present in a live set here, clearing as the none pair. */
class SyncWriterWindowTest {
    private val news = checkNotNull(ExactDomain.restore("news.example"))
    private val hourly = checkNotNull(WebsiteWindow.of(60, 5))

    private suspend fun openWriter(store: FakeSyncReplicaStore): SyncWriter {
        return assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
    }

    @Test
    fun `given a present website when a rule is set and then cleared then both are authored and the projection follows`() = runTest {
        val store = FakeSyncReplicaStore(snapshot())
        val writer = openWriter(store)
        assertIs<LocalMutationResult.Success>(writer.mutate(LocalSyncMutation.PresentDomain(news, PauseSetId.FIRST)))

        val set = assertIs<LocalMutationResult.Success>(writer.mutate(LocalSyncMutation.SetDomainWindow(news, PauseSetId.FIRST, hourly)))
        assertEquals(hourly, set.projection.pauseSets.single().windows[news])

        val cleared = assertIs<LocalMutationResult.Success>(writer.mutate(LocalSyncMutation.SetDomainWindow(news, PauseSetId.FIRST, null)))
        assertEquals(emptyMap(), cleared.projection.pauseSets.single().windows)
        val authored = store.current.acceptedBundles.values.map { it.operation.payload }.filterIsInstance<SyncOperationPayload.SetDomainWindow>()
        assertEquals(listOf(hourly, null), authored.map { it.window }.sortedBy { it?.periodMinutes ?: Int.MAX_VALUE })
    }

    @Test
    fun `given a website that is absent or a set that is not live when a rule is set then the writer refuses it`() = runTest {
        val unknownSet = checkNotNull(PauseSetId.of(testIdentifier(81)))
        listOf(
            LocalSyncMutation.SetDomainWindow(news, PauseSetId.FIRST, hourly),
            LocalSyncMutation.SetDomainWindow(news, PauseSetId.FIRST, null),
            LocalSyncMutation.SetDomainWindow(news, unknownSet, hourly),
        ).forEach { invalid ->
            val store = FakeSyncReplicaStore(snapshot())
            val writer = openWriter(store)
            val before = store.current

            assertEquals(LocalMutationFailure.INVALID_MUTATION, assertIs<LocalMutationResult.Failure>(writer.mutate(invalid)).reason)
            assertEquals(before, store.current)
        }
    }
}
