package app.posato.feature.sync.bootstrap

import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.data.SyncStoreResult
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.RecordDeleteResult
import app.posato.feature.sync.testContext
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalPolicyTestDatabase
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.SyncTargetPolicyStore
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class AppleSyncTestHarness(
    dispatcher: CoroutineDispatcher,
    name: String = "apple-sync-test.db",
    bootstrapStore: BootstrapStore? = null,
    mailboxPort: MailboxPort? = null,
    wallClock: SyncWallClock = SyncWallClock { 100 },
    cryptoProvider: FakeSyncCryptoProvider? = null,
    private val testDatabase: LocalPolicyTestDatabase = createLocalPolicyTestDatabase(name),
) {
    val driver = testDatabase.openDriver()
    val database = PosatoDatabase(driver)
    val store = bootstrapStore ?: SqlBootstrapStore(database, dispatcher)
    val replica = SqlSyncReplicaStore(database, dispatcher)
    val account = FakeBootstrapAccountPort()
    val cloud = FakeBootstrapCloudPort()
    val keys = FakeBootstrapKeyPort()
    val mailbox = FakeMailboxPort()
    val crypto = cryptoProvider ?: FakeSyncCryptoProvider()
    val sqlPolicy = SqlLocalTargetPolicyStore(database, dispatcher)
    val sync = AppleSync(
        BootstrapCoordinator(account, cloud, keys, store, crypto, mailboxPort ?: mailbox),
        SyncOperationCore(replica, crypto, wallClock),
        mailboxPort ?: mailbox,
        keys,
        store,
        sqlPolicy,
        crypto,
        dispatcher,
    )
    val syncPolicy = SyncTargetPolicyStore(sqlPolicy, sync)

    suspend fun waitForKey() {
        cloud.zoneExists = true
        cloud.storedAnchor = WorkspaceAnchor(testContext.workspaceId, testContext.transportEpochId, testContext.keyEpochId)
        sync.syncWithIcloud()
    }

    fun deliverJoinKey() {
        val context = testContext
        keys.items[BootstrapEncoding.identifierToAccountText(context.workspaceId.value)] = checkNotNull(
            BootstrapEncoding.encodeKeyItem(context.workspaceId, context.transportEpochId, context.keyEpochId, ByteArray(32) { 7 }),
        ).copyBytes()
    }

    suspend fun establish() {
        val context = testContext
        store.commitEstablished(EstablishedWorkspace(context, bindingA))
        cloud.zoneExists = true
        cloud.storedAnchor = WorkspaceAnchor(context.workspaceId, context.transportEpochId, context.keyEpochId)
        val account = BootstrapEncoding.identifierToAccountText(context.workspaceId.value)
        keys.items[account] = checkNotNull(
            BootstrapEncoding.encodeKeyItem(context.workspaceId, context.transportEpochId, context.keyEpochId, ByteArray(32) { 7 }),
        ).copyBytes()
    }

    suspend fun recordDomainChanges(
        before: TargetPolicy,
        after: TargetPolicy
    ) {
        val current = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.read()).value
        assertEquals(before, current.policy)
        assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(syncPolicy.replace(current.revision, after))
    }

    suspend fun snapshot(): SyncReplicaSnapshot {
        return assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(replica.read(testContext)).value
    }

    suspend fun close() {
        closeKeepingDatabase()
        testDatabase.delete()
    }

    suspend fun closeKeepingDatabase() {
        sync.close()
        driver.close()
    }
}

internal class FakeMailboxPort : MailboxPort {
    val saved = mutableListOf<MailboxBundle>()
    val cursors = mutableListOf<MailboxCursor>()
    val pages = ArrayDeque<ChangeFetchResult>()
    var saveResult: BundleSaveResult = BundleSaveResult.Saved
    var deleteResult: RecordDeleteResult = RecordDeleteResult.DeletedAndAbsent
    val deleteResults: ArrayDeque<RecordDeleteResult> = ArrayDeque()
    var deleteCalls = 0
    var sweepResult: BundleSweepResult = BundleSweepResult.Swept
    var sweepCalls = 0
    val resumeResets = mutableListOf<ByteArray>()
    var beforeFetch: suspend () -> Unit = {}

    override suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray
    ): BundleSaveResult {
        saved.add(checkNotNull(MailboxBundle.fromParts(identifier, payload)))
        return saveResult
    }

    override suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor
    ): ChangeFetchResult {
        cursors.add(cursor)
        beforeFetch()
        return pages.removeFirstOrNull() ?: ChangeFetchResult.Page(ChangePage(null, false, testCursor(1)))
    }

    override suspend fun deleteWorkspaceRecords(expectedBinding: AccountBinding): RecordDeleteResult {
        deleteCalls += 1
        return deleteResults.removeFirstOrNull() ?: deleteResult
    }

    override suspend fun sweepBundlesIfAnchorMissing(expectedBinding: AccountBinding): BundleSweepResult {
        sweepCalls += 1
        return sweepResult
    }

    override suspend fun clearRemovalResumeState(expectedBinding: AccountBinding) {
        resumeResets += expectedBinding.copyBytes()
    }
}

internal suspend fun intentRowCount(harness: AppleSyncTestHarness): Int {
    return harness.database.syncLocalPolicyQueries.selectIntents().executeAsList().size
}

internal fun testCursor(value: Int): MailboxCursor {
    return checkNotNull(MailboxCursor.fromBytes(byteArrayOf(value.toByte())))
}

internal fun testPolicy(vararg domains: String): TargetPolicy {
    return assertIs<TargetPolicyValidationResult.Success>(TargetPolicy.fromStoredValues(domains.toList(), null)).policy
}
