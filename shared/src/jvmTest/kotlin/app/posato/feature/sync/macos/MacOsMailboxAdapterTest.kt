package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.ACCOUNT_BINDING_BYTES
import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.RecordDeleteResult
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
    fun `given an expired token when fetching then recovery has a distinct outcome`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.TokenExpired)))
        assertEquals(ChangeFetchResult.TokenExpired, adapter.fetchChanges(binding(), cursor()))
    }

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
    fun `given deleted records when deleting then deleted and absent is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.DeletedAndAbsent)))

        assertEquals(RecordDeleteResult.DeletedAndAbsent, adapter.deleteWorkspaceRecords(binding()))
    }

    @Test
    fun `given unknown exchange when deleting then retryable is returned`() = runTest {
        val transport = FakeTransport(CompanionExchange.Unknown)
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(RecordDeleteResult.Retryable, adapter.deleteWorkspaceRecords(binding()))
        assertEquals(10, transport.exchangeCount)
    }

    @Test
    fun `given incomplete then deleted when deleting then the resume token is sent`() = runTest {
        val token = byteArrayOf(4, 5, 6)
        val transport = FakeTransport(
            message(SyncCompanionOutcome.Incomplete, token),
            outcome(SyncCompanionOutcome.DeletedAndAbsent),
        )
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(RecordDeleteResult.DeletedAndAbsent, adapter.deleteWorkspaceRecords(binding()))

        val expected = MacOsSyncCompanionProtocol.deleteResumePayload(ByteArray(ACCOUNT_BINDING_BYTES) { 7 }, token)
        assertTrue(checkNotNull(transport.lastSentPayload).contentEquals(expected))
    }

    @Test
    fun `given malformed resume token when deleting then unknown outcome is returned`() = runTest {
        val transport = FakeTransport(
            message(SyncCompanionOutcome.Incomplete, ByteArray(MacOsSyncCompanionProtocol.DELETE_RESUME_TOKEN_BYTES + 1)),
        )
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(RecordDeleteResult.UnknownOutcome, adapter.deleteWorkspaceRecords(binding()))
        assertEquals(1, transport.exchangeCount)
    }

    @Test
    fun `given malformed resume token when sweeping then unknown outcome is returned`() = runTest {
        val transport = FakeTransport(
            message(SyncCompanionOutcome.Incomplete, ByteArray(MacOsSyncCompanionProtocol.CURSOR_BYTES + 1)),
        )
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(BundleSweepResult.UnknownOutcome, adapter.sweepBundlesIfAnchorMissing(binding()))
        assertEquals(1, transport.exchangeCount)
    }

    @Test
    fun `given incomplete across the attempt cap when deleting then the next call resumes`() = runTest {
        // 200 deletion-only pages at 10 attempts per call: the first call
        // ends retryable, and the second call resumes from the tenth page's
        // token instead of replaying the same start cursors.
        val tokens = (0..200).map { byteArrayOf(it.toByte()) }
        val script = tokens.dropLast(1).map { message(SyncCompanionOutcome.Incomplete, it) } +
            outcome(SyncCompanionOutcome.DeletedAndAbsent)
        val transport = FakeTransport(*script.toTypedArray())
        val adapter = MacOsMailboxAdapter(transport)
        val data = binding()

        assertEquals(RecordDeleteResult.Retryable, adapter.deleteWorkspaceRecords(data))
        assertEquals(10, transport.exchangeCount)
        assertEquals(RecordDeleteResult.Retryable, adapter.deleteWorkspaceRecords(data))
        assertEquals(20, transport.exchangeCount)
        val resumedPayload = MacOsSyncCompanionProtocol.deleteResumePayload(
            ByteArray(ACCOUNT_BINDING_BYTES) { 7 },
            tokens[9],
        )
        assertTrue(checkNotNull(transport.sentPayloads[10]).contentEquals(resumedPayload))
        repeat(18) {
            assertEquals(RecordDeleteResult.Retryable, adapter.deleteWorkspaceRecords(data))
        }
        assertEquals(200, transport.exchangeCount)
        assertEquals(RecordDeleteResult.DeletedAndAbsent, adapter.deleteWorkspaceRecords(data))
        assertEquals(201, transport.exchangeCount)
    }

    @Test
    fun `given incomplete across the attempt cap when sweeping then the next call resumes`() = runTest {
        val tokens = (0..25).map { byteArrayOf(it.toByte()) }
        val script = tokens.dropLast(1).map { message(SyncCompanionOutcome.Incomplete, it) } +
            outcome(SyncCompanionOutcome.Swept)
        val transport = FakeTransport(*script.toTypedArray())
        val adapter = MacOsMailboxAdapter(transport)
        val data = binding()

        assertEquals(BundleSweepResult.Retryable, adapter.sweepBundlesIfAnchorMissing(data))
        assertEquals(BundleSweepResult.Retryable, adapter.sweepBundlesIfAnchorMissing(data))
        val resumedPayload = MacOsSyncCompanionProtocol.cursorPayload(
            ByteArray(ACCOUNT_BINDING_BYTES) { 7 },
            tokens[9],
        )
        assertTrue(checkNotNull(transport.sentPayloads[10]).contentEquals(resumedPayload))
        assertEquals(BundleSweepResult.Swept, adapter.sweepBundlesIfAnchorMissing(data))
        assertEquals(26, transport.exchangeCount)
    }

    @Test
    fun `given unknown outcome when deleting then the stored cursor is kept`() = runTest {
        val token = byteArrayOf(4, 5)
        val transport = FakeTransport(
            message(SyncCompanionOutcome.Incomplete, token),
            outcome(SyncCompanionOutcome.UnknownOutcome),
            outcome(SyncCompanionOutcome.DeletedAndAbsent),
        )
        val adapter = MacOsMailboxAdapter(transport)
        val data = binding()

        assertEquals(RecordDeleteResult.UnknownOutcome, adapter.deleteWorkspaceRecords(data))
        assertEquals(RecordDeleteResult.DeletedAndAbsent, adapter.deleteWorkspaceRecords(data))
        // The second call resumes from the kept cursor instead of restarting:
        // index 2 is the second call's first payload, while index 1 only
        // proves the in-call resume after the first incomplete.
        val resumedPayload = MacOsSyncCompanionProtocol.deleteResumePayload(
            ByteArray(ACCOUNT_BINDING_BYTES) { 7 },
            token,
        )
        assertTrue(checkNotNull(transport.sentPayloads[2]).contentEquals(resumedPayload))
    }

    @Test
    fun `given unknown outcome when sweeping then the stored cursor is kept`() = runTest {
        val token = byteArrayOf(4, 5)
        val transport = FakeTransport(
            message(SyncCompanionOutcome.Incomplete, token),
            outcome(SyncCompanionOutcome.UnknownOutcome),
            outcome(SyncCompanionOutcome.Swept),
        )
        val adapter = MacOsMailboxAdapter(transport)
        val data = binding()

        assertEquals(BundleSweepResult.UnknownOutcome, adapter.sweepBundlesIfAnchorMissing(data))
        assertEquals(BundleSweepResult.Swept, adapter.sweepBundlesIfAnchorMissing(data))
        // The second call resumes from the kept cursor instead of restarting.
        val resumedPayload = MacOsSyncCompanionProtocol.cursorPayload(
            ByteArray(ACCOUNT_BINDING_BYTES) { 7 },
            token,
        )
        assertTrue(checkNotNull(transport.sentPayloads[2]).contentEquals(resumedPayload))
    }

    @Test
    fun `given terminal outcome when deleting then the stored cursor is cleared`() = runTest {
        val token = byteArrayOf(4, 5)
        val script = List(10) { message(SyncCompanionOutcome.Incomplete, token) } +
            outcome(SyncCompanionOutcome.DeletedAndAbsent) +
            outcome(SyncCompanionOutcome.DeletedAndAbsent)
        val transport = FakeTransport(*script.toTypedArray())
        val adapter = MacOsMailboxAdapter(transport)
        val data = binding()

        assertEquals(RecordDeleteResult.Retryable, adapter.deleteWorkspaceRecords(data))
        assertEquals(RecordDeleteResult.DeletedAndAbsent, adapter.deleteWorkspaceRecords(data))
        assertEquals(RecordDeleteResult.DeletedAndAbsent, adapter.deleteWorkspaceRecords(data))
        val plainPayload = MacOsSyncCompanionProtocol.cloudPayload(ByteArray(ACCOUNT_BINDING_BYTES) { 7 })
        assertTrue(checkNotNull(transport.lastSentPayload).contentEquals(plainPayload))
    }

    @Test
    fun `given changed binding when deleting then the stored cursor is dropped`() = runTest {
        val token = byteArrayOf(4, 5)
        val script = List(10) { message(SyncCompanionOutcome.Incomplete, token) } +
            outcome(SyncCompanionOutcome.DeletedAndAbsent)
        val transport = FakeTransport(*script.toTypedArray())
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(RecordDeleteResult.Retryable, adapter.deleteWorkspaceRecords(binding()))
        assertEquals(RecordDeleteResult.DeletedAndAbsent, adapter.deleteWorkspaceRecords(otherBinding()))
        val plainPayload = MacOsSyncCompanionProtocol.cloudPayload(ByteArray(ACCOUNT_BINDING_BYTES) { 8 })
        assertTrue(checkNotNull(transport.lastSentPayload).contentEquals(plainPayload))
    }

    @Test
    fun `given swept outcome when sweeping then swept is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.Swept)))

        assertEquals(BundleSweepResult.Swept, adapter.sweepBundlesIfAnchorMissing(binding()))
    }

    @Test
    fun `given anchor present outcome when sweeping then anchor present is returned`() = runTest {
        val adapter = MacOsMailboxAdapter(FakeTransport(outcome(SyncCompanionOutcome.AnchorPresent)))

        assertEquals(BundleSweepResult.AnchorPresent, adapter.sweepBundlesIfAnchorMissing(binding()))
    }

    @Test
    fun `given unknown exchange when sweeping then retryable is returned`() = runTest {
        val transport = FakeTransport(CompanionExchange.Unknown)
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(BundleSweepResult.Retryable, adapter.sweepBundlesIfAnchorMissing(binding()))
        assertEquals(10, transport.exchangeCount)
    }

    @Test
    fun `given incomplete then swept when sweeping then the resume token is sent`() = runTest {
        val token = byteArrayOf(8, 9)
        val transport = FakeTransport(
            message(SyncCompanionOutcome.Incomplete, token),
            outcome(SyncCompanionOutcome.Swept),
        )
        val adapter = MacOsMailboxAdapter(transport)

        assertEquals(BundleSweepResult.Swept, adapter.sweepBundlesIfAnchorMissing(binding()))

        val expected = MacOsSyncCompanionProtocol.cursorPayload(ByteArray(ACCOUNT_BINDING_BYTES) { 7 }, token)
        assertTrue(checkNotNull(transport.lastSentPayload).contentEquals(expected))
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

    private fun otherBinding(): AccountBinding {
        return checkNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 8 }))
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
        private vararg val exchanges: CompanionExchange,
    ) : SyncCompanionTransport {
        var lastPayload: ByteArray? = null
        var lastSentPayload: ByteArray? = null
        val sentPayloads = mutableListOf<ByteArray>()
        var lastOperation: SyncCompanionOperation? = null
        var lastCapabilities: Long? = null
        var exchangeCount: Int = 0
            private set

        override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
            lastPayload = message.payload
            lastSentPayload = message.payload.copyOf()
            sentPayloads.add(message.payload.copyOf())
            lastOperation = message.operation
            lastCapabilities = message.capabilities
            val index = minOf(exchangeCount, exchanges.size - 1)
            exchangeCount += 1
            return exchanges[index]
        }

        override fun newRequestIdentifier(): ByteArray {
            return ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 }
        }
    }
}
