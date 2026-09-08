package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.testContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AppleBootstrapTest {
    @Test
    fun `given fresh fakes when constructed then no provider access happened before the action`() = runTest {
        val harness = AppleBootstrapHarness(background = StandardTestDispatcher(testScheduler))

        assertEquals(0, harness.account.calls)
        assertEquals(0, harness.cloud.zoneFetchCalls)
        assertEquals(0, harness.cloud.zoneSaveCalls)
        assertEquals(0, harness.cloud.anchorReadCalls)
        assertEquals(0, harness.keys.readCalls)
        assertEquals(0, harness.keys.createCalls)
        assertEquals(0, harness.store.persistCalls)
        assertEquals(0, harness.store.commitCalls)
    }

    @Test
    fun `given a fresh workspace when sync runs then one attempt establishes readiness`() = runTest {
        val harness = AppleBootstrapHarness(background = StandardTestDispatcher(testScheduler))

        val result = harness.bootstrap.syncWithIcloud()

        assertIs<BootstrapResult.Ready>(result)
        val committed = harness.store.state
        assertIs<BootstrapState.Established>(committed)
        assertEquals(committed.workspace.context, result.context)
    }

    @Test
    fun `given two runtimes in one process when sync runs concurrently then attempts never overlap`() = runTest {
        val cloud = SerialCheckCloudPort()
        val store = FakeBootstrapStore()
        val first = secondRuntime(cloud, store)
        val second = secondRuntime(cloud, store)

        val firstAttempt = async(Dispatchers.Default) { first.syncWithIcloud() }
        val secondAttempt = async(Dispatchers.Default) { second.syncWithIcloud() }

        assertIs<BootstrapResult.Ready>(firstAttempt.await())
        assertIs<BootstrapResult.Ready>(secondAttempt.await())
        assertEquals(1, cloud.maxObserved)
    }

    @Test
    fun `given an established store when established context is read then the context is returned without provider access`() = runTest {
        val harness = AppleBootstrapHarness(background = StandardTestDispatcher(testScheduler))
        harness.store.state = BootstrapState.Established(EstablishedWorkspace(testContext, bindingA))

        val context = harness.bootstrap.establishedContext()

        assertEquals(testContext, context)
        assertEquals(0, harness.account.calls)
        assertEquals(0, harness.cloud.zoneFetchCalls)
        assertEquals(0, harness.keys.readCalls)
    }

    @Test
    fun `given no established store when established context is read then null is returned`() = runTest {
        val harness = AppleBootstrapHarness(background = StandardTestDispatcher(testScheduler))

        assertNull(harness.bootstrap.establishedContext())
    }

    @Test
    fun `given a provider failure when sync runs then a retryable outcome is reported`() = runTest {
        val bootstrap = AppleBootstrap(
            BootstrapCoordinator(
                ThrowingAccountPort(IllegalStateException("companion failed")),
                FakeBootstrapCloudPort(),
                FakeBootstrapKeyPort(),
                FakeBootstrapStore(),
                FakeSyncCryptoProvider(),
            ),
            StandardTestDispatcher(testScheduler),
        )

        val result = bootstrap.syncWithIcloud()

        assertIs<BootstrapResult.Retryable>(result)
    }

    @Test
    fun `given an injected dispatcher when sync runs then the attempt honors that dispatcher`() = runTest {
        val recording = RecordingDispatcher(StandardTestDispatcher(testScheduler))
        val harness = AppleBootstrapHarness(background = recording)

        val result = harness.bootstrap.syncWithIcloud()

        assertIs<BootstrapResult.Ready>(result)
        assertEquals(true, recording.uses > 0)
    }
}

private fun secondRuntime(
    cloud: BootstrapCloudPort,
    store: BootstrapStore
): AppleBootstrap {
    return AppleBootstrap(
        BootstrapCoordinator(
            FakeBootstrapAccountPort(),
            cloud,
            FakeBootstrapKeyPort(),
            store,
            FakeSyncCryptoProvider(),
        ),
        Dispatchers.Default,
    )
}

private class AppleBootstrapHarness(
    val account: FakeBootstrapAccountPort = FakeBootstrapAccountPort(),
    val cloud: FakeBootstrapCloudPort = FakeBootstrapCloudPort(),
    val keys: FakeBootstrapKeyPort = FakeBootstrapKeyPort(),
    val store: FakeBootstrapStore = FakeBootstrapStore(),
    background: CoroutineDispatcher = Dispatchers.Default,
) {
    val bootstrap = AppleBootstrap(
        BootstrapCoordinator(account, cloud, keys, store, FakeSyncCryptoProvider()),
        background,
    )
}

private class SerialCheckCloudPort(
    private val delegate: FakeBootstrapCloudPort = FakeBootstrapCloudPort(),
) : BootstrapCloudPort by delegate {
    private val guard = Mutex()
    private var current = 0
    var maxObserved = 0
        private set

    override suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult {
        guard.withLock {
            current += 1
            maxObserved = maxOf(maxObserved, current)
        }
        try {
            delay(100)
            return delegate.fetchZone(expectedBinding)
        } finally {
            guard.withLock {
                current -= 1
            }
        }
    }
}

private class ThrowingAccountPort(
    private val failure: RuntimeException,
) : BootstrapAccountPort {
    override suspend fun resolveBinding(): BindingResolution {
        throw failure
    }
}

private class RecordingDispatcher(
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    var uses = 0
        private set

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable
    ) {
        uses += 1
        delegate.dispatch(context, block)
    }
}
