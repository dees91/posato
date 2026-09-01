package app.posato.feature.sync

import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.SyncSigningKey
import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId

internal fun testIdentifier(value: Int): SyncIdentifier {
    val bytes = ByteArray(SyncFormatLimits.IDENTIFIER_BYTES)
    bytes[6] = 0x40
    bytes[8] = 0x80.toByte()
    bytes[12] = (value ushr 24).toByte()
    bytes[13] = (value ushr 16).toByte()
    bytes[14] = (value ushr 8).toByte()
    bytes[15] = value.toByte()

    return checkNotNull(SyncIdentifier.fromUuidV4Bytes(bytes))
}

internal val testContext = SyncContext(
    workspaceId = WorkspaceId(testIdentifier(1)),
    transportEpochId = TransportEpochId(testIdentifier(2)),
    keyEpochId = KeyEpochId(testIdentifier(3)),
)

internal val testPublicKey = checkNotNull(PublicSigningKey.fromBytes(ByteArray(SyncFormatLimits.PUBLIC_KEY_BYTES) { 7 }))

internal fun testOperation(
    id: Int,
    sequence: Long,
    payload: SyncOperationPayload,
    author: Int = 10,
    physical: Long = sequence,
): SyncOperation {
    return SyncOperation(
        operationId = BundleId(testIdentifier(id)),
        context = testContext,
        authorId = AuthorId(testIdentifier(author)),
        publicSigningKey = testPublicKey,
        authorSequence = sequence,
        clock = HybridLogicalClock(physical, 0),
        payload = payload,
    )
}

internal class FakeSyncCryptoProvider(
    private val signingPublicKey: PublicSigningKey = testPublicKey,
) : SyncCryptoProvider {
    private var nextByte = 1
    internal var signingKeyCloseCount = 0
        private set

    override fun randomBytes(count: Int): ByteArray {
        return ByteArray(count) { nextByte++.toByte() }
    }

    override fun sha256(message: ByteArray): ByteArray {
        return ByteArray(32) { index -> (message.getOrElse(index) { 0 }.toInt() xor index).toByte() }
    }

    override fun hmacSha256(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray {
        return ByteArray(32) { index ->
            val keyByte = key[index % key.size].toInt()
            val messageByte = message[index % message.size].toInt()
            (keyByte xor messageByte xor index).toByte()
        }
    }

    override fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        return plaintext + authenticationTag(key, authenticatedData, plaintext)
    }

    override fun openAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        ciphertextAndTag: ByteArray,
    ): ByteArray? {
        if (ciphertextAndTag.size < SyncFormatLimits.AES_TAG_BYTES) {
            return null
        }
        val plaintext = ciphertextAndTag.copyOfRange(0, ciphertextAndTag.size - SyncFormatLimits.AES_TAG_BYTES)
        val tag = ciphertextAndTag.copyOfRange(plaintext.size, ciphertextAndTag.size)

        return plaintext.takeIf { tag.contentEquals(authenticationTag(key, authenticatedData, plaintext)) }
    }

    override fun createSigningKey(): SyncSigningKey {
        return FakeSyncSigningKey(signingPublicKey) { signingKeyCloseCount += 1 }
    }

    override fun verifyEd25519(
        publicKey: PublicSigningKey,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        return signature.contentEquals(fakeSignature(publicKey, message))
    }

    private fun authenticationTag(
        key: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        return ByteArray(SyncFormatLimits.AES_TAG_BYTES) { index ->
            (key[index].toInt() xor authenticatedData[index].toInt() xor plaintext[index % plaintext.size].toInt()).toByte()
        }
    }
}

private class FakeSyncSigningKey(
    override val publicKey: PublicSigningKey,
    private val onClose: () -> Unit,
) : SyncSigningKey {
    private var isClosed = false

    override fun sign(message: ByteArray): ByteArray? {
        return if (isClosed) null else fakeSignature(publicKey, message)
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            onClose()
        }
    }
}

private fun fakeSignature(
    publicKey: PublicSigningKey,
    message: ByteArray,
): ByteArray {
    val keyBytes = publicKey.copyBytes()
    return ByteArray(SyncFormatLimits.SIGNATURE_BYTES) { index ->
        (keyBytes[index % keyBytes.size].toInt() xor message[index % message.size].toInt()).toByte()
    }
}
