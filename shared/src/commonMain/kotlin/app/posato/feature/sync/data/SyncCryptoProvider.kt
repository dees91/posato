package app.posato.feature.sync.data

import app.posato.feature.sync.domain.PublicSigningKey

internal interface SyncSigningKey {
    val publicKey: PublicSigningKey

    fun sign(message: ByteArray): ByteArray?

    fun close()
}

internal interface SyncCryptoProvider {
    fun randomBytes(count: Int): ByteArray?

    fun sha256(message: ByteArray): ByteArray?

    fun hmacSha256(
        key: ByteArray,
        message: ByteArray
    ): ByteArray?

    fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray
    ): ByteArray?

    fun openAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        ciphertextAndTag: ByteArray
    ): ByteArray?

    fun createSigningKey(): SyncSigningKey?

    /**
     * PBKDF2 with HMAC-SHA256 (RFC 8018). Providers with a native implementation override it; this one is
     * the reference the vectors pin and the fallback for any other provider.
     */
    fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        length: Int,
    ): ByteArray? {
        return commonPbkdf2HmacSha256(password, salt, iterations, length)
    }

    fun verifyEd25519(
        publicKey: PublicSigningKey,
        message: ByteArray,
        signature: ByteArray
    ): Boolean
}

internal fun SyncCryptoProvider.hkdfSha256(
    inputKeyMaterial: ByteArray,
    salt: ByteArray,
    info: ByteArray,
): ByteArray? {
    val pseudorandomKey = hmacSha256(salt, inputKeyMaterial) ?: return null
    return try {
        val expansionInput = info + byteArrayOf(1)
        hmacSha256(pseudorandomKey, expansionInput)
    } finally {
        pseudorandomKey.fill(0)
    }
}

private const val SHA256_BYTES: Int = 32

/** RFC 8018 PBKDF2 over this provider's HMAC-SHA256; every intermediate is zeroed before it returns. */
internal fun SyncCryptoProvider.commonPbkdf2HmacSha256(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    length: Int,
): ByteArray? {
    if (iterations < 1 || length < 1) {
        return null
    }
    val output = ByteArray(length)
    val blocks = (length + SHA256_BYTES - 1) / SHA256_BYTES
    for (block in 1..blocks) {
        val index = byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte())
        var round = hmacSha256(password, salt + index) ?: return null.also { output.fill(0) }
        val sum = round.copyOf()
        repeat(iterations - 1) {
            val next = hmacSha256(password, round) ?: return null.also {
                output.fill(0)
                sum.fill(0)
                round.fill(0)
            }
            round.fill(0)
            round = next
            for (position in sum.indices) {
                sum[position] = (sum[position].toInt() xor round[position].toInt()).toByte()
            }
        }
        round.fill(0)
        val offset = (block - 1) * SHA256_BYTES
        sum.copyInto(output, offset, 0, minOf(SHA256_BYTES, length - offset))
        sum.fill(0)
    }
    return output
}
