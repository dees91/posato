package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BootstrapJoinTest {
    @Test
    fun `given a waiting join when the coordinator is recreated then consent is required again`() = runTest {
        val harness = JoinHarness()
        harness.waitForKey()
        val recreated = BootstrapCoordinator(harness.account, harness.cloud, harness.keys, harness.store, FakeSyncCryptoProvider())
        val reads = harness.account.calls
        assertFalse(recreated.joins.hasPending())
        assertEquals(JoinCheckResult.STATE_CHANGED, recreated.joins.recheck())
        assertEquals(reads, harness.account.calls)
        assertIs<BootstrapState.None>(harness.store.state)
    }

    @Test
    fun `given no waiting consent when rechecked then no provider is touched`() = runTest {
        val harness = JoinHarness()
        assertEquals(JoinCheckResult.STATE_CHANGED, harness.coordinator.joins.recheck())
        assertEquals(0, harness.account.calls)
        assertEquals(0, harness.cloud.zoneFetchCalls)
        assertEquals(0, harness.keys.readCalls)
    }

    @Test
    fun `given an absent key when rechecked then waiting remains read only`() = runTest {
        val harness = JoinHarness()
        harness.waitForKey()
        assertEquals(JoinCheckResult.WAITING, harness.coordinator.joins.recheck())
        assertTrue(harness.coordinator.joins.hasPending())
        harness.assertNoWrites()
        assertIs<BootstrapState.None>(harness.store.state)
    }

    @Test
    fun `given a delivered key when rechecked twice then adoption commits once`() = runTest {
        val harness = JoinHarness()
        harness.waitForKey()
        harness.deliverKey()
        assertEquals(JoinCheckResult.READY, harness.coordinator.joins.recheck())
        assertEquals(JoinCheckResult.STATE_CHANGED, harness.coordinator.joins.recheck())
        assertEquals(1, harness.store.commitCalls)
        assertEquals(0, harness.store.persistCalls)
        assertFalse(harness.coordinator.joins.hasPending())
    }

    @Test
    fun `given a switched account when rechecked then no workspace or key access follows`() = runTest {
        val harness = JoinHarness()
        harness.waitForKey()
        val reads = harness.cloud.zoneFetchCalls
        val keyReads = harness.keys.readCalls
        harness.account.default = BindingResolution.Available(bindingB)
        assertEquals(JoinCheckResult.ACTION_REQUIRED, harness.coordinator.joins.recheck())
        assertEquals(reads, harness.cloud.zoneFetchCalls)
        assertEquals(keyReads, harness.keys.readCalls)
        assertFalse(harness.coordinator.joins.hasPending())
        harness.assertNoWrites()
    }

    @Test
    fun `given a removed or replaced workspace when rechecked then a new setup is never created`() = runTest {
        for (replacement in listOf(null, JoinHarness.anchor.copy(workspaceId = WorkspaceId(testIdentifier(99))))) {
            val harness = JoinHarness()
            harness.waitForKey()
            harness.cloud.storedAnchor = replacement
            assertEquals(
                if (replacement == null) JoinCheckResult.LOCAL_ONLY else JoinCheckResult.ACTION_REQUIRED,
                harness.coordinator.joins.recheck(),
            )
            assertFalse(harness.coordinator.joins.hasPending())
            harness.assertNoWrites()
        }
        val harness = JoinHarness()
        harness.waitForKey()
        harness.cloud.zoneExists = false
        assertEquals(JoinCheckResult.LOCAL_ONLY, harness.coordinator.joins.recheck())
        harness.assertNoWrites()
    }

    @Test
    fun `given temporary provider failures when rechecked then the consented wait is retained`() = runTest {
        val cases: List<(JoinHarness) -> Unit> = listOf(
            { it.account.default = BindingResolution.Undetermined },
            { it.cloud.scriptZoneFetch(ZoneFetchResult.Retryable) },
            { it.cloud.scriptZoneFetch(ZoneFetchResult.UnknownOutcome) },
            { it.cloud.scriptAnchorRead(AnchorReadResult.Retryable) },
            { it.cloud.scriptAnchorRead(AnchorReadResult.UnknownOutcome) },
            { it.keys.scriptRead(KeyItemReadResult.Retryable) },
            { it.keys.scriptRead(KeyItemReadResult.UnknownOutcome) },
        )
        for (configure in cases) {
            val harness = JoinHarness()
            harness.waitForKey()
            configure(harness)
            assertEquals(JoinCheckResult.UNCHANGED, harness.coordinator.joins.recheck())
            assertTrue(harness.coordinator.joins.hasPending())
            harness.assertNoWrites()
        }
    }

    @Test
    fun `given definitive provider failures when rechecked then the continuation ends`() = runTest {
        val cases: List<(JoinHarness) -> Unit> = listOf(
            { it.account.default = BindingResolution.Unavailable },
            { it.account.default = BindingResolution.Restricted },
            { it.cloud.scriptZoneFetch(ZoneFetchResult.AccountChanged) },
            { it.cloud.scriptAnchorRead(AnchorReadResult.AccountChanged) },
            { it.cloud.scriptAnchorRead(AnchorReadResult.IntegrityFailure) },
            { it.keys.scriptRead(KeyItemReadResult.AccountChanged) },
            { it.keys.scriptRead(KeyItemReadResult.IntegrityFailure) },
            { it.deliverKey(testContext.copy(workspaceId = WorkspaceId(testIdentifier(99)))) },
        )
        for (configure in cases) {
            val harness = JoinHarness()
            harness.waitForKey()
            configure(harness)
            assertEquals(JoinCheckResult.ACTION_REQUIRED, harness.coordinator.joins.recheck())
            assertFalse(harness.coordinator.joins.hasPending())
            harness.assertNoWrites()
        }
    }

    @Test
    fun `given a store failure when checking or adopting then no successful join is reported`() = runTest {
        for (failure in BootstrapStoreFailure.entries) {
            for (onCommit in listOf(false, true)) {
                val harness = JoinHarness()
                harness.waitForKey()
                if (onCommit) {
                    harness.deliverKey()
                    harness.store.writeFailure = failure
                } else {
                    harness.store.readFailure = failure
                }
                assertEquals(
                    if (failure == BootstrapStoreFailure.CORRUPTION) JoinCheckResult.ACTION_REQUIRED else JoinCheckResult.RETRYABLE,
                    harness.coordinator.joins.recheck(),
                )
                assertEquals(failure == BootstrapStoreFailure.STORAGE_FAILURE, harness.coordinator.joins.hasPending())
                assertIs<BootstrapState.None>(harness.store.state)
            }
        }
    }

    @Test
    fun `given existing local state when rechecked then no join provider is called`() = runTest {
        val context = testContext
        val states = listOf(
            BootstrapState.Candidate(PersistedCandidate(context.workspaceId, context.transportEpochId, context.keyEpochId, bindingA)),
            BootstrapState.Established(EstablishedWorkspace(context, bindingA)),
            BootstrapState.Established(EstablishedWorkspace(context, bindingB)),
        )
        for (state in states) {
            val harness = JoinHarness()
            harness.waitForKey()
            val calls = harness.account.calls
            harness.store.state = state
            val expected = when (state) {
                is BootstrapState.Candidate -> JoinCheckResult.STATE_CHANGED
                is BootstrapState.Established -> if (state.workspace.binding == bindingA) JoinCheckResult.READY else JoinCheckResult.ACTION_REQUIRED
                BootstrapState.None -> error("A persisted state is required")
            }
            assertEquals(expected, harness.coordinator.joins.recheck())
            assertEquals(calls, harness.account.calls)
            assertEquals(state, harness.store.state)
            assertFalse(harness.coordinator.joins.hasPending())
            harness.assertNoWrites()
        }
    }

    @Test
    fun `given a cancelled check when retried then the same continuation remains available`() = runTest {
        val harness = JoinHarness()
        harness.waitForKey()
        harness.cloud.beforeZoneFetch = { throw CancellationException() }
        var cancelled = false
        try {
            harness.coordinator.joins.recheck()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(harness.coordinator.joins.hasPending())
        harness.cloud.beforeZoneFetch = {}
        harness.deliverKey()
        assertEquals(JoinCheckResult.READY, harness.coordinator.joins.recheck())
    }
}

private class JoinHarness {
    val account = FakeBootstrapAccountPort()
    val cloud = FakeBootstrapCloudPort(zoneExists = true, storedAnchor = anchor)
    val keys = FakeBootstrapKeyPort()
    val store = FakeBootstrapStore()
    val coordinator = BootstrapCoordinator(account, cloud, keys, store, FakeSyncCryptoProvider())

    suspend fun waitForKey() {
        assertEquals(BootstrapResult.WaitingForWorkspaceKey, coordinator.bootstrap())
    }

    fun deliverKey(context: SyncContext = testContext) {
        keys.items[BootstrapEncoding.identifierToAccountText(anchor.workspaceId.value)] = checkNotNull(
            BootstrapEncoding.encodeKeyItem(context.workspaceId, context.transportEpochId, context.keyEpochId, ByteArray(32) { 7 }),
        ).copyBytes()
    }

    fun assertNoWrites() {
        assertEquals(0, cloud.zoneSaveCalls)
        assertEquals(0, cloud.anchorCreateCalls)
        assertEquals(0, keys.createCalls)
        assertEquals(0, keys.deleteCalls)
        assertEquals(0, store.persistCalls)
        assertEquals(0, store.commitCalls)
    }

    companion object {
        val anchor = WorkspaceAnchor(testContext.workspaceId, testContext.transportEpochId, testContext.keyEpochId)
    }
}
