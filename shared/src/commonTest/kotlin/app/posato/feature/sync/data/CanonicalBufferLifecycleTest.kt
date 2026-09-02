package app.posato.feature.sync.data

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncReducer
import app.posato.feature.sync.domain.TransportKey
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.sync.testPublicKey
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanonicalBufferLifecycleTest {
    @Test
    fun `given successful projection hashing when digest completes then its plaintext preimage is cleared`() {
        val provider = CapturingSha256Provider { ByteArray(HASH_BYTES) { 1 } }

        assertIs<ImmutableBytes>(projection().canonicalDigest(provider))

        provider.assertCapturedMessageCleared()
    }

    @Test
    fun `given rejected projection hashing when digest completes then its plaintext preimage is cleared`() {
        val provider = CapturingSha256Provider { null }

        assertNull(projection().canonicalDigest(provider))

        provider.assertCapturedMessageCleared()
    }

    @Test
    fun `given failing projection hashing when digest throws then its plaintext preimage is cleared`() {
        val provider = CapturingSha256Provider { throw ExpectedCryptoException() }

        assertFailsWith<ExpectedCryptoException> { projection().canonicalDigest(provider) }

        provider.assertCapturedMessageCleared()
    }

    @Test
    fun `given failing signing when bundle preparation returns then its signature preimage is cleared`() {
        val provider = FakeSyncCryptoProvider()
        val signingKey = CapturingFailingSigningKey()
        val transportKey = transportKey()

        try {
            assertIs<PrepareBundleResult.CryptographyFailure>(
                EncryptedBundleCodec(provider).prepare(
                    operation = testOperation(20, 1, SyncOperationPayload.AuthorRegister),
                    transportKey = transportKey,
                    signingKey = signingKey,
                    salt = ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { 4 },
                ),
            )
        } finally {
            transportKey.close()
        }

        signingKey.assertCapturedMessageCleared()
    }

    @Test
    fun `given successful bundle preparation when bytes are retained then owned components are cleared`() {
        val delegate = FakeSyncCryptoProvider()
        val provider = CapturingPrepareProvider(delegate)
        val signingKey = CapturingSigningKey(checkNotNull(delegate.createSigningKey()))
        val transportKey = transportKey()
        val salt = ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { 4 }

        try {
            assertIs<PrepareBundleResult.Success>(
                EncryptedBundleCodec(provider).prepare(
                    operation = testOperation(20, 1, SyncOperationPayload.AuthorRegister),
                    transportKey = transportKey,
                    signingKey = signingKey,
                    salt = salt,
                ),
            )
        } finally {
            signingKey.close()
            transportKey.close()
        }

        provider.assertCapturedBuffersCleared()
        signingKey.assertCapturedSignatureCleared()
        assertContentEquals(ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { 4 }, salt)
    }

    @Test
    fun `given valid signature when bundle decoding completes then its signature preimage is cleared`() {
        val delegate = FakeSyncCryptoProvider()
        val provider = CapturingVerifyProvider(delegate)
        val signingKey = checkNotNull(delegate.createSigningKey())
        val transportKey = transportKey()
        val bundle = try {
            prepareBundle(delegate, signingKey, transportKey)
        } finally {
            signingKey.close()
        }

        try {
            assertIs<DecodeBundleResult.Success>(
                EncryptedBundleCodec(provider).decode(bundle, testContext, transportKey),
            )
        } finally {
            transportKey.close()
        }

        provider.assertCapturedMessageCleared()
    }
}

private fun projection() = SyncReducer.reduce(
    listOf(
        testOperation(1, 1, SyncOperationPayload.AuthorRegister),
        testOperation(
            2,
            2,
            SyncOperationPayload.DomainPresent(checkNotNull(ExactDomain.restore("focus.example"))),
        ),
        testOperation(
            3,
            3,
            SyncOperationPayload.ApplicationPolicyPresent(
                checkNotNull(ApplicationPolicyName.restore("Focused work")),
            ),
        ),
        testOperation(
            4,
            4,
            SyncOperationPayload.SessionStart(SessionId(testIdentifier(80)), 1_000, 2_000),
        ),
    ),
)

private fun transportKey(): TransportKey {
    return checkNotNull(TransportKey.fromBytes(ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES) { 3 }))
}

private fun prepareBundle(
    provider: SyncCryptoProvider,
    signingKey: SyncSigningKey,
    transportKey: TransportKey,
): EncryptedBundle {
    val prepared = EncryptedBundleCodec(provider).prepare(
        operation = testOperation(20, 1, SyncOperationPayload.AuthorRegister),
        transportKey = transportKey,
        signingKey = signingKey,
        salt = ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { 4 },
    )

    return assertIs<PrepareBundleResult.Success>(prepared).bundle
}

private class CapturingSha256Provider(
    private val hash: (ByteArray) -> ByteArray?,
) : SyncCryptoProvider by FakeSyncCryptoProvider() {
    private lateinit var capturedMessage: ByteArray

    override fun sha256(message: ByteArray): ByteArray? {
        assertTrue(message.any { byte -> byte != 0.toByte() })
        capturedMessage = message

        return hash(message)
    }

    fun assertCapturedMessageCleared() {
        assertTrue(capturedMessage.all { byte -> byte == 0.toByte() })
    }
}

private class CapturingFailingSigningKey : SyncSigningKey {
    override val publicKey = testPublicKey
    private lateinit var capturedMessage: ByteArray

    override fun sign(message: ByteArray): ByteArray? {
        assertTrue(message.any { byte -> byte != 0.toByte() })
        capturedMessage = message
        throw ExpectedCryptoException()
    }

    override fun close() = Unit

    fun assertCapturedMessageCleared() {
        assertTrue(capturedMessage.all { byte -> byte == 0.toByte() })
    }
}

private class CapturingVerifyProvider(
    private val delegate: SyncCryptoProvider,
) : SyncCryptoProvider by delegate {
    private lateinit var capturedMessage: ByteArray

    override fun verifyEd25519(
        publicKey: PublicSigningKey,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        assertTrue(message.any { byte -> byte != 0.toByte() })
        capturedMessage = message

        return delegate.verifyEd25519(publicKey, message, signature)
    }

    fun assertCapturedMessageCleared() {
        assertTrue(capturedMessage.all { byte -> byte == 0.toByte() })
    }
}

private class CapturingPrepareProvider(
    private val delegate: SyncCryptoProvider,
) : SyncCryptoProvider by delegate {
    private lateinit var capturedHeader: ByteArray
    private lateinit var capturedCiphertext: ByteArray
    private lateinit var capturedSalt: ByteArray

    override fun hmacSha256(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray? {
        if (!::capturedSalt.isInitialized) {
            capturedSalt = key
        }

        return delegate.hmacSha256(key, message)
    }

    override fun sealAesGcm(
        key: ByteArray,
        nonce: ByteArray,
        authenticatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray? {
        capturedHeader = authenticatedData
        capturedCiphertext = checkNotNull(delegate.sealAesGcm(key, nonce, authenticatedData, plaintext))

        return capturedCiphertext
    }

    fun assertCapturedBuffersCleared() {
        assertTrue(capturedHeader.all { byte -> byte == 0.toByte() })
        assertTrue(capturedCiphertext.all { byte -> byte == 0.toByte() })
        assertTrue(capturedSalt.all { byte -> byte == 0.toByte() })
    }
}

private class CapturingSigningKey(
    private val delegate: SyncSigningKey,
) : SyncSigningKey by delegate {
    private lateinit var capturedSignature: ByteArray

    override fun sign(message: ByteArray): ByteArray? {
        capturedSignature = checkNotNull(delegate.sign(message))

        return capturedSignature
    }

    fun assertCapturedSignatureCleared() {
        assertTrue(capturedSignature.all { byte -> byte == 0.toByte() })
    }
}

private class ExpectedCryptoException : RuntimeException()

private const val HASH_BYTES = 32
