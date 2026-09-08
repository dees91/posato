package app.posato.di

import app.posato.feature.session.ui.FakeEnforcementPort
import app.posato.feature.session.ui.FakeSessionMappings
import app.posato.feature.sync.bootstrap.BootstrapResult
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class DesktopBootstrapCompositionTest {
    @Test
    fun `given the real desktop graph when resolved twice then the bootstrap facade is a singleton`() {
        val databasePath = isolatedDatabasePath()
        val graph = createGraphFactory<DesktopApplicationGraph.Factory>().create(
            FakeSessionMappings(),
            FakeEnforcementPort(),
            databasePath,
        )

        assertSame(graph.appleBootstrap, graph.appleBootstrap)
    }

    @Test
    fun `given an unverifiable companion when sync runs then a truthful non-ready outcome is reported`() {
        val databasePath = isolatedDatabasePath()
        val graph = createGraphFactory<DesktopApplicationGraph.Factory>().create(
            FakeSessionMappings(),
            FakeEnforcementPort(),
            databasePath,
        )

        val result = runBlocking { graph.appleBootstrap.syncWithIcloud() }

        assertIs<BootstrapResult.Retryable>(result)
    }

    @Test
    fun `given the real desktop graph when inspected then bootstrap runs on the io dispatcher`() {
        val databasePath = isolatedDatabasePath()
        val graph = createGraphFactory<DesktopApplicationGraph.Factory>().create(
            FakeSessionMappings(),
            FakeEnforcementPort(),
            databasePath,
        )

        assertSame(Dispatchers.IO, graph.appleBootstrap.backgroundDispatcher)
    }

    private fun isolatedDatabasePath(): String {
        val directory = Files.createTempDirectory("posato-sync-009-test")

        return directory.resolve("posato-policy.db").toString()
    }
}
