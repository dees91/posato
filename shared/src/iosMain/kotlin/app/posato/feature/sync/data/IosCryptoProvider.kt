package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SyncFormatLimits
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess
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

        return provider.randomBytes(count)?.toByteArray(count)
    }

    override fun sha256(message: ByteArray): ByteArray? {
        return provider.sha256(message.toNSData())?.toByteArray(SHA256_BYTES)
    }

    override fun hmacSha256(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray? {
        return provider.hmacSha256(key.toNSData(), message.toNSData())?.toByteArray(SHA256_BYTES)
    }

    /** CommonCrypto's PBKDF2, so a slow derivation runs natively instead of one Swift call per round. */
    @OptIn(ExperimentalForeignApi::class)
    override fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        length: Int,
    ): ByteArray? {
        // CommonCrypto takes the password as a C string, so only ASCII passes through unchanged.
        val usable = password.isNotEmpty() && password.none { byte -> byte <= 0 } && salt.isNotEmpty()
        if (iterations < 1 || length < 1 || !usable) {
            return commonPbkdf2HmacSha256(password, salt, iterations, length)
        }
        val output = ByteArray(length)
        val text = password.decodeToString()
        val status = run {
            salt.usePinned { pinnedSalt ->
                output.usePinned { pinnedOutput ->
                    CCKeyDerivationPBKDF(
                        kCCPBKDF2,
                        text,
                        password.size.convert(),
                        pinnedSalt.addressOf(0).reinterpret<UByteVar>(),
                        salt.size.convert(),
                        kCCPRFHmacAlgSHA256,
                        iterations.convert(),
                        pinnedOutput.addressOf(0).reinterpret<UByteVar>(),
                        length.convert(),
                    )
                }
            }
        }
        return output.takeIf { status == kCCSuccess } ?: run {
            output.fill(0)
            null
        }
    }

    override fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray? {
        return provider.sealAesGcm(
            key.toNSData(),
            nonce.toNSData(),
            authenticatedData.toNSData(),
            plaintext.toNSData(),
        )?.toByteArray(plaintext.size + SyncFormatLimits.AES_TAG_BYTES)
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
        )?.toByteArray(ciphertextAndTag.size - SyncFormatLimits.AES_TAG_BYTES)
    }

    override fun createSigningKey(): SyncSigningKey? {
        val signingKey = provider.createSigningKey() ?: return null
        val publicKey = signingKey.publicKey()
            ?.toByteArray(SyncFormatLimits.PUBLIC_KEY_BYTES)
            ?.let(PublicSigningKey::fromBytes)
        return if (publicKey == null) {
            signingKey.close()
            null
        } else {
            IosSyncSigningKey(signingKey, publicKey)
        }
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
    override val publicKey: PublicSigningKey,
) : SyncSigningKey {
    override fun sign(message: ByteArray): ByteArray? {
        return signingKey.sign(message.toNSData())?.toByteArray(SyncFormatLimits.SIGNATURE_BYTES)
    }

    override fun close() {
        signingKey.close()
    }

    override fun toString(): String {
        return "IosSyncSigningKey(redacted)"
    }
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(expectedSize: Int): ByteArray? {
    if (expectedSize < 0 || length != expectedSize.toULong()) {
        return null
    }
    val result = ByteArray(expectedSize)
    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, expectedSize.toULong())
        }
    }

    return result
}

private const val SHA256_BYTES = 32
