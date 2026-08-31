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
