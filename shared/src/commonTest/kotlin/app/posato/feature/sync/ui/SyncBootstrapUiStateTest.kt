package app.posato.feature.sync.ui

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.bootstrap.AppleBootstrap
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
import app.posato.feature.sync.bootstrap.BootstrapResult
import app.posato.feature.sync.bootstrap.BootstrapState
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.sync.bootstrap.FakeBootstrapAccountPort
import app.posato.feature.sync.bootstrap.FakeBootstrapCloudPort
import app.posato.feature.sync.bootstrap.FakeBootstrapKeyPort
import app.posato.feature.sync.bootstrap.FakeBootstrapStore
import app.posato.feature.sync.bootstrap.bindingA
import app.posato.feature.sync.testContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncBootstrapUiStateTest {
    @Test
    fun `given a fresh holder when sync runs twice rapidly then one attempt establishes readiness`() = runTest {
        val account = FakeBootstrapAccountPort()
        val holder = SyncBootstrapUiState(
            AppleBootstrap(
                BootstrapCoordinator(
                    account,
                    FakeBootstrapCloudPort(),
                    FakeBootstrapKeyPort(),
                    FakeBootstrapStore(),
                    FakeSyncCryptoProvider(),
                ),
                StandardTestDispatcher(testScheduler),
            ),
            this,
        )

        holder.sync()
        holder.sync()
        advanceUntilIdle()

        assertEquals(1, account.calls)
        assertIs<BootstrapResult.Ready>(holder.outcome)
    }

    @Test
    fun `given an established store when refreshed then linked is true without provider access`() = runTest {
        val account = FakeBootstrapAccountPort()
        val store = FakeBootstrapStore()
        store.state = BootstrapState.Established(EstablishedWorkspace(testContext, bindingA))
        val holder = SyncBootstrapUiState(
            AppleBootstrap(
                BootstrapCoordinator(
                    account,
                    FakeBootstrapCloudPort(),
                    FakeBootstrapKeyPort(),
                    store,
                    FakeSyncCryptoProvider(),
                ),
                StandardTestDispatcher(testScheduler),
            ),
            this,
        )

        holder.refreshLinked()

        assertEquals(true, holder.linked)
        assertEquals(0, account.calls)
    }
}
