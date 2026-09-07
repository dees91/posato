package app.posato.feature.sync.data

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
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MAILBOX_CURSOR_BYTES
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.ZoneDeleteResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSData
import platform.Foundation.NSThread
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosCloudKitMailboxAdapterTest {
    @Test
    fun `given a zone outcome when fetched then the outcome matches`() = runTest {
        val cases = listOf(
            IosCloudZoneFetchStatus.Found to ZoneFetchResult.Found,
            IosCloudZoneFetchStatus.Missing to ZoneFetchResult.Missing,
            IosCloudZoneFetchStatus.Retryable to ZoneFetchResult.Retryable,
            IosCloudZoneFetchStatus.AccountChanged to ZoneFetchResult.AccountChanged,
            IosCloudZoneFetchStatus.UnknownOutcome to ZoneFetchResult.UnknownOutcome,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosCloudKitMailboxProvider(zoneFetch = status)

            assertEquals(expected, IosBootstrapCloudAdapter(provider).fetchZone(testBinding()))
        }
    }

    @Test
    fun `given a zone outcome when saved then the outcome matches`() = runTest {
        val cases = listOf(
            IosCloudZoneSaveStatus.Created to ZoneSaveResult.Created,
            IosCloudZoneSaveStatus.AlreadyExists to ZoneSaveResult.AlreadyExists,
            IosCloudZoneSaveStatus.Retryable to ZoneSaveResult.Retryable,
            IosCloudZoneSaveStatus.AccountChanged to ZoneSaveResult.AccountChanged,
            IosCloudZoneSaveStatus.UnknownOutcome to ZoneSaveResult.UnknownOutcome,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosCloudKitMailboxProvider(zoneSave = status)

            assertEquals(expected, IosBootstrapCloudAdapter(provider).saveZone(testBinding()))
        }
    }

    @Test
    fun `given found exact anchor fields when read then the identical anchor is found`() = runTest {
        val fields = anchorFieldBytes()
        val provider = FakeIosCloudKitMailboxProvider(anchorRead = IosCloudAnchorRead(IosCloudAnchorReadStatus.Found, fields.toCloudNSData()))

        assertEquals(AnchorReadResult.Found(testAnchor()), IosBootstrapCloudAdapter(provider).readAnchor(testBinding()))
    }

    @Test
    fun `given found wrong length fields when read then integrity fails without copying`() = runTest {
        val provider = FakeIosCloudKitMailboxProvider(
            anchorRead = IosCloudAnchorRead(IosCloudAnchorReadStatus.Found, MailboxUnguardedLengthNSData(47)),
        )

        assertEquals(AnchorReadResult.IntegrityFailure, IosBootstrapCloudAdapter(provider).readAnchor(testBinding()))
    }

    @Test
    fun `given a non found anchor outcome when read then the outcome matches`() = runTest {
        val cases = listOf(
            IosCloudAnchorReadStatus.Missing to AnchorReadResult.Missing,
            IosCloudAnchorReadStatus.Retryable to AnchorReadResult.Retryable,
            IosCloudAnchorReadStatus.AccountChanged to AnchorReadResult.AccountChanged,
            IosCloudAnchorReadStatus.UnknownOutcome to AnchorReadResult.UnknownOutcome,
            IosCloudAnchorReadStatus.IntegrityFailure to AnchorReadResult.IntegrityFailure,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosCloudKitMailboxProvider(anchorRead = IosCloudAnchorRead(status, null))

            assertEquals(expected, IosBootstrapCloudAdapter(provider).readAnchor(testBinding()))
        }
    }

    @Test
    fun `given an anchor outcome when created then the outcome matches`() = runTest {
        val cases = listOf(
            IosCloudAnchorCreateStatus.Created to AnchorCreateResult.Created,
            IosCloudAnchorCreateStatus.Conflict to AnchorCreateResult.Conflict,
            IosCloudAnchorCreateStatus.Retryable to AnchorCreateResult.Retryable,
            IosCloudAnchorCreateStatus.AccountChanged to AnchorCreateResult.AccountChanged,
            IosCloudAnchorCreateStatus.UnknownOutcome to AnchorCreateResult.UnknownOutcome,
            IosCloudAnchorCreateStatus.IntegrityFailure to AnchorCreateResult.IntegrityFailure,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosCloudKitMailboxProvider(anchorCreate = status)

            assertEquals(expected, IosBootstrapCloudAdapter(provider).createAnchor(testBinding(), testAnchor()))
        }
    }

    @Test
    fun `given a create when executed then the binding and anchor bytes are forwarded`() = runTest {
        val provider = FakeIosCloudKitMailboxProvider(anchorCreate = IosCloudAnchorCreateStatus.Created)
        val binding = testBinding()
        val anchor = testAnchor()

        assertEquals(AnchorCreateResult.Created, IosBootstrapCloudAdapter(provider).createAnchor(binding, anchor))
        assertContentEquals(binding.copyBytes(), provider.seenBindings.single())
        assertContentEquals(anchorFieldBytes(), provider.seenAnchorFields.single())
    }

    @Test
    fun `given a bundle outcome when saved then the outcome matches`() = runTest {
        val cases = listOf(
            IosCloudBundleSaveStatus.Saved to BundleSaveResult.Saved,
            IosCloudBundleSaveStatus.Identical to BundleSaveResult.Identical,
            IosCloudBundleSaveStatus.Conflict to BundleSaveResult.Conflict,
            IosCloudBundleSaveStatus.Retryable to BundleSaveResult.Retryable,
            IosCloudBundleSaveStatus.AccountChanged to BundleSaveResult.AccountChanged,
            IosCloudBundleSaveStatus.UnknownOutcome to BundleSaveResult.UnknownOutcome,
            IosCloudBundleSaveStatus.IntegrityFailure to BundleSaveResult.IntegrityFailure,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosCloudKitMailboxProvider(bundleSave = status)

            assertEquals(expected, IosMailboxAdapter(provider).saveBundle(testBinding(), testIdentifier(), testPayload()))
        }
    }

    @Test
    fun `given a save when executed then the binding identifier and payload are forwarded`() = runTest {
        val provider = FakeIosCloudKitMailboxProvider(bundleSave = IosCloudBundleSaveStatus.Saved)
        val binding = testBinding()
        val identifier = testIdentifier()
        val payload = testPayload()

        assertEquals(BundleSaveResult.Saved, IosMailboxAdapter(provider).saveBundle(binding, identifier, payload))
        assertContentEquals(binding.copyBytes(), provider.seenBindings.single())
        assertContentEquals(identifier, provider.seenIdentifiers.single())
        assertContentEquals(payload, provider.seenPayloads.single())
    }

    @Test
    fun `given a page when fetched then the identical bundle and cursor cross`() = runTest {
        val identifier = testIdentifier()
        val payload = testPayload()
        val cursor = byteArrayOf(0x0A.toByte())
        val provider = FakeIosCloudKitMailboxProvider(
            changePage = IosCloudChangePage(
                IosCloudChangeFetchStatus.Page,
                true,
                cursor.toCloudNSData(),
                identifier.toCloudNSData(),
                payload.toCloudNSData(),
            ),
        )

        val expected = ChangeFetchResult.Page(
            ChangePage(
                bundle = checkNotNull(MailboxBundle.fromParts(identifier, payload)),
                moreChanges = true,
                nextCursor = checkNotNull(MailboxCursor.fromBytes(cursor)),
            ),
        )

        assertEquals(expected, IosMailboxAdapter(provider).fetchChanges(testBinding(), testCursor()))
    }

    @Test
    fun `given a page without a bundle when fetched then the cursor still crosses`() = runTest {
        val cursor = byteArrayOf(0x0B.toByte())
        val provider = FakeIosCloudKitMailboxProvider(
            changePage = IosCloudChangePage(IosCloudChangeFetchStatus.Page, false, cursor.toCloudNSData(), null, null),
        )

        val expected = ChangeFetchResult.Page(
            ChangePage(bundle = null, moreChanges = false, nextCursor = checkNotNull(MailboxCursor.fromBytes(cursor))),
        )

        assertEquals(expected, IosMailboxAdapter(provider).fetchChanges(testBinding(), testCursor()))
    }

    @Test
    fun `given a half present bundle when fetched then integrity fails`() = runTest {
        val identifierOnly = FakeIosCloudKitMailboxProvider(
            changePage = IosCloudChangePage(
                IosCloudChangeFetchStatus.Page,
                false,
                byteArrayOf(0x01).toCloudNSData(),
                testIdentifier().toCloudNSData(),
                null,
            ),
        )

        assertEquals(
            ChangeFetchResult.IntegrityFailure,
            IosMailboxAdapter(identifierOnly).fetchChanges(testBinding(), testCursor()),
        )

        val payloadOnly = FakeIosCloudKitMailboxProvider(
            changePage = IosCloudChangePage(
                IosCloudChangeFetchStatus.Page,
                false,
                byteArrayOf(0x01).toCloudNSData(),
                null,
                testPayload().toCloudNSData(),
            ),
        )

        assertEquals(
            ChangeFetchResult.IntegrityFailure,
            IosMailboxAdapter(payloadOnly).fetchChanges(testBinding(), testCursor()),
        )
    }

    @Test
    fun `given an oversized cursor when fetched then integrity fails without copying`() = runTest {
        val provider = FakeIosCloudKitMailboxProvider(
            changePage = IosCloudChangePage(
                IosCloudChangeFetchStatus.Page,
                false,
                MailboxUnguardedLengthNSData(MAILBOX_CURSOR_BYTES + 1),
                null,
                null,
            ),
        )

        assertEquals(
            ChangeFetchResult.IntegrityFailure,
            IosMailboxAdapter(provider).fetchChanges(testBinding(), testCursor()),
        )
    }

    @Test
    fun `given a non page outcome when fetched then the outcome matches`() = runTest {
        val cases = listOf(
            IosCloudChangeFetchStatus.ZoneMissing to ChangeFetchResult.ZoneMissing,
            IosCloudChangeFetchStatus.Retryable to ChangeFetchResult.Retryable,
            IosCloudChangeFetchStatus.AccountChanged to ChangeFetchResult.AccountChanged,
            IosCloudChangeFetchStatus.UnknownOutcome to ChangeFetchResult.UnknownOutcome,
            IosCloudChangeFetchStatus.IntegrityFailure to ChangeFetchResult.IntegrityFailure,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosCloudKitMailboxProvider(
                changePage = IosCloudChangePage(status, false, null, null, null),
            )

            assertEquals(expected, IosMailboxAdapter(provider).fetchChanges(testBinding(), testCursor()))
        }
    }

    @Test
    fun `given a delete outcome when deleted then the outcome matches`() = runTest {
        val cases = listOf(
            IosCloudZoneDeleteStatus.DeletedAndAbsent to ZoneDeleteResult.DeletedAndAbsent,
            IosCloudZoneDeleteStatus.Retryable to ZoneDeleteResult.Retryable,
            IosCloudZoneDeleteStatus.AccountChanged to ZoneDeleteResult.AccountChanged,
            IosCloudZoneDeleteStatus.UnknownOutcome to ZoneDeleteResult.UnknownOutcome,
        )

        cases.forEach { (status, expected) ->
            val provider = FakeIosCloudKitMailboxProvider(zoneDelete = status)

            assertEquals(expected, IosMailboxAdapter(provider).deleteZoneAndVerifyAbsent(testBinding()))
        }
    }

    @Test
    fun `given a cancelled fetch when executed then cancel reaches the provider`() = runTest {
        val provider = BlockingFakeMailboxProvider()
        val job = launch(Dispatchers.Default) {
            IosMailboxAdapter(provider).fetchChanges(testBinding(), testCursor())
        }

        withTimeout(10_000) {
            while (!provider.entered) {
                kotlinx.coroutines.delay(10)
            }
        }
        job.cancelAndJoin()

        assertTrue(provider.cancelRecorded)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `given the new mailbox carriers when described then values stay redacted`() {
        assertEquals(
            "IosCloudAnchorRead(redacted)",
            IosCloudAnchorRead(IosCloudAnchorReadStatus.Found, null).toString(),
        )
        assertEquals(
            "IosCloudChangePage(redacted)",
            IosCloudChangePage(IosCloudChangeFetchStatus.Page, false, null, null, null).toString(),
        )
    }

    @Test
    fun `given a fetch when executed then the binding and cursor are forwarded`() = runTest {
        val provider = FakeIosCloudKitMailboxProvider(
            changePage = IosCloudChangePage(IosCloudChangeFetchStatus.ZoneMissing, false, null, null, null),
        )
        val binding = testBinding()
        val cursor = testCursor()

        assertEquals(ChangeFetchResult.ZoneMissing, IosMailboxAdapter(provider).fetchChanges(binding, cursor))
        assertContentEquals(binding.copyBytes(), provider.seenBindings.single())
        assertContentEquals(cursor.copyBytes(), provider.seenCursors.single())
    }
}

private fun testBinding(): AccountBinding {
    return checkNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { index -> index.toByte() }))
}

private fun testCursor(): MailboxCursor {
    return checkNotNull(MailboxCursor.fromBytes(byteArrayOf(0x01)))
}

private fun testIdentifier(): ByteArray {
    return byteArrayOf(
        0x12,
        0x3E,
        0x45,
        0x67,
        0xE8.toByte(),
        0x9B.toByte(),
        0x42,
        0xD3.toByte(),
        0xA4.toByte(),
        0x56,
        0x42,
        0x66,
        0x14,
        0x17,
        0x40,
        0x00,
    )
}

private fun testPayload(): ByteArray {
    return byteArrayOf(0x01, 0x02)
}

private fun testAnchor(): WorkspaceAnchor {
    val workspace = byteArrayOf(
        0x12,
        0x3E,
        0x45,
        0x67,
        0xE8.toByte(),
        0x9B.toByte(),
        0x42,
        0xD3.toByte(),
        0xA4.toByte(),
        0x56,
        0x42,
        0x66,
        0x14,
        0x17,
        0x40,
        0x00,
    )
    val transport = workspace.copyOf().also { it[15] = 0x01 }
    val keyEpoch = workspace.copyOf().also { it[15] = 0x02 }
    return WorkspaceAnchor(
        workspaceId = WorkspaceId(checkNotNull(SyncIdentifier.fromUuidV4Bytes(workspace))),
        transportEpochId = TransportEpochId(checkNotNull(SyncIdentifier.fromUuidV4Bytes(transport))),
        keyEpochId = KeyEpochId(checkNotNull(SyncIdentifier.fromUuidV4Bytes(keyEpoch))),
    )
}

private fun anchorFieldBytes(): ByteArray {
    val workspace = testAnchor().workspaceId.value.copyBytes()
    val transport = testAnchor().transportEpochId.value.copyBytes()
    val keyEpoch = testAnchor().keyEpochId.value.copyBytes()
    return workspace + transport + keyEpoch
}

private class MailboxUnguardedLengthNSData(
    private val reportedLength: Int,
) : NSData() {
    override fun length(): ULong = reportedLength.toULong()

    override fun bytes(): CPointer<out CPointed>? {
        error("Wrong-sized native data must not be copied")
    }
}

private class FakeIosCloudKitMailboxProvider(
    var zoneFetch: IosCloudZoneFetchStatus = IosCloudZoneFetchStatus.Retryable,
    var zoneSave: IosCloudZoneSaveStatus = IosCloudZoneSaveStatus.Retryable,
    var anchorRead: IosCloudAnchorRead = IosCloudAnchorRead(IosCloudAnchorReadStatus.Retryable, null),
    var anchorCreate: IosCloudAnchorCreateStatus = IosCloudAnchorCreateStatus.Retryable,
    var bundleSave: IosCloudBundleSaveStatus = IosCloudBundleSaveStatus.Retryable,
    var changePage: IosCloudChangePage = IosCloudChangePage(IosCloudChangeFetchStatus.Retryable, false, null, null, null),
    var zoneDelete: IosCloudZoneDeleteStatus = IosCloudZoneDeleteStatus.Retryable,
) : IosCloudKitMailboxProvider {
    val seenBindings = mutableListOf<ByteArray>()
    val seenAnchorFields = mutableListOf<ByteArray>()
    val seenIdentifiers = mutableListOf<ByteArray>()
    val seenPayloads = mutableListOf<ByteArray>()
    val seenCursors = mutableListOf<ByteArray>()

    override fun fetchZone(binding: NSData): IosCloudZoneFetchStatus {
        seenBindings += binding.copyBytes()
        return zoneFetch
    }

    override fun saveZone(binding: NSData): IosCloudZoneSaveStatus {
        seenBindings += binding.copyBytes()
        return zoneSave
    }

    override fun readAnchor(binding: NSData): IosCloudAnchorRead {
        seenBindings += binding.copyBytes()
        return anchorRead
    }

    override fun createAnchor(
        binding: NSData,
        fields: NSData,
    ): IosCloudAnchorCreateStatus {
        seenBindings += binding.copyBytes()
        seenAnchorFields += fields.copyBytes()
        return anchorCreate
    }

    override fun saveBundle(
        binding: NSData,
        identifier: NSData,
        payload: NSData,
    ): IosCloudBundleSaveStatus {
        seenBindings += binding.copyBytes()
        seenIdentifiers += identifier.copyBytes()
        seenPayloads += payload.copyBytes()
        return bundleSave
    }

    override fun fetchChanges(
        binding: NSData,
        cursor: NSData,
    ): IosCloudChangePage {
        seenBindings += binding.copyBytes()
        seenCursors += cursor.copyBytes()
        return changePage
    }

    override fun deleteZoneAndVerifyAbsent(binding: NSData): IosCloudZoneDeleteStatus {
        seenBindings += binding.copyBytes()
        return zoneDelete
    }

    override fun cancelInflight() = Unit
}

private class BlockingFakeMailboxProvider : IosCloudKitMailboxProvider {
    @Volatile
    var entered = false

    @Volatile
    var cancelRecorded = false

    private fun unimplemented(): Nothing = error("Only fetchChanges is implemented")

    override fun fetchZone(binding: NSData): IosCloudZoneFetchStatus = unimplemented()

    override fun saveZone(binding: NSData): IosCloudZoneSaveStatus = unimplemented()

    override fun readAnchor(binding: NSData): IosCloudAnchorRead = unimplemented()

    override fun createAnchor(
        binding: NSData,
        fields: NSData,
    ): IosCloudAnchorCreateStatus = unimplemented()

    override fun saveBundle(
        binding: NSData,
        identifier: NSData,
        payload: NSData,
    ): IosCloudBundleSaveStatus = unimplemented()

    override fun fetchChanges(
        binding: NSData,
        cursor: NSData,
    ): IosCloudChangePage {
        entered = true
        while (!cancelRecorded) {
            NSThread.sleepForTimeInterval(0.005)
        }
        return IosCloudChangePage(IosCloudChangeFetchStatus.UnknownOutcome, false, null, null, null)
    }

    override fun deleteZoneAndVerifyAbsent(binding: NSData): IosCloudZoneDeleteStatus = unimplemented()

    override fun cancelInflight() {
        cancelRecorded = true
    }
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toCloudNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@OptIn(BetaInteropApi::class)
private fun NSData.copyBytes(): ByteArray {
    val result = ByteArray(length.toInt())
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
