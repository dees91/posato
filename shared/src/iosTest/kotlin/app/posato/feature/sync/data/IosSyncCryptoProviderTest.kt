package app.posato.feature.sync.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IosSyncCryptoProviderTest {
    @Test
    fun `given a signing key when represented then its identity is redacted`() {
        val nativeSigningKey = FakeIosSigningKey(ByteArray(32).toNSData())
        val signingKey = assertNotNull(
            IosSyncCryptoProvider(FakeIosCryptoProvider(signingKey = nativeSigningKey)).createSigningKey(),
        )
        try {
            assertEquals("IosSyncSigningKey(redacted)", signingKey.toString())
        } finally {
            signingKey.close()
        }
        assertEquals(1, nativeSigningKey.closeCount)
    }

    @Test
    fun `given native random output when adapted then only the requested length succeeds`() {
        val provider = FakeIosCryptoProvider(randomBytes = ByteArray(1).toNSData())
        assertNull(IosSyncCryptoProvider(provider).randomBytes(-1))
        assertEquals(emptyList(), provider.requestedCounts)

        listOf(15, 17).forEach { returnedCount ->
            val wrongSizedProvider = FakeIosCryptoProvider(randomBytes = ByteArray(returnedCount).toNSData())

            assertNull(IosSyncCryptoProvider(wrongSizedProvider).randomBytes(16))
            assertEquals(listOf(16), wrongSizedProvider.requestedCounts)
        }

        val exactProvider = FakeIosCryptoProvider(randomBytes = ByteArray(16) { it.toByte() }.toNSData())
        assertContentEquals(ByteArray(16) { it.toByte() }, IosSyncCryptoProvider(exactProvider).randomBytes(16))
    }

    @Test
    fun `given invalid native signing public key when adapted then creation fails and the handle closes`() {
        listOf<NSData?>(null, ByteArray(31).toNSData(), UnreadableWrongSizedNSData(33u)).forEach { publicKey ->
            val signingKey = FakeIosSigningKey(publicKey)

            assertNull(IosSyncCryptoProvider(FakeIosCryptoProvider(signingKey = signingKey)).createSigningKey())
            assertEquals(1, signingKey.closeCount)
        }
    }

    @Test
    fun `given an exact native signing public key when adapted then the wrapper owns the handle`() {
        val publicKey = ByteArray(32) { it.toByte() }
        val signingKey = FakeIosSigningKey(publicKey.toNSData())
        val adapted = assertNotNull(IosSyncCryptoProvider(FakeIosCryptoProvider(signingKey = signingKey)).createSigningKey())

        assertContentEquals(publicKey, adapted.publicKey.copyBytes())
        assertEquals(0, signingKey.closeCount)

        adapted.close()

        assertEquals(1, signingKey.closeCount)
    }

    @Test
    fun `given wrong-sized native cryptographic output when adapted then no payload is copied`() {
        val wrongSizedOutput = UnreadableWrongSizedNSData(65u)
        val provider = IosSyncCryptoProvider(FakeIosCryptoProvider(cryptographicResult = wrongSizedOutput))

        val input = byteArrayOf(1)
        assertNull(provider.sha256(input))
        assertNull(provider.hmacSha256(input, input))
        assertNull(provider.sealAesGcm(input, input, input, input))
        assertNull(provider.openAesGcm(input, input, input, ByteArray(16)))

        val signingKey = FakeIosSigningKey(ByteArray(32).toNSData(), wrongSizedOutput)
        val adapted = assertNotNull(IosSyncCryptoProvider(FakeIosCryptoProvider(signingKey = signingKey)).createSigningKey())
        assertNull(adapted.sign(input))
        adapted.close()
        assertEquals(1, signingKey.closeCount)
    }
}

private class UnreadableWrongSizedNSData(
    private val reportedLength: ULong,
) : NSData() {
    override fun length(): ULong = reportedLength

    override fun bytes(): CPointer<out CPointed>? {
        error("Wrong-sized native data must not be copied")
    }
}

private class FakeIosCryptoProvider(
    private val randomBytes: NSData? = null,
    private val signingKey: IosSigningKey? = null,
    private val cryptographicResult: NSData? = null,
) : IosCryptoProvider {
    val requestedCounts = mutableListOf<Int>()

    override fun randomBytes(count: Int): NSData? {
        requestedCounts += count
        return randomBytes
    }

    override fun sha256(message: NSData): NSData? = cryptographicResult

    override fun hmacSha256(
        key: NSData,
        message: NSData,
    ): NSData? = cryptographicResult

    override fun sealAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        plaintext: NSData,
    ): NSData? = cryptographicResult

    override fun openAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        ciphertextAndTag: NSData,
    ): NSData? = cryptographicResult

    override fun createSigningKey(): IosSigningKey? = signingKey

    override fun verifyEd25519(
        publicKey: NSData,
        message: NSData,
        signature: NSData,
    ): Boolean = false
}

private class FakeIosSigningKey(
    private val publicKey: NSData?,
    private val signature: NSData? = null,
) : IosSigningKey {
    var closeCount = 0
        private set

    override fun publicKey(): NSData? = publicKey

    override fun sign(message: NSData): NSData? = signature

    override fun close() {
        closeCount += 1
    }
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
