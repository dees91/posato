package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.ACCOUNT_BINDING_BYTES
import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.KEYCHAIN_ITEM_BYTES
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemCreateResult
import app.posato.feature.sync.bootstrap.KeyItemDeleteResult
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceKeyItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MacOsBootstrapKeychainAdapterTest {
    @Test
    fun `given unavailable entitlements when resolving binding then unavailable is returned`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Unavailable))
        val adapter = MacOsBootstrapKeychainAdapter(transport)

        assertEquals(BindingResolution.Unavailable, adapter.resolveBinding())
    }

    @Test
    fun `given unavailable entitlements when reading then retryable is returned`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Unavailable))
        val adapter = MacOsBootstrapKeychainAdapter(transport)

        assertEquals(KeyItemReadResult.Retryable, adapter.readItem(binding(), account()))
    }

    @Test
    fun `given a different account when reading then no payload is consumed as found`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.AccountChanged))
        val adapter = MacOsBootstrapKeychainAdapter(transport)

        assertEquals(KeyItemReadResult.AccountChanged, adapter.readItem(binding(), account()))
    }

    @Test
    fun `given identical create then already exists is returned`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Identical))
        val adapter = MacOsBootstrapKeychainAdapter(transport)

        assertEquals(KeyItemCreateResult.AlreadyExists, adapter.createItem(binding(), account(), item()))
    }

    @Test
    fun `given unknown exchange when deleting then unknown outcome is returned`() = runTest {
        val transport = FakeTransport(CompanionExchange.Unknown)
        val adapter = MacOsBootstrapKeychainAdapter(transport)

        assertEquals(KeyItemDeleteResult.UnknownOutcome, adapter.deleteItemAndVerifyAbsent(binding(), account()))
    }

    @Test
    fun `given create when finished then payload copies are cleared`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Created))
        val adapter = MacOsBootstrapKeychainAdapter(transport)

        adapter.createItem(binding(), account(), item())

        val sent = checkNotNull(transport.lastPayload)
        assertTrue(sent.all { byte -> byte == 0.toByte() })
    }

    @Test
    fun `given found binding bytes when resolving then the account port returns available`() = runTest {
        val bytes = ByteArray(ACCOUNT_BINDING_BYTES) { 7 }
        val transport = FakeTransport(
            CompanionExchange.Message(
                SyncCompanionMessage(
                    operation = SyncCompanionOperation.ResolveBinding,
                    requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                    deadlineMilliseconds = 5_000,
                    capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
                    outcome = SyncCompanionOutcome.Found,
                    payload = bytes,
                ),
            ),
        )
        val adapter = MacOsBootstrapKeychainAdapter(transport)

        val resolution = adapter.resolveBinding()

        assertIs<BindingResolution.Available>(resolution)
        assertEquals(AccountBinding.fromBytes(bytes), resolution.binding)
    }

    private fun outcome(outcome: SyncCompanionOutcome): CompanionExchange {
        return CompanionExchange.Message(
            SyncCompanionMessage(
                operation = SyncCompanionOperation.ReadItem,
                requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                deadlineMilliseconds = 5_000,
                capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
                outcome = outcome,
                payload = ByteArray(0),
            ),
        )
    }

    private fun binding(): AccountBinding {
        return checkNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 7 }))
    }

    private fun account(): KeyAccount {
        return checkNotNull(KeyAccount.fromText("00000000-0000-4000-8000-000000000001"))
    }

    private fun item(): WorkspaceKeyItem {
        return checkNotNull(WorkspaceKeyItem.fromBytes(ByteArray(KEYCHAIN_ITEM_BYTES) { 6 }))
    }

    private class FakeTransport(
        private val exchange: CompanionExchange,
    ) : SyncCompanionTransport {
        var lastPayload: ByteArray? = null

        override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
            lastPayload = message.payload
            return exchange
        }

        override fun newRequestIdentifier(): ByteArray {
            return ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 }
        }
    }
}
