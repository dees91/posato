package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.ACCOUNT_BINDING_BYTES
import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AnchorCreateResult
import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.bootstrap.ZoneFetchResult
import app.posato.feature.sync.bootstrap.ZoneSaveResult
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MacOsBootstrapCloudAdapterTest {
    @Test
    fun `given found zone when fetching then found is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.Found)))

        assertEquals(ZoneFetchResult.Found, adapter.fetchZone(binding()))
    }

    @Test
    fun `given missing zone when fetching then missing is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.Missing)))

        assertEquals(ZoneFetchResult.Missing, adapter.fetchZone(binding()))
    }

    @Test
    fun `given already exists when saving zone then already exists is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.AlreadyExists)))

        assertEquals(ZoneSaveResult.AlreadyExists, adapter.saveZone(binding()))
    }

    @Test
    fun `given created when saving zone then created is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.Created)))

        assertEquals(ZoneSaveResult.Created, adapter.saveZone(binding()))
    }

    @Test
    fun `given found anchor when reading then the anchor is returned`() = runTest {
        val fields = anchorFields()
        val transport = FakeTransport(message(SyncCompanionOutcome.Found, fields))
        val adapter = MacOsBootstrapCloudAdapter(transport)

        val result = adapter.readAnchor(binding())

        assertIs<AnchorReadResult.Found>(result)
        assertEquals(anchor(), result.anchor)
    }

    @Test
    fun `given short anchor when reading then integrity failure is returned`() = runTest {
        val transport = FakeTransport(message(SyncCompanionOutcome.Found, byteArrayOf(1, 2, 3)))
        val adapter = MacOsBootstrapCloudAdapter(transport)

        assertEquals(AnchorReadResult.IntegrityFailure, adapter.readAnchor(binding()))
    }

    @Test
    fun `given missing anchor when reading then missing is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.Missing)))

        assertEquals(AnchorReadResult.Missing, adapter.readAnchor(binding()))
    }

    @Test
    fun `given conflict when creating anchor then conflict is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.Conflict)))

        assertEquals(AnchorCreateResult.Conflict, adapter.createAnchor(binding(), anchor()))
    }

    @Test
    fun `given created when creating anchor then created is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.Created)))

        assertEquals(AnchorCreateResult.Created, adapter.createAnchor(binding(), anchor()))
    }

    @Test
    fun `given account changed when fetching zone then account changed is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(outcome(SyncCompanionOutcome.AccountChanged)))

        assertEquals(ZoneFetchResult.AccountChanged, adapter.fetchZone(binding()))
    }

    @Test
    fun `given unknown exchange when saving zone then unknown outcome is returned`() = runTest {
        val adapter = MacOsBootstrapCloudAdapter(FakeTransport(CompanionExchange.Unknown))

        assertEquals(ZoneSaveResult.UnknownOutcome, adapter.saveZone(binding()))
    }

    @Test
    fun `given unexpected outcome when fetching zone then unknown outcome is returned`() = runTest {
        val transport = FakeTransport(message(SyncCompanionOutcome.Identical, ByteArray(0)))
        val adapter = MacOsBootstrapCloudAdapter(transport)

        assertEquals(ZoneFetchResult.UnknownOutcome, adapter.fetchZone(binding()))
    }

    @Test
    fun `given cloud operation when exchanged then the cloudkit capability is sent`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Missing))
        val adapter = MacOsBootstrapCloudAdapter(transport)

        adapter.fetchZone(binding())

        assertEquals(SyncCompanionOperation.FetchZone, transport.lastOperation)
        assertEquals(MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY, transport.lastCapabilities)
    }

    @Test
    fun `given create anchor when finished then field copies are cleared`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Created))
        val adapter = MacOsBootstrapCloudAdapter(transport)

        adapter.createAnchor(binding(), anchor())

        val sent = checkNotNull(transport.lastPayload)
        assertTrue(sent.all { byte -> byte == 0.toByte() })
    }

    private fun outcome(outcome: SyncCompanionOutcome): CompanionExchange {
        return CompanionExchange.Message(
            SyncCompanionMessage(
                operation = SyncCompanionOperation.FetchZone,
                requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                deadlineMilliseconds = 5_000,
                capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
                outcome = outcome,
                payload = ByteArray(0),
            ),
        )
    }

    private fun message(
        outcome: SyncCompanionOutcome,
        payload: ByteArray,
    ): CompanionExchange {
        return CompanionExchange.Message(
            SyncCompanionMessage(
                operation = SyncCompanionOperation.ReadAnchor,
                requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                deadlineMilliseconds = 5_000,
                capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
                outcome = outcome,
                payload = payload,
            ),
        )
    }

    private fun binding(): AccountBinding {
        return checkNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 7 }))
    }

    private fun identifier(value: Byte): SyncIdentifier {
        val bytes = ByteArray(MacOsSyncCompanionProtocol.BUNDLE_IDENTIFIER_BYTES)
        bytes[6] = 0x40
        bytes[8] = 0x80.toByte()
        bytes[15] = value
        return checkNotNull(SyncIdentifier.fromUuidV4Bytes(bytes))
    }

    private fun anchor(): WorkspaceAnchor {
        return WorkspaceAnchor(
            workspaceId = WorkspaceId(identifier(1)),
            transportEpochId = TransportEpochId(identifier(2)),
            keyEpochId = KeyEpochId(identifier(3)),
        )
    }

    private fun anchorFields(): ByteArray {
        return identifier(1).copyBytes() + identifier(2).copyBytes() + identifier(3).copyBytes()
    }

    private class FakeTransport(
        private val exchange: CompanionExchange,
    ) : SyncCompanionTransport {
        var lastPayload: ByteArray? = null
        var lastOperation: SyncCompanionOperation? = null
        var lastCapabilities: Long? = null

        override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
            lastPayload = message.payload
            lastOperation = message.operation
            lastCapabilities = message.capabilities
            return exchange
        }

        override fun newRequestIdentifier(): ByteArray {
            return ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 }
        }
    }
}
