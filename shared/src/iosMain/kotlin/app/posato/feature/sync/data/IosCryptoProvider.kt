package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PublicSigningKey
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

interface IosSigningKey {
    fun publicKey(): NSData?

    fun sign(message: NSData): NSData?

    fun close()
}

interface IosCryptoProvider {
    fun randomBytes(count: Int): NSData?

    fun sha256(message: NSData): NSData?

    fun hmacSha256(
        key: NSData,
        message: NSData,
    ): NSData?

    fun sealAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        plaintext: NSData,
    ): NSData?

    fun openAesGcm(
        key: NSData,
        nonce: NSData,
        authenticatedData: NSData,
        ciphertextAndTag: NSData,
    ): NSData?

    fun createSigningKey(): IosSigningKey?

    fun verifyEd25519(
        publicKey: NSData,
        message: NSData,
        signature: NSData,
    ): Boolean
}

internal class IosSyncCryptoProvider(
    private val provider: IosCryptoProvider,
) : SyncCryptoProvider {
    override fun randomBytes(count: Int): ByteArray? {
        if (count < 0) {
            return null
        }

        return provider.randomBytes(count)
            ?.takeIf { bytes -> bytes.length == count.toULong() }
            ?.toByteArray()
    }

    override fun sha256(message: ByteArray): ByteArray? {
        return provider.sha256(message.toNSData())?.toByteArray()
    }

    override fun hmacSha256(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray? {
        return provider.hmacSha256(key.toNSData(), message.toNSData())?.toByteArray()
    }

    override fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray? {
        return provider.sealAesGcm(key.toNSData(), nonce.toNSData(), authenticatedData.toNSData(), plaintext.toNSData())?.toByteArray()
    }

    override fun openAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        ciphertextAndTag: ByteArray,
    ): ByteArray? {
        return provider.openAesGcm(
            key.toNSData(),
            nonce.toNSData(),
            authenticatedData.toNSData(),
            ciphertextAndTag.toNSData(),
        )?.toByteArray()
    }

    override fun createSigningKey(): SyncSigningKey? {
        return provider.createSigningKey()?.let(::IosSyncSigningKey)
    }

    override fun verifyEd25519(
        publicKey: PublicSigningKey,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        return provider.verifyEd25519(publicKey.copyBytes().toNSData(), message.toNSData(), signature.toNSData())
    }
}

private class IosSyncSigningKey(
    private val signingKey: IosSigningKey,
) : SyncSigningKey {
    override val publicKey: PublicSigningKey = checkNotNull(signingKey.publicKey()?.toByteArray()?.let(PublicSigningKey::fromBytes))

    override fun sign(message: ByteArray): ByteArray? {
        return signingKey.sign(message.toNSData())?.toByteArray()
    }

    override fun close() {
        signingKey.close()
    }
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray? {
    if (length > Int.MAX_VALUE.toULong()) {
        return null
    }
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }

    return result
}
