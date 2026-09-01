package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SyncFormatLimits
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.EdECPublicKey
import java.security.spec.EdECPoint
import java.security.spec.EdECPublicKeySpec
import java.security.spec.NamedParameterSpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class JdkSyncCryptoProvider(
    private val secureRandom: SecureRandom = SecureRandom(),
) : SyncCryptoProvider {
    override fun randomBytes(count: Int): ByteArray? {
        if (count < 0) {
            return null
        }
        return cryptographicCall {
            ByteArray(count).also(secureRandom::nextBytes)
        }
    }

    override fun sha256(message: ByteArray): ByteArray? {
        return cryptographicCall { MessageDigest.getInstance("SHA-256").digest(message) }
    }

    override fun hmacSha256(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray? {
        return cryptographicCall {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac.doFinal(message)
        }
    }

    override fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray? {
        return aesGcm(Cipher.ENCRYPT_MODE, key, nonce, authenticatedData, plaintext)
    }

    override fun openAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        ciphertextAndTag: ByteArray,
    ): ByteArray? {
        return aesGcm(Cipher.DECRYPT_MODE, key, nonce, authenticatedData, ciphertextAndTag)
    }

    override fun createSigningKey(): SyncSigningKey? {
        return cryptographicCall {
            val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val publicKey = PublicSigningKey.fromBytes((keyPair.public as EdECPublicKey).toRawBytes())
                ?: return@cryptographicCall null
            JdkSyncSigningKey(publicKey, keyPair.private)
        }
    }

    override fun verifyEd25519(
        publicKey: PublicSigningKey,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        if (signature.size != SyncFormatLimits.SIGNATURE_BYTES) {
            return false
        }

        return cryptographicCall {
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey.toJdkPublicKey())
            verifier.update(message)
            verifier.verify(signature)
        } == true
    }

    private fun aesGcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        input: ByteArray,
    ): ByteArray? {
        if (key.size != SyncFormatLimits.AES_KEY_BYTES || nonce.size != SyncFormatLimits.AES_NONCE_BYTES) {
            return null
        }

        return cryptographicCall {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(SyncFormatLimits.AES_TAG_BYTES * Byte.SIZE_BITS, nonce))
            cipher.updateAAD(authenticatedData)
            cipher.doFinal(input)
        }
    }
}

private class JdkSyncSigningKey(
    override val publicKey: PublicSigningKey,
    private var privateKey: java.security.PrivateKey?,
) : SyncSigningKey {
    override fun sign(message: ByteArray): ByteArray? {
        val retainedKey = privateKey ?: return null

        return cryptographicCall {
            val signer = Signature.getInstance("Ed25519")
            signer.initSign(retainedKey)
            signer.update(message)
            signer.sign()
        }
    }

    override fun close() {
        privateKey = null
    }
}

private fun EdECPublicKey.toRawBytes(): ByteArray {
    val result = point.y.toFixedLittleEndian(SyncFormatLimits.PUBLIC_KEY_BYTES)
    if (point.isXOdd) {
        result[result.lastIndex] = (result.last().toInt() or ED25519_X_ODD_MASK).toByte()
    }

    return result
}

private fun PublicSigningKey.toJdkPublicKey(): java.security.PublicKey {
    val raw = copyBytes()
    val isXOdd = raw.last().toInt() and ED25519_X_ODD_MASK != 0
    raw[raw.lastIndex] = (raw.last().toInt() and ED25519_Y_MASK).toByte()
    val y = BigInteger(1, raw.reversedArray())
    val spec = EdECPublicKeySpec(NamedParameterSpec.ED25519, EdECPoint(isXOdd, y))

    return KeyFactory.getInstance("Ed25519").generatePublic(spec)
}

private const val ED25519_X_ODD_MASK = 0x80
private const val ED25519_Y_MASK = 0x7F

private fun BigInteger.toFixedLittleEndian(size: Int): ByteArray {
    val signedBigEndian = toByteArray()
    val unsignedBigEndian = if (signedBigEndian.size > 1 && signedBigEndian.first() == 0.toByte()) {
        signedBigEndian.copyOfRange(1, signedBigEndian.size)
    } else {
        signedBigEndian
    }
    require(unsignedBigEndian.size <= size)
    val result = ByteArray(size)
    unsignedBigEndian.reversedArray().copyInto(result)

    return result
}

private fun <T> cryptographicCall(block: () -> T): T? {
    return try {
        block()
    } catch (_: Exception) {
        null
    }
}
