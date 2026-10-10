package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SyncFormatLimits
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
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
            val publicKey = PublicSigningKey.fromBytes(keyPair.public.toRawBytes())
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

    override fun toString(): String {
        return "JdkSyncSigningKey(redacted)"
    }
}

/**
 * The raw key from its X.509 encoding, which the JDK's EdEC keys and Android's Conscrypt keys share; Conscrypt's
 * Ed25519 keys are not `EdECPublicKey`, so the encoding is the one form both platforms read and write.
 */
private fun java.security.PublicKey.toRawBytes(): ByteArray {
    val encoded = encoded
    require(encoded.size == ED25519_X509_PREFIX.size + SyncFormatLimits.PUBLIC_KEY_BYTES)
    require(encoded.copyOf(ED25519_X509_PREFIX.size).contentEquals(ED25519_X509_PREFIX))
    return encoded.copyOfRange(ED25519_X509_PREFIX.size, encoded.size)
}

private fun PublicSigningKey.toJdkPublicKey(): java.security.PublicKey {
    return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(ED25519_X509_PREFIX + copyBytes()))
}

private val ED25519_X509_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

private fun <T> cryptographicCall(block: () -> T): T? {
    return try {
        block()
    } catch (_: Exception) {
        null
    }
}
