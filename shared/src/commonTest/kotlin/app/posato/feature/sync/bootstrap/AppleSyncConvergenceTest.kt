package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncReducer
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.ZoneDeleteResult
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ApplicationPolicyNameResult
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
    val saved = mutableListOf<MailboxBundle>()
    val cursors = mutableListOf<MailboxCursor>()
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
        val index = cursorIndex(cursor)
        val bundle = saved.getOrNull(index)
        return if (bundle == null) {
            ChangeFetchResult.Page(ChangePage(null, false, cursor))
        } else {
            ChangeFetchResult.Page(ChangePage(bundle, index + 1 < saved.size, testCursor(index + 1)))
        }
    }

    override suspend fun deleteZoneAndVerifyAbsent(expectedBinding: AccountBinding): ZoneDeleteResult {
        return ZoneDeleteResult.DeletedAndAbsent
    }

    private fun cursorIndex(cursor: MailboxCursor): Int {
        val bytes = cursor.copyBytes()
        return if (bytes.isEmpty()) 0 else bytes[0].toInt()
    }
}

private fun testNamedPolicy(name: String): TargetPolicy {
    val parsed = assertIs<ApplicationPolicyNameResult.Success>(ApplicationPolicyName.parse(name)).name
    return assertIs<TargetPolicyValidationResult.Success>(
        TargetPolicy.fromStoredValues(emptyList(), parsed.canonicalValue),
    ).policy
}
