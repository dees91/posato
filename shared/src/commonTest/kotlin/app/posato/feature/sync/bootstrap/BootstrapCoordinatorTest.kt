package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.SyncSigningKey
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class SequencedCrypto(
    private var next: Int
) : SyncCryptoProvider {
    override fun randomBytes(count: Int): ByteArray {
        return ByteArray(count) { next++.toByte() }
    }

    override fun sha256(message: ByteArray): ByteArray? {
        error("unused in bootstrap tests")
    }

    override fun hmacSha256(
        key: ByteArray,
        message: ByteArray
    ): ByteArray? {
        error("unused in bootstrap tests")
    }

    override fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray
    ): ByteArray? {
        error("unused in bootstrap tests")
    }

    override fun openAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        ciphertextAndTag: ByteArray
    ): ByteArray? {
        error("unused in bootstrap tests")
    }

    override fun createSigningKey(): SyncSigningKey? {
        error("unused in bootstrap tests")
    }

    override fun verifyEd25519(
        publicKey: PublicSigningKey,
        message: ByteArray,
        signature: ByteArray
    ): Boolean {
        error("unused in bootstrap tests")
    }
}

private class FailingRandomCrypto : SyncCryptoProvider {
    override fun randomBytes(count: Int): ByteArray? {
        return null
    }

    override fun sha256(message: ByteArray): ByteArray? {
        return null
    }

    override fun hmacSha256(
        key: ByteArray,
        message: ByteArray
    ): ByteArray? {
        return null
    }

    override fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray
    ): ByteArray? {
        return null
    }

    override fun openAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        ciphertextAndTag: ByteArray
    ): ByteArray? {
        return null
    }

    override fun createSigningKey(): SyncSigningKey? {
        return null
    }

    override fun verifyEd25519(
        publicKey: PublicSigningKey,
        message: ByteArray,
        signature: ByteArray
    ): Boolean {
        return false
    }
}

private class BootstrapHarness(
    val account: FakeBootstrapAccountPort = FakeBootstrapAccountPort(),
    val cloud: FakeBootstrapCloudPort = FakeBootstrapCloudPort(),
    val keys: FakeBootstrapKeyPort = FakeBootstrapKeyPort(),
    val store: FakeBootstrapStore = FakeBootstrapStore(),
    crypto: SyncCryptoProvider = app.posato.feature.sync.FakeSyncCryptoProvider()
) {
    val coordinator = BootstrapCoordinator(account, cloud, keys, store, crypto)
}

private fun knownAnchor(
    workspace: Int,
    transport: Int,
    keyEpoch: Int
): WorkspaceAnchor {
    return WorkspaceAnchor(
        WorkspaceId(testIdentifier(workspace)),
        TransportEpochId(testIdentifier(transport)),
        KeyEpochId(testIdentifier(keyEpoch)),
    )
}

private fun accountTextOf(anchor: WorkspaceAnchor): String {
    return checkNotNull(BootstrapEncoding.identifierToAccountText(anchor.workspaceId.value))
}

private fun seedValidItem(
    keys: FakeBootstrapKeyPort,
    anchor: WorkspaceAnchor,
    keyByte: Byte = 7
) {
    val encoded = checkNotNull(
        BootstrapEncoding.encodeKeyItem(
            anchor.workspaceId,
            anchor.transportEpochId,
            anchor.keyEpochId,
            ByteArray(WORKSPACE_KEY_BYTES) { keyByte },
        ),
    )
    keys.items[accountTextOf(anchor)] = encoded.copyBytes()
}

private fun candidateOf(
    workspace: Int,
    transport: Int,
    keyEpoch: Int
): PersistedCandidate {
    return PersistedCandidate(
        WorkspaceId(testIdentifier(workspace)),
        TransportEpochId(testIdentifier(transport)),
        KeyEpochId(testIdentifier(keyEpoch)),
        bindingA,
    )
}

private fun establishedOf(anchor: WorkspaceAnchor): BootstrapState.Established {
    return BootstrapState.Established(
        EstablishedWorkspace(SyncContext(anchor.workspaceId, anchor.transportEpochId, anchor.keyEpochId), bindingA),
    )
}

class BootstrapCoordinatorTest {
    @Test
    fun `given a fresh database without the zone when bootstrapping then zone candidate and anchor are created`() = runTest {
        val harness = BootstrapHarness()

        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertEquals(1, harness.cloud.zoneSaveCalls)
        assertEquals(1, harness.cloud.anchorCreateCalls)
        val established = assertIs<BootstrapState.Established>(harness.store.state)
        assertEquals(ready.context, established.workspace.context)
        assertEquals(bindingA, established.workspace.binding)
    }

    @Test
    fun `given an existing zone when bootstrapping then the zone is never saved`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true

        val result = harness.coordinator.bootstrap()

        assertIs<BootstrapResult.Ready>(result)
        assertEquals(0, harness.cloud.zoneSaveCalls)
    }

    @Test
    fun `given concurrent zone creation when bootstrapping then the shared zone is adopted`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.scriptZoneFetch(ZoneFetchResult.Missing, ZoneFetchResult.Found)
        harness.cloud.scriptZoneSave(ZoneSaveResult.AlreadyExists)

        val result = harness.coordinator.bootstrap()

        assertIs<BootstrapResult.Ready>(result)
        assertEquals(1, harness.cloud.zoneSaveCalls)
        assertEquals(2, harness.cloud.zoneFetchCalls)
        assertEquals(1, harness.cloud.anchorCreateCalls)
    }

    @Test
    fun `given a crash after zone save when restarting then the saved zone is reused without another save`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true

        val result = harness.coordinator.bootstrap()

        assertIs<BootstrapResult.Ready>(result)
        assertEquals(0, harness.cloud.zoneSaveCalls)
        assertEquals(1, harness.cloud.anchorCreateCalls)
    }

    @Test
    fun `given a zone-save timeout when the zone fetch confirms then bootstrap proceeds`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.scriptZoneFetch(ZoneFetchResult.Missing, ZoneFetchResult.Found)
        harness.cloud.scriptZoneSave(ZoneSaveResult.UnknownOutcome)

        val result = harness.coordinator.bootstrap()

        assertIs<BootstrapResult.Ready>(result)
    }

    @Test
    fun `given a zone-save timeout without confirmation when bootstrapping then retry follows without anchor access`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.scriptZoneFetch(ZoneFetchResult.Missing, ZoneFetchResult.Missing)
        harness.cloud.scriptZoneSave(ZoneSaveResult.UnknownOutcome)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
        assertEquals(0, harness.cloud.anchorReadCalls)
        assertEquals(0, harness.cloud.anchorCreateCalls)
    }

    @Test
    fun `given an unconfirmed duplicate zone save when bootstrapping then retry follows without anchor access`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.scriptZoneFetch(ZoneFetchResult.Missing, ZoneFetchResult.Missing)
        harness.cloud.scriptZoneSave(ZoneSaveResult.AlreadyExists)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
        assertEquals(0, harness.cloud.anchorReadCalls)
        assertEquals(0, harness.cloud.anchorCreateCalls)
        assertEquals(0, harness.keys.createCalls)
        assertIs<BootstrapState.None>(harness.store.state)
    }

    @Test
    fun `given zone absence after establishment when bootstrapping then action is required without recreation`() = runTest {
        val harness = BootstrapHarness()
        harness.store.state = establishedOf(knownAnchor(1, 2, 3))
        harness.cloud.zoneExists = false

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.cloud.zoneSaveCalls)
        assertEquals(0, harness.cloud.anchorReadCalls)
        assertEquals(0, harness.keys.readCalls)
        assertIs<BootstrapState.Established>(harness.store.state)
    }

    @Test
    fun `given an established workspace with the zone present when bootstrapping then ready is returned`() = runTest {
        val harness = BootstrapHarness()
        val anchor = knownAnchor(1, 2, 3)
        harness.store.state = establishedOf(anchor)
        harness.cloud.zoneExists = true
        harness.cloud.storedAnchor = anchor

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Ready(SyncContext(anchor.workspaceId, anchor.transportEpochId, anchor.keyEpochId)), result)
        assertEquals(1, harness.cloud.anchorReadCalls)
        assertEquals(0, harness.keys.readCalls)
    }

    @Test
    fun `given a missing anchor after establishment when bootstrapping then action is required`() = runTest {
        val harness = BootstrapHarness()
        harness.store.state = establishedOf(knownAnchor(1, 2, 3))
        harness.cloud.zoneExists = true
        harness.cloud.storedAnchor = null

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertIs<BootstrapState.Established>(harness.store.state)
    }

    @Test
    fun `given a different anchor after establishment when bootstrapping then action is required`() = runTest {
        val harness = BootstrapHarness()
        harness.store.state = establishedOf(knownAnchor(1, 2, 3))
        harness.cloud.zoneExists = true
        harness.cloud.storedAnchor = knownAnchor(4, 5, 6)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertIs<BootstrapState.Established>(harness.store.state)
        assertEquals(0, harness.keys.createCalls)
    }

    @Test
    fun `given an anchor timeout when bootstrapping then retry follows without generating a candidate`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.cloud.scriptAnchorRead(AnchorReadResult.UnknownOutcome)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
        assertEquals(0, harness.keys.createCalls)
        assertIs<BootstrapState.None>(harness.store.state)
    }

    @Test
    fun `given an existing anchor with a missing item when bootstrapping then waiting follows without a candidate`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.cloud.storedAnchor = knownAnchor(11, 12, 13)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.WaitingForWorkspaceKey, result)
        assertEquals(0, harness.keys.createCalls)
        assertIs<BootstrapState.None>(harness.store.state)
    }

    @Test
    fun `given delayed key delivery when bootstrapping again then the winner is adopted`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        val winner = knownAnchor(11, 12, 13)
        harness.cloud.storedAnchor = winner

        assertEquals(BootstrapResult.WaitingForWorkspaceKey, harness.coordinator.bootstrap())
        seedValidItem(harness.keys, winner)

        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertEquals(winner.workspaceId, ready.context.workspaceId)
        assertEquals(0, harness.cloud.anchorCreateCalls)
    }

    @Test
    fun `given an indeterminate anchor create that won when resuming then the same candidate is confirmed`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        val ours = knownAnchor(31, 32, 33)
        harness.store.state = BootstrapState.Candidate(candidateOf(31, 32, 33))
        seedValidItem(harness.keys, ours)
        harness.cloud.storedAnchor = ours
        harness.cloud.scriptAnchorCreate(AnchorCreateResult.Conflict)

        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertEquals(ours.workspaceId, ready.context.workspaceId)
        assertEquals(0, harness.keys.deleteCalls)
    }

    @Test
    fun `given a lost anchor race when resuming then only the losing candidate item is cleaned up`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        val winner = knownAnchor(41, 42, 43)
        harness.store.state = BootstrapState.Candidate(candidateOf(31, 32, 33))
        seedValidItem(harness.keys, knownAnchor(31, 32, 33))
        seedValidItem(harness.keys, winner, keyByte = 9)
        harness.cloud.storedAnchor = winner
        harness.cloud.scriptAnchorCreate(AnchorCreateResult.Conflict)

        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertEquals(winner.workspaceId, ready.context.workspaceId)
        assertEquals(1, harness.keys.deleteCalls)
        assertTrue(!harness.keys.items.containsKey(accountTextOf(knownAnchor(31, 32, 33))))
        assertTrue(harness.keys.items.containsKey(accountTextOf(winner)))
        val established = assertIs<BootstrapState.Established>(harness.store.state)
        assertEquals(winner.workspaceId, established.workspace.context.workspaceId)
    }

    @Test
    fun `given two coordinators over one shared provider when joining sequentially then both converge on one anchor`() = runTest {
        val cloud = FakeBootstrapCloudPort()
        val keys = FakeBootstrapKeyPort()
        val coordinatorA = sharedCoordinator(cloud, keys, FakeBootstrapStore(), 1)
        val storeB = FakeBootstrapStore()
        val coordinatorB = sharedCoordinator(cloud, keys, storeB, 101)

        val readyA = assertIs<BootstrapResult.Ready>(coordinatorA.bootstrap())
        val readyB = assertIs<BootstrapResult.Ready>(coordinatorB.bootstrap())

        assertEquals(readyA.context, readyB.context)
        assertEquals(1, cloud.anchorCreateCalls)
        assertEquals(1, keys.createCalls)
        assertEquals(readyA.context, assertIs<BootstrapState.Established>(storeB.state).workspace.context)
        val accountA = checkNotNull(BootstrapEncoding.identifierToAccountText(readyA.context.workspaceId.value))
        assertTrue(keys.items.containsKey(accountA))
    }

    @Test
    fun `given an interleaved anchor race when converging then the loser cleans up exactly its candidate`() = runTest {
        val cloud = FakeBootstrapCloudPort()
        val keys = FakeBootstrapKeyPort()
        val storeB = FakeBootstrapStore()
        val coordinatorB = sharedCoordinator(cloud, keys, storeB, 1)
        cloud.scriptAnchorCreate(AnchorCreateResult.UnknownOutcome)

        assertEquals(BootstrapResult.Retryable, coordinatorB.bootstrap())
        val candidateB = assertIs<BootstrapState.Candidate>(storeB.state).candidate

        val coordinatorA = sharedCoordinator(cloud, keys, FakeBootstrapStore(), 101)
        val readyA = assertIs<BootstrapResult.Ready>(coordinatorA.bootstrap())

        val readyB = assertIs<BootstrapResult.Ready>(coordinatorB.bootstrap())

        assertEquals(readyA.context, readyB.context)
        assertTrue(candidateB.workspaceId != readyA.context.workspaceId)
        val accountB = checkNotNull(BootstrapEncoding.identifierToAccountText(candidateB.workspaceId.value))
        assertTrue(!keys.items.containsKey(accountB))
        val accountA = checkNotNull(BootstrapEncoding.identifierToAccountText(readyA.context.workspaceId.value))
        assertTrue(keys.items.containsKey(accountA))
        assertEquals(readyA.context.workspaceId, cloud.storedAnchor?.workspaceId)
    }

    @Test
    fun `given an indeterminate anchor create when resuming then the same candidate and bytes are retried`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.cloud.scriptAnchorCreate(AnchorCreateResult.UnknownOutcome)

        assertEquals(BootstrapResult.Retryable, harness.coordinator.bootstrap())
        val candidate = assertIs<BootstrapState.Candidate>(harness.store.state).candidate

        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertEquals(candidate.workspaceId, ready.context.workspaceId)
        assertEquals(2, harness.cloud.anchorCreateCalls)
        assertEquals(candidate.workspaceId, harness.cloud.createdAnchors[0].workspaceId)
        assertEquals(candidate.workspaceId, harness.cloud.createdAnchors[1].workspaceId)
    }

    @Test
    fun `given a corrupt item checksum when bootstrapping then action is required without commitment`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        val winner = knownAnchor(51, 52, 53)
        harness.cloud.storedAnchor = winner
        seedValidItem(harness.keys, winner)
        val account = accountTextOf(winner)
        val tampered = checkNotNull(harness.keys.items[account])
        tampered[20] = (tampered[20] + 1).toByte()

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.store.commitCalls)
    }

    @Test
    fun `given a short item when bootstrapping then the boundary integrity failure requires action`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        val winner = knownAnchor(51, 52, 53)
        harness.cloud.storedAnchor = winner
        harness.keys.items[accountTextOf(winner)] = ByteArray(80)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.store.commitCalls)
    }

    @Test
    fun `given an item with mismatched context when bootstrapping then action is required`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        val anchor = knownAnchor(51, 52, 53)
        harness.cloud.storedAnchor = anchor
        val foreign = checkNotNull(
            BootstrapEncoding.encodeKeyItem(
                WorkspaceId(testIdentifier(61)),
                TransportEpochId(testIdentifier(62)),
                KeyEpochId(testIdentifier(63)),
                ByteArray(WORKSPACE_KEY_BYTES) { 7 },
            ),
        )
        harness.keys.items[accountTextOf(anchor)] = foreign.copyBytes()

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.store.commitCalls)
    }

    @Test
    fun `given a persisted candidate with an unavailable item when resuming then action follows without replacement`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.store.state = BootstrapState.Candidate(candidateOf(71, 72, 73))

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.cloud.anchorCreateCalls)
    }

    @Test
    fun `given a persisted candidate with a replaced item when resuming then action is required`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.store.state = BootstrapState.Candidate(candidateOf(71, 72, 73))
        seedValidItem(harness.keys, knownAnchor(71, 72, 73), keyByte = 5)
        val account = accountTextOf(knownAnchor(71, 72, 73))
        val replaced = checkNotNull(harness.keys.items[account])
        replaced[70] = (replaced[70] + 1).toByte()

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.cloud.anchorCreateCalls)
    }

    @Test
    fun `given a crash after candidate persistence when resuming then the same candidate completes`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.store.state = BootstrapState.Candidate(candidateOf(81, 82, 83))
        seedValidItem(harness.keys, knownAnchor(81, 82, 83))

        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertEquals(testIdentifier(81), ready.context.workspaceId.value)
        assertEquals(1, harness.cloud.anchorCreateCalls)
        assertEquals(testIdentifier(81), harness.cloud.createdAnchors.single().workspaceId.value)
    }

    @Test
    fun `given an inert unanchored item without persisted state when restarting then a fresh candidate is used`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        seedValidItem(harness.keys, knownAnchor(91, 92, 93))

        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertTrue(ready.context.workspaceId.value != testIdentifier(91))
        assertTrue(harness.keys.items.containsKey(accountTextOf(knownAnchor(91, 92, 93))))
    }

    @Test
    fun `given an account change against a persisted binding when bootstrapping then no provider access happens`() = runTest {
        val harness = BootstrapHarness()
        harness.store.state = BootstrapState.Candidate(candidateOf(1, 2, 3))
        harness.account.default = BindingResolution.Available(bindingB)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.cloud.zoneFetchCalls)
        assertEquals(0, harness.cloud.anchorReadCalls)
        assertEquals(0, harness.keys.readCalls)
        assertEquals(0, harness.keys.createCalls)
    }

    @Test
    fun `given an account change mid-flight when bootstrapping then creation stops without commitment`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.keys.scriptCreate(KeyItemCreateResult.AccountChanged)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertIs<BootstrapState.None>(harness.store.state)
        assertEquals(0, harness.cloud.anchorCreateCalls)
        assertEquals(0, harness.store.commitCalls)
    }

    @Test
    fun `given a postflight switch after anchor creation when confirming then action follows without commitment`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.cloud.scriptAnchorCreate(AnchorCreateResult.Created)
        harness.cloud.scriptAnchorRead(AnchorReadResult.Missing, AnchorReadResult.AccountChanged)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(1, harness.cloud.anchorCreateCalls)
        assertEquals(0, harness.store.commitCalls)
        assertIs<BootstrapState.Candidate>(harness.store.state)
    }

    @Test
    fun `given a return to the original account when bootstrapping then the same candidate resumes`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.store.state = BootstrapState.Candidate(candidateOf(101, 102, 103))
        seedValidItem(harness.keys, knownAnchor(101, 102, 103))
        harness.account.scriptResolution(
            BindingResolution.Available(bindingB),
            BindingResolution.Available(bindingA),
        )

        assertEquals(BootstrapResult.ActionRequired, harness.coordinator.bootstrap())
        val result = harness.coordinator.bootstrap()

        val ready = assertIs<BootstrapResult.Ready>(result)
        assertEquals(testIdentifier(101), ready.context.workspaceId.value)
        assertEquals(bindingA, assertIs<BootstrapState.Established>(harness.store.state).workspace.binding)
    }

    @Test
    fun `given random failure when bootstrapping then retry follows without key operations`() = runTest {
        val harness = BootstrapHarness(crypto = FailingRandomCrypto())
        harness.cloud.zoneExists = true

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
        assertEquals(0, harness.keys.createCalls)
        assertIs<BootstrapState.None>(harness.store.state)
    }

    @Test
    fun `given corrupt persisted state when bootstrapping then action is required`() = runTest {
        val harness = BootstrapHarness()
        harness.store.readFailure = BootstrapStoreFailure.CORRUPTION

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.ActionRequired, result)
        assertEquals(0, harness.cloud.zoneFetchCalls)
    }

    @Test
    fun `given a storage failure on persist when bootstrapping then retry follows`() = runTest {
        val harness = BootstrapHarness()
        harness.cloud.zoneExists = true
        harness.store.writeFailure = BootstrapStoreFailure.STORAGE_FAILURE

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
    }

    @Test
    fun `given an established workspace when reading the key then the created bytes are returned`() = runTest {
        val harness = BootstrapHarness()

        val ready = assertIs<BootstrapResult.Ready>(harness.coordinator.bootstrap())
        val keyRead = assertIs<WorkspaceKeyRead.Ready>(harness.coordinator.readWorkspaceKey())

        assertEquals(ready.context, keyRead.context)
        val stored = checkNotNull(harness.keys.items[accountTextOf(knownAnchorOf(ready.context))])
        assertTrue(stored.copyOfRange(48, 80).contentEquals(keyRead.key.copyBytes()))
        assertEquals(ready.context, harness.coordinator.establishedContext())
    }

    @Test
    fun `given an account change when reading the key then action is required without exposure`() = runTest {
        val harness = BootstrapHarness()

        val ready = assertIs<BootstrapResult.Ready>(harness.coordinator.bootstrap())
        harness.account.default = BindingResolution.Available(bindingB)

        assertEquals(WorkspaceKeyRead.ActionRequired, harness.coordinator.readWorkspaceKey())
        assertEquals(ready.context, harness.coordinator.establishedContext())
    }

    @Test
    fun `given a missing item after establishment when reading the key then waiting follows`() = runTest {
        val harness = BootstrapHarness()

        val ready = assertIs<BootstrapResult.Ready>(harness.coordinator.bootstrap())
        harness.keys.items.remove(accountTextOf(knownAnchorOf(ready.context)))

        assertEquals(WorkspaceKeyRead.WaitingForWorkspaceKey, harness.coordinator.readWorkspaceKey())
    }

    @Test
    fun `given no established workspace when reading then action follows`() = runTest {
        val harness = BootstrapHarness()

        assertEquals(WorkspaceKeyRead.ActionRequired, harness.coordinator.readWorkspaceKey())
        assertNull(harness.coordinator.establishedContext())
    }

    @Test
    fun `given a removed workspace when the zone is missing then the resurrected anchor is refused after one zone save`() = runTest {
        for (itemPresent in listOf(false, true)) {
            val harness = BootstrapHarness()
            val removed = knownAnchor(11, 12, 13)
            harness.store.recordRemoved(removed.workspaceId)
            harness.cloud.storedAnchor = removed
            if (itemPresent) {
                seedValidItem(harness.keys, removed)
            }

            val result = harness.coordinator.bootstrap()

            assertEquals(BootstrapResult.Retryable, result)
            assertEquals(1, harness.cloud.zoneSaveCalls)
            assertEquals(0, harness.store.persistCalls)
            assertEquals(0, harness.cloud.anchorCreateCalls)
            assertEquals(0, harness.keys.createCalls)
            assertEquals(0, harness.keys.deleteCalls)
            assertEquals(0, harness.store.commitCalls)
            assertIs<BootstrapState.None>(harness.store.state)
        }
    }

    @Test
    fun `given a removed workspace when the zone is found then the resurrected anchor is refused without a zone save`() = runTest {
        val harness = BootstrapHarness()
        val removed = knownAnchor(11, 12, 13)
        harness.cloud.zoneExists = true
        harness.store.recordRemoved(removed.workspaceId)
        harness.cloud.storedAnchor = removed
        seedValidItem(harness.keys, removed)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
        assertEquals(0, harness.cloud.zoneSaveCalls)
        assertEquals(0, harness.store.persistCalls)
        assertEquals(0, harness.cloud.anchorCreateCalls)
        assertEquals(0, harness.keys.createCalls)
        assertEquals(0, harness.keys.deleteCalls)
        assertIs<BootstrapState.None>(harness.store.state)
    }

    @Test
    fun `given a refused resurrection when the anchor is later missing then a fresh workspace is established`() = runTest {
        val harness = BootstrapHarness()
        val removed = knownAnchor(11, 12, 13)
        harness.cloud.zoneExists = true
        harness.store.recordRemoved(removed.workspaceId)
        harness.cloud.storedAnchor = removed

        assertEquals(BootstrapResult.Retryable, harness.coordinator.bootstrap())
        harness.cloud.storedAnchor = null

        val ready = assertIs<BootstrapResult.Ready>(harness.coordinator.bootstrap())
        assertTrue(ready.context.workspaceId != removed.workspaceId)
        assertEquals(0, harness.cloud.zoneSaveCalls)
        val established = assertIs<BootstrapState.Established>(harness.store.state)
        assertEquals(ready.context, established.workspace.context)
    }

    @Test
    fun `given a refused resurrection when a new anchor is found then that workspace is joined`() = runTest {
        val harness = BootstrapHarness()
        val removed = knownAnchor(11, 12, 13)
        val winner = knownAnchor(21, 22, 23)
        harness.cloud.zoneExists = true
        harness.store.recordRemoved(removed.workspaceId)
        harness.cloud.storedAnchor = removed

        assertEquals(BootstrapResult.Retryable, harness.coordinator.bootstrap())
        harness.cloud.storedAnchor = winner
        seedValidItem(harness.keys, winner)

        val ready = assertIs<BootstrapResult.Ready>(harness.coordinator.bootstrap())
        assertEquals(winner.workspaceId, ready.context.workspaceId)
        assertEquals(0, harness.cloud.anchorCreateCalls)
        assertEquals(0, harness.keys.deleteCalls)
    }

    @Test
    fun `given a persisted candidate when the winner is tombstoned then cleanup is skipped`() = runTest {
        val harness = BootstrapHarness()
        val own = knownAnchor(31, 32, 33)
        val winner = knownAnchor(41, 42, 43)
        harness.cloud.zoneExists = true
        harness.store.state = BootstrapState.Candidate(candidateOf(31, 32, 33))
        seedValidItem(harness.keys, own)
        seedValidItem(harness.keys, winner, keyByte = 9)
        harness.cloud.storedAnchor = winner
        harness.store.recordRemoved(winner.workspaceId)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
        assertEquals(0, harness.keys.deleteCalls)
        assertTrue(harness.keys.items.containsKey(accountTextOf(own)))
        val candidate = assertIs<BootstrapState.Candidate>(harness.store.state).candidate
        assertEquals(own.workspaceId, candidate.workspaceId)
    }

    @Test
    fun `given a mint that conflicts with a tombstoned anchor then the candidate and item stay`() = runTest {
        val harness = BootstrapHarness()
        val resurrected = knownAnchor(41, 42, 43)
        harness.cloud.zoneExists = true
        harness.cloud.storedAnchor = resurrected
        harness.store.recordRemoved(resurrected.workspaceId)
        harness.cloud.scriptAnchorRead(AnchorReadResult.Missing)
        harness.cloud.scriptAnchorCreate(AnchorCreateResult.Conflict)

        val result = harness.coordinator.bootstrap()

        assertEquals(BootstrapResult.Retryable, result)
        val candidate = assertIs<BootstrapState.Candidate>(harness.store.state).candidate
        assertEquals(1, harness.store.persistCalls)
        assertEquals(0, harness.keys.deleteCalls)
        assertEquals(0, harness.store.commitCalls)
        val account = checkNotNull(BootstrapEncoding.identifierToAccountText(candidate.workspaceId.value))
        assertTrue(harness.keys.items.containsKey(account))
        assertTrue(candidate.workspaceId != resurrected.workspaceId)
    }

    @Test
    fun `given a tombstone lookup failure when bootstrapping then the attempt fails closed`() = runTest {
        val removed = knownAnchor(11, 12, 13)
        for (failure in BootstrapStoreFailure.entries) {
            val harness = BootstrapHarness()
            harness.cloud.zoneExists = true
            harness.cloud.storedAnchor = removed
            seedValidItem(harness.keys, removed)
            harness.store.containsFailure = failure

            val result = harness.coordinator.bootstrap()

            assertEquals(mapStoreFailure(failure), result)
            assertEquals(0, harness.store.persistCalls)
            assertEquals(0, harness.store.commitCalls)
            assertIs<BootstrapState.None>(harness.store.state)
        }
    }

    private fun knownAnchorOf(context: SyncContext): WorkspaceAnchor {
        return WorkspaceAnchor(context.workspaceId, context.transportEpochId, context.keyEpochId)
    }

    private fun sharedCoordinator(
        cloud: FakeBootstrapCloudPort,
        keys: FakeBootstrapKeyPort,
        store: FakeBootstrapStore,
        firstRandomByte: Int
    ): BootstrapCoordinator {
        return BootstrapCoordinator(FakeBootstrapAccountPort(), cloud, keys, store, SequencedCrypto(firstRandomByte))
    }
}
