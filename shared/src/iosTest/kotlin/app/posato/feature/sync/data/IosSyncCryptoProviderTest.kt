package app.posato.feature.sync.data

import kotlinx.cinterop.BetaInteropApi
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
        listOf<NSData?>(null, ByteArray(31).toNSData(), ByteArray(33).toNSData()).forEach { publicKey ->
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
}

private class FakeIosCryptoProvider(
    private val randomBytes: NSData? = null,
    private val signingKey: IosSigningKey? = null,
) : IosCryptoProvider {
    val requestedCounts = mutableListOf<Int>()

    override fun randomBytes(count: Int): NSData? {
        requestedCounts += count
        return randomBytes
    }

    override fun sha256(message: NSData): NSData? = null

    override fun hmacSha256(
        key: NSData,
        message: NSData,
    ): NSData? = null

    override fun sealAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        plaintext: NSData,
    ): NSData? = null

    override fun openAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        ciphertextAndTag: NSData,
    ): NSData? = null

    override fun createSigningKey(): IosSigningKey? = signingKey

    override fun verifyEd25519(
        publicKey: NSData,
        message: NSData,
        signature: NSData,
    ): Boolean = false
}

private class FakeIosSigningKey(
    private val publicKey: NSData?,
) : IosSigningKey {
    var closeCount = 0
        private set

    override fun publicKey(): NSData? = publicKey

    override fun sign(message: NSData): NSData? = null

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
