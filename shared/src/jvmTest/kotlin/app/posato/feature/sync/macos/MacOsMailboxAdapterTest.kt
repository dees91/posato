package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.ACCOUNT_BINDING_BYTES
import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.ZoneDeleteResult
import kotlinx.coroutines.test.runTest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacOsMailboxAdapterTest {
    @Test
    fun `given created bundle when saving then saved is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.Created)))

        assertEquals(BundleSaveResult.Saved, adapter.saveBundle(binding(), identifier(), byteArrayOf(1, 2)))
    }

    @Test
    fun `given identical bundle when saving then identical is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.Identical)))

        assertEquals(BundleSaveResult.Identical, adapter.saveBundle(binding(), identifier(), byteArrayOf(1)))
    }

    @Test
    fun `given conflict bundle when saving then conflict is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.Conflict)))

        assertEquals(BundleSaveResult.Conflict, adapter.saveBundle(binding(), identifier(), byteArrayOf(1)))
    }

    @Test
    fun `given integrity failure when saving then integrity failure is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.IntegrityFailure)))

        assertEquals(BundleSaveResult.IntegrityFailure, adapter.saveBundle(binding(), identifier(), byteArrayOf(1)))
    }

    @Test
    fun `given page when fetching then the bundle and cursor are returned`() = runTest {
        val page = pageBytes(more = false, cursor = byteArrayOf(4, 5), identifier = identifier(), bundle = byteArrayOf(6))
        val transport = FakeTransport(message(SyncCompanionOutcome.Found, page))
        val adapter = MacOsMailboxAdapter(transport)

        val result = adapter.fetchChanges(binding(), cursor())

        assertIs<ChangeFetchResult.Page>(result)
        assertEquals(false, result.page.moreChanges)
        assertEquals(MailboxCursor.fromBytes(byteArrayOf(4, 5)), result.page.nextCursor)
        assertEquals(MailboxBundle.fromParts(identifier(), byteArrayOf(6)), result.page.bundle)
    }

    @Test
    fun `given empty page when fetching then no bundle with more changes is returned`() = runTest {
        val page = pageBytes(more = true, cursor = byteArrayOf(7), identifier = null, bundle = null)
        val transport = FakeTransport(message(SyncCompanionOutcome.Found, page))
        val adapter = MacOsMailboxAdapter(transport)

        val result = adapter.fetchChanges(binding(), cursor())

        assertIs<ChangeFetchResult.Page>(result)
        assertNull(result.page.bundle)
        assertEquals(true, result.page.moreChanges)
    }

    @Test
    fun `given missing zone when fetching then zone missing is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.Missing)))

        assertEquals(ChangeFetchResult.ZoneMissing, adapter.fetchChanges(binding(), cursor()))
    }

    @Test
    fun `given malformed page when fetching then integrity failure is returned`() = runTest {
        val transport = FakeTransport(message(SyncCompanionOutcome.Found, byteArrayOf(1, 2, 3)))
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(ChangeFetchResult.IntegrityFailure, adapter.fetchChanges(binding(), cursor()))
    }

    @Test
    fun `given trailing bytes when fetching then integrity failure is returned`() = runTest {
        val page = pageBytes(more = false, cursor = ByteArray(0), identifier = null, bundle = null) + byteArrayOf(9)
        val transport = FakeTransport(message(SyncCompanionOutcome.Found, page))
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(ChangeFetchResult.IntegrityFailure, adapter.fetchChanges(binding(), cursor()))
    }

    @Test
    fun `given deleted zone when deleting then deleted and absent is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.DeletedAndAbsent)))

        assertEquals(ZoneDeleteResult.DeletedAndAbsent, adapter.deleteZoneAndVerifyAbsent(binding()))
    }

    @Test
    fun `given unknown exchange when deleting then unknown outcome is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(CompanionExchange.Unknown))

        assertEquals(ZoneDeleteResult.UnknownOutcome, adapter.deleteZoneAndVerifyAbsent(binding()))
    }

    @Test
    fun `given save bundle when exchanged then the save operation is sent`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Created))
        val adapter = MacOsMailboxAdapter(transport)

        adapter.saveBundle(binding(), identifier(), byteArrayOf(1))

        assertEquals(SyncCompanionOperation.SaveBundle, transport.lastOperation)
        assertEquals(MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY, transport.lastCapabilities)
    }

    @Test
    fun `given save bundle when finished then payload copies are cleared`() = runTest {
        val transport = FakeTransport(outcome(SyncCompanionOutcome.Created))
        val adapter = MacOsMailboxAdapter(transport)

        adapter.saveBundle(binding(), identifier(), byteArrayOf(1, 2, 3))

        val sent = checkNotNull(transport.lastPayload)
        assertTrue(sent.all { byte -> byte == 0.toByte() })
    }

    @Test
    fun `given carriers when stringified then secrets stay redacted`() {
        assertEquals("MailboxBundle(redacted)", MailboxBundle.fromParts(identifier(), byteArrayOf(1)).toString())
        assertEquals("MailboxCursor(redacted)", MailboxCursor.fromBytes(byteArrayOf(1)).toString())
        assertEquals(
            "ChangePage(redacted)",
            ChangePage(
                bundle = MailboxBundle.fromParts(identifier(), byteArrayOf(1)),
                moreChanges = false,
                nextCursor = checkNotNull(MailboxCursor.fromBytes(byteArrayOf(1))),
            ).toString(),
        )
    }

    @Test
    fun `given oversized cursor when created then no cursor is returned`() {
        assertNull(MailboxCursor.fromBytes(ByteArray(MacOsSyncCompanionProtocol.CURSOR_BYTES + 1)))
    }

    @Test
    fun `given empty bundle when created then no bundle is returned`() {
        assertNull(MailboxBundle.fromParts(identifier(), ByteArray(0)))
    }

    private fun outcome(outcome: SyncCompanionOutcome): CompanionExchange {
        return message(outcome, ByteArray(0))
    }

    private fun message(
        outcome: SyncCompanionOutcome,
        payload: ByteArray,
    ): CompanionExchange {
        return CompanionExchange.Message(
            SyncCompanionMessage(
                operation = SyncCompanionOperation.FetchChanges,
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

    private fun identifier(): ByteArray {
        return ByteArray(MacOsSyncCompanionProtocol.BUNDLE_IDENTIFIER_BYTES) { 9 }
    }

    private fun cursor(): MailboxCursor {
        return checkNotNull(MailboxCursor.fromBytes(ByteArray(0)))
    }

    private fun pageBytes(
        more: Boolean,
        cursor: ByteArray,
        identifier: ByteArray?,
        bundle: ByteArray?,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN)
        buffer.put(if (more) 1 else 0)
        buffer.putInt(cursor.size)
        buffer.put(cursor)
        if (identifier == null || bundle == null) {
            buffer.put(0)
        } else {
            buffer.put(1)
            buffer.put(identifier)
            buffer.putInt(bundle.size)
            buffer.put(bundle)
        }
        return buffer.array().copyOf(buffer.position())
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
