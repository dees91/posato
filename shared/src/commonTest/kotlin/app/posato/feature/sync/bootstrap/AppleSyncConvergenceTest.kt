package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.PrepareBundleResult
import app.posato.feature.sync.data.SyncOperationCodec
import app.posato.feature.sync.data.useAndClear
import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncReducer
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.domain.TransportKey
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.RecordDeleteResult
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testPublicKey
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ApplicationPolicyNameResult
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleSyncConvergenceTest {
    @Test
    fun `given two linked devices when each saves in turn then both converge to equal policies`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "sync-converge-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "sync-converge-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 200 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()

            first.recordDomainChanges(testPolicy(), testPolicy("first.example"))
            advanceUntilIdle()
            second.sync.syncNow()
            advanceUntilIdle()
            assertEquals(listOf("first.example"), localDomains(second))

            second.recordDomainChanges(testPolicy("first.example"), testPolicy("first.example", "second.example"))
            advanceUntilIdle()
            first.sync.syncNow()
            advanceUntilIdle()

            assertEquals(localPolicy(first), localPolicy(second))
            assertEquals(localBase(first), localBase(second))
            assertEquals(SyncStatus.COMPLETED, first.sync.state.value.status)
            assertEquals(SyncStatus.COMPLETED, second.sync.state.value.status)

            val accepted = first.snapshot().acceptedBundles.size + second.snapshot().acceptedBundles.size
            first.sync.syncNow()
            second.sync.syncNow()
            advanceUntilIdle()
            assertEquals(accepted, first.snapshot().acceptedBundles.size + second.snapshot().acceptedBundles.size)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given concurrent saves when rounds interleave then both converge to the union`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "sync-union-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "sync-union-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 300 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()

            first.recordDomainChanges(testPolicy(), testPolicy("alpha.example"))
            second.recordDomainChanges(testPolicy(), testPolicy("beta.example"))
            advanceUntilIdle()
            first.sync.syncNow()
            second.sync.syncNow()
            advanceUntilIdle()
            first.sync.syncNow()
            second.sync.syncNow()
            advanceUntilIdle()

            assertEquals(4, mailbox.saved.size)
            assertEquals(SyncStatus.COMPLETED, first.sync.state.value.status)
            assertEquals(SyncStatus.COMPLETED, second.sync.state.value.status)
            assertEquals(listOf("alpha.example", "beta.example"), localDomains(first))
            assertEquals(localPolicy(first), localPolicy(second))
            assertEquals(localBase(first), localBase(second))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a group created on one device when the peer syncs then the name converges on both`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "sync-group-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "sync-group-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 200 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()

            first.recordDomainChanges(testPolicy(), testNamedPolicy("Family"))
            advanceUntilIdle()
            second.sync.syncNow()
            advanceUntilIdle()

            assertEquals("Family", localPolicy(first).applicationPolicyName?.canonicalValue)
            assertEquals("Family", localPolicy(second).applicationPolicyName?.canonicalValue)
            assertEquals(localPolicy(first), localPolicy(second))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a fresh replica with a local default when joining a custom workspace then both keep the custom name`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "sync-custom-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "sync-custom-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 200 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            first.sync.onForeground()
            advanceUntilIdle()
            first.recordDomainChanges(testPolicy(), testNamedPolicy("Family"))
            advanceUntilIdle()

            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(second.syncPolicy.replace(0, testNamedPolicy("Applications")))
            second.establish()
            second.sync.onForeground()
            advanceUntilIdle()

            assertEquals("Family", localPolicy(first).applicationPolicyName?.canonicalValue)
            assertEquals("Family", localPolicy(second).applicationPolicyName?.canonicalValue)
            assertEquals(0, intentRowCount(second))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a local rename while the projection holds a name when deciding then the rename stays local per D4`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "sync-rename-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "sync-rename-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 200 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()
            first.recordDomainChanges(testPolicy(), testNamedPolicy("Family"))
            advanceUntilIdle()
            second.sync.syncNow()
            advanceUntilIdle()
            assertEquals("Family", localPolicy(second).applicationPolicyName?.canonicalValue)

            val published = mailbox.saved.size
            first.recordDomainChanges(testNamedPolicy("Family"), testNamedPolicy("Renamed"))
            advanceUntilIdle()
            second.sync.syncNow()
            advanceUntilIdle()

            assertEquals("Renamed", localPolicy(first).applicationPolicyName?.canonicalValue)
            assertEquals("Family", localPolicy(second).applicationPolicyName?.canonicalValue)
            assertEquals(published, mailbox.saved.size)
            assertEquals(0, intentRowCount(first))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a pre-link domain when linking then the first pass publishes it to the peer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "sync-prelink-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "sync-prelink-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 200 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            first.sync.onForeground()
            advanceUntilIdle()
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(second.syncPolicy.replace(0, testPolicy("prelink.example")))

            second.establish()
            second.sync.onForeground()
            advanceUntilIdle()
            assertTrue(mailbox.saved.isNotEmpty())

            first.sync.syncNow()
            advanceUntilIdle()
            assertEquals(listOf("prelink.example"), localDomains(first))
            assertEquals(localPolicy(first), localPolicy(second))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given an active session when a website arrives mid-session then the frozen start set is unchanged`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "sync-session-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "sync-session-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 200 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()

            val sessions = SqlLocalSessionStore(second.database, dispatcher)
            val frozen = FrozenStartSet(persistentListOf("frozen.example"), null)
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
                sessions.start(SessionId(testIdentifier(41)), NOW, NOW + 30 * 60_000L, NOW, frozen),
            )
            val frozenBefore = frozenDomains(second)

            first.recordDomainChanges(testPolicy(), testPolicy("arrived.example"))
            advanceUntilIdle()
            second.sync.syncNow()
            advanceUntilIdle()

            assertEquals(listOf("arrived.example"), localDomains(second))
            assertEquals(frozenBefore, frozenDomains(second))
            val status = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(sessions.read(NOW)).value
            assertIs<LocalSessionStatus.Active>(status)
            assertEquals(SessionId(testIdentifier(41)), status.record.sessionId)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a local-cap refusal when the next pass halts on a missing zone then the reason is cleared`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = AppleSyncTestHarness(dispatcher, "sync-reason-clear.db")
        try {
            harness.establish()
            fabricateFullProjection(harness)
            harness.sync.onForeground()
            advanceUntilIdle()
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
            assertEquals(SyncAttentionReason.LOCAL_CAPACITY, harness.sync.state.value.reason)

            harness.cloud.zoneExists = false
            harness.sync.syncNow()
            advanceUntilIdle()
            assertEquals(SyncStatus.ACTION_REQUIRED, harness.sync.state.value.status)
            assertNull(harness.sync.state.value.reason)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given an established local addition losing remote total order then visible policies match reduced winner`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "review-losing-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "review-losing-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 1000 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()

            second.recordDomainChanges(testPolicy(), testPolicy("losing.example"))
            advanceUntilIdle()
            second.recordDomainChanges(testPolicy("losing.example"), testPolicy())
            advanceUntilIdle()

            first.recordDomainChanges(testPolicy(), testPolicy("losing.example"))
            advanceUntilIdle()
            second.sync.syncNow()
            advanceUntilIdle()
            first.sync.syncNow()
            advanceUntilIdle()

            val projection = SyncReducer.reduce(first.snapshot().acceptedBundles.values.map { it.operation })
            assertEquals(emptyList(), projection.domains)
            assertEquals(0, intentRowCount(first))
            assertEquals(SyncStatus.COMPLETED, first.sync.state.value.status)
            assertEquals(localPolicy(second), localPolicy(first))
            assertEquals(emptyList(), localDomains(first))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given an established local removal losing remote total order then visible policies match reduced winner`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val first = AppleSyncTestHarness(dispatcher, "review-losing-removal-first.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val second = AppleSyncTestHarness(
            dispatcher,
            "review-losing-removal-second.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 1000 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()

            first.recordDomainChanges(testPolicy(), testPolicy("contested.example"))
            advanceUntilIdle()
            second.sync.syncNow()
            advanceUntilIdle()
            first.sync.syncNow()
            advanceUntilIdle()
            assertEquals(listOf("contested.example"), localDomains(first))
            assertEquals(listOf("contested.example"), localDomains(second))

            second.recordDomainChanges(testPolicy("contested.example"), testPolicy())
            advanceUntilIdle()
            second.recordDomainChanges(testPolicy(), testPolicy("contested.example"))
            advanceUntilIdle()
            first.recordDomainChanges(testPolicy("contested.example"), testPolicy())
            advanceUntilIdle()
            first.sync.syncNow()
            second.sync.syncNow()
            advanceUntilIdle()
            first.sync.syncNow()
            second.sync.syncNow()
            advanceUntilIdle()

            val projection = SyncReducer.reduce(first.snapshot().acceptedBundles.values.map { it.operation })
            assertEquals(listOf("contested.example"), projection.domains.map { it.canonicalValue })
            assertEquals(0, intentRowCount(first))
            assertEquals(SyncStatus.COMPLETED, first.sync.state.value.status)
            assertEquals(SyncStatus.COMPLETED, second.sync.state.value.status)
            assertEquals(localPolicy(second), localPolicy(first))
            assertEquals(listOf("contested.example"), localDomains(first))
        } finally {
            first.close()
            second.close()
        }
    }

    private suspend fun localPolicy(harness: AppleSyncTestHarness): TargetPolicy {
        return assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.sqlPolicy.read()).value.policy
    }

    private suspend fun localBase(harness: AppleSyncTestHarness): PolicySyncBase? {
        return assertIs<LocalPolicyResult.Success<PolicySyncBase?>>(harness.sqlPolicy.readBase()).value
    }

    private suspend fun localDomains(harness: AppleSyncTestHarness): List<String> {
        return localPolicy(harness).domains.map { it.canonicalValue }
    }

    private suspend fun acceptedSequences(harness: AppleSyncTestHarness): String {
        return harness.snapshot().acceptedBundles.values.map {
            it.operation.authorSequence.toString() + ":" + it.operation.payload.toString().substringBefore("(")
        }.sorted().joinToString()
    }

    private suspend fun replicaProjection(harness: AppleSyncTestHarness): String {
        val snapshot = harness.snapshot()
        val projection = SyncReducer.reduce(snapshot.acceptedBundles.values.map { it.operation })
        return projection.domains.map { it.canonicalValue }.sorted().joinToString()
    }

    private suspend fun frozenDomains(harness: AppleSyncTestHarness): String? {
        return harness.database.localSessionQueries.selectSession().executeAsList().single().frozen_domains
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
    }
}

internal class SharedFakeMailboxPort : MailboxPort {
    var beforeFetch: suspend () -> Unit = {}
    val saved = mutableListOf<MailboxBundle>()
    val cursors = mutableListOf<MailboxCursor>()
    val resumeResets = mutableListOf<ByteArray>()
    var saveResult: BundleSaveResult = BundleSaveResult.Saved

    override suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray,
    ): BundleSaveResult {
        if (saveResult != BundleSaveResult.Saved && saveResult != BundleSaveResult.Identical) {
            return saveResult
        }
        val known = saved.any { it.copyIdentifier().contentEquals(identifier) }
        if (!known) {
            saved.add(checkNotNull(MailboxBundle.fromParts(identifier, payload)))
        }
        return if (known) BundleSaveResult.Identical else BundleSaveResult.Saved
    }

    override suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor,
    ): ChangeFetchResult {
        cursors.add(cursor)
        beforeFetch()
        val index = cursorIndex(cursor)
        val bundle = saved.getOrNull(index)
        return if (bundle == null) {
            ChangeFetchResult.Page(ChangePage(null, false, cursor))
        } else {
            ChangeFetchResult.Page(ChangePage(bundle, index + 1 < saved.size, testCursor(index + 1)))
        }
    }

    override suspend fun deleteWorkspaceRecords(expectedBinding: AccountBinding): RecordDeleteResult {
        return RecordDeleteResult.DeletedAndAbsent
    }

    override suspend fun sweepBundlesIfAnchorMissing(expectedBinding: AccountBinding): BundleSweepResult {
        return BundleSweepResult.Swept
    }

    override suspend fun clearRemovalResumeState(expectedBinding: AccountBinding) {
        resumeResets += expectedBinding.copyBytes()
    }

    private fun cursorIndex(cursor: MailboxCursor): Int {
        val bytes = cursor.copyBytes()
        return if (bytes.isEmpty()) 0 else bytes[0].toInt()
    }
}

private const val FABRICATED_PRESENT_COUNT = 1025
private const val FABRICATED_CLOCK_BASE = 1000L

private suspend fun fabricateFullProjection(harness: AppleSyncTestHarness) {
    val crypto = FakeSyncCryptoProvider()
    val signingKey = crypto.createSigningKey()
    val transportKey = checkNotNull(readWorkspaceTransportKey(harness))
    val authorId = AuthorId(fabricatedIdentifier(0))
    val operations = (1..FABRICATED_PRESENT_COUNT + 1).map { sequence ->
        SyncOperation(
            operationId = BundleId(fabricatedIdentifier(sequence)),
            context = testContext,
            authorId = authorId,
            publicSigningKey = testPublicKey,
            authorSequence = sequence.toLong(),
            clock = HybridLogicalClock(FABRICATED_CLOCK_BASE + sequence, 0),
            payload = if (sequence == 1) {
                SyncOperationPayload.AuthorRegister
            } else {
                SyncOperationPayload.DomainPresent(checkNotNull(ExactDomain.restore("site$sequence.example")))
            },
        )
    }
    harness.database.transactionWithResult {
        harness.database.syncReplicaQueries.insertSyncReplicaState(
            workspace_id = testContext.workspaceId.value.copyBytes(),
            transport_epoch_id = testContext.transportEpochId.value.copyBytes(),
            key_epoch_id = testContext.keyEpochId.value.copyBytes(),
        )
        operations.forEachIndexed { index, operation ->
            val operationBytes = checkNotNull(SyncOperationCodec.encode(operation)).useAndClear { it.copyOf() }
            val salt = ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { saltIndex -> (index + saltIndex).toByte() }
            val prepared = assertIs<PrepareBundleResult.Success>(
                EncryptedBundleCodec(crypto).prepare(operation, transportKey, signingKey, salt),
            )
            harness.database.syncReplicaQueries.insertAcceptedBundle(
                bundle_id = operation.operationId.value.copyBytes(),
                bundle_bytes = prepared.bundle.copyBytes(),
                operation_bytes = operationBytes,
                author_id = operation.authorId.value.copyBytes(),
                author_sequence = operation.authorSequence,
                public_key = operation.publicSigningKey.copyBytes(),
                hlc_physical = operation.clock.physicalMillis,
                hlc_logical = operation.clock.logicalCounter.toLong(),
            )
        }
    }
    val maxPhysical = FABRICATED_CLOCK_BASE + FABRICATED_PRESENT_COUNT + 1
    harness.driver.execute(
        null,
        "UPDATE sync_replica_state SET hlc_physical = $maxPhysical, hlc_logical = 0, hlc_exhausted = 0",
        0,
    )
}

private fun fabricatedIdentifier(counter: Int): SyncIdentifier {
    val bytes = ByteArray(SyncFormatLimits.IDENTIFIER_BYTES) { index -> ((counter ushr (index * 8)) and 0xFF).toByte() }
    bytes[6] = (bytes[6].toInt() and 0x0F or 0x40).toByte()
    bytes[8] = (bytes[8].toInt() and 0x3F or 0x80).toByte()
    return checkNotNull(SyncIdentifier.fromUuidV4Bytes(bytes))
}

private fun readWorkspaceTransportKey(harness: AppleSyncTestHarness): TransportKey? {
    val account = BootstrapEncoding.identifierToAccountText(testContext.workspaceId.value)
    val itemBytes = harness.keys.items[account] ?: return null
    val item = WorkspaceKeyItem.fromBytes(itemBytes) ?: return null
    val decoded = BootstrapEncoding.decodeKeyItem(item) ?: return null
    return TransportKey.fromBytes(decoded.workspaceKey).also { decoded.clear() }
}

private fun testNamedPolicy(name: String): TargetPolicy {
    val parsed = assertIs<ApplicationPolicyNameResult.Success>(ApplicationPolicyName.parse(name)).name
    return assertIs<TargetPolicyValidationResult.Success>(
        TargetPolicy.fromStoredValues(emptyList(), parsed.canonicalValue),
    ).policy
}
