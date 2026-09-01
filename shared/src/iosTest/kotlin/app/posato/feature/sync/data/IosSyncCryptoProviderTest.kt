package app.posato.feature.sync.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosSyncCryptoProviderTest {
    @Test
    fun `given native random output when adapted then only the requested length succeeds`() {
        val provider = FixedRandomIosCryptoProvider(ByteArray(1).toNSData())
        assertNull(IosSyncCryptoProvider(provider).randomBytes(-1))
        assertEquals(emptyList(), provider.requestedCounts)

        listOf(15, 17).forEach { returnedCount ->
            val wrongSizedProvider = FixedRandomIosCryptoProvider(ByteArray(returnedCount).toNSData())

            assertNull(IosSyncCryptoProvider(wrongSizedProvider).randomBytes(16))
            assertEquals(listOf(16), wrongSizedProvider.requestedCounts)
        }

        val exactProvider = FixedRandomIosCryptoProvider(ByteArray(16) { it.toByte() }.toNSData())
        assertContentEquals(ByteArray(16) { it.toByte() }, IosSyncCryptoProvider(exactProvider).randomBytes(16))
    }
}

private class FixedRandomIosCryptoProvider(
    private val randomBytes: NSData?,
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

    override fun createSigningKey(): IosSigningKey? = null

    override fun verifyEd25519(
        publicKey: NSData,
        message: NSData,
        signature: NSData,
    ): Boolean = false
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
