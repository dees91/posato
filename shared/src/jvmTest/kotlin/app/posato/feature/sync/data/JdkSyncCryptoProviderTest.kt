package app.posato.feature.sync.data

import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.TransportKey
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import java.security.KeyFactory
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class JdkSyncCryptoProviderTest {
    @Test
    fun `given a secure random provider failure when generating bytes then null is returned`() {
        val secureRandom = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) {
                throw ProviderException("synthetic provider failure")
            }
        }

        assertNull(JdkSyncCryptoProvider(secureRandom).randomBytes(32))
    }

    @Test
    fun `given the shared format one fixture when encoded then every cryptographic boundary matches`() {
        val provider = JdkSyncCryptoProvider()
        val signingKey = FixedSigningKey()
        val operation = SyncOperation(
            BundleId(testIdentifier(100)),
            testContext,
            AuthorId(testIdentifier(101)),
            signingKey.publicKey,
            1,
            HybridLogicalClock(1_000, 2),
            SyncOperationPayload.AuthorRegister,
        )
        val transportKey = checkNotNull(TransportKey.fromBytes(ByteArray(32) { it.toByte() }))
        val prepared = assertIs<PrepareBundleResult.Success>(
            EncryptedBundleCodec(provider).prepare(operation, transportKey, signingKey, ByteArray(32) { (it + 32).toByte() }),
        )
        val info = "app.posato.sync.bundle-key.v1".encodeToByteArray() +
            testContext.workspaceId.value.copyBytes() + testContext.transportEpochId.value.copyBytes() +
            testContext.keyEpochId.value.copyBytes() + operation.operationId.value.copyBytes()
        val expectedBundle = hex(GOLDEN_BUNDLE)

        assertContentEquals(hex(GOLDEN_PLAINTEXT), SyncOperationCodec.encode(operation))
        assertContentEquals(hex(GOLDEN_DERIVED_KEY), provider.hkdfSha256(ByteArray(32) { it.toByte() }, hex(GOLDEN_SALT), info))
        assertContentEquals(expectedBundle, prepared.bundle.copyBytes())
        assertContentEquals(
            expectedBundle.copyOfRange(0, SyncFormatLimits.HEADER_BYTES),
            prepared.bundle.copyBytes().copyOfRange(0, SyncFormatLimits.HEADER_BYTES),
        )
        assertEquals(
            operation,
            assertIs<DecodeBundleResult.Success>(
                EncryptedBundleCodec(provider).decode(prepared.bundle, testContext, transportKey),
            ).operation,
        )
    }

    @Test
    fun `given an RFC 5869 case when deriving a bundle key then the expected output is produced`() {
        val provider = JdkSyncCryptoProvider()
        val inputKeyMaterial = ByteArray(22) { 0x0B }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")

        val output = provider.hkdfSha256(inputKeyMaterial, salt, info)

        assertContentEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf"), output)
    }

    @Test
    fun `given a format one operation when sealed and decoded then JCA preserves canonical bytes and signature`() {
        val provider = JdkSyncCryptoProvider()
        val signingKey = checkNotNull(provider.createSigningKey())
        val operation = SyncOperation(
            operationId = BundleId(testIdentifier(100)),
            context = testContext,
            authorId = AuthorId(testIdentifier(101)),
            publicSigningKey = signingKey.publicKey,
            authorSequence = 1,
            clock = HybridLogicalClock(1_000, 2),
            payload = SyncOperationPayload.AuthorRegister,
        )
        val transportKey = checkNotNull(TransportKey.fromBytes(ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES) { it.toByte() }))
        val codec = EncryptedBundleCodec(provider)

        val prepared = assertIs<PrepareBundleResult.Success>(
            codec.prepare(operation, transportKey, signingKey, ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { (it + 32).toByte() }),
        )
        val decoded = assertIs<DecodeBundleResult.Success>(codec.decode(prepared.bundle, testContext, transportKey))

        assertEquals(SyncFormatLimits.HEADER_BYTES, codec.encodeHeader(decoded.header).size)
        assertEquals(operation, decoded.operation)

        val tampered = prepared.bundle.copyBytes().also { bytes -> bytes[SyncFormatLimits.HEADER_BYTES] = (bytes[108].toInt() xor 1).toByte() }
        val failure = assertIs<DecodeBundleResult.Failure>(
            codec.decode(checkNotNull(EncryptedBundle.fromBytes(tampered)), testContext, transportKey),
        )
        assertNotEquals(RemoteBundleFailure.INVALID_OPERATION, failure.reason)
        signingKey.close()
        transportKey.close()
    }

    @Test
    fun `given an Ed25519 key when signing then the raw public key verifies and altered input fails`() {
        val provider = JdkSyncCryptoProvider()
        val signingKey = checkNotNull(provider.createSigningKey())
        val message = "signed message".encodeToByteArray()
        val signature = checkNotNull(signingKey.sign(message))

        assertEquals(SyncFormatLimits.PUBLIC_KEY_BYTES, signingKey.publicKey.copyBytes().size)
        assertEquals(SyncFormatLimits.SIGNATURE_BYTES, signature.size)
        assertEquals(true, provider.verifyEd25519(signingKey.publicKey, message, signature))
        assertEquals(false, provider.verifyEd25519(signingKey.publicKey, message + 0, signature))
        signingKey.close()
    }

    @Test
    fun `given the RFC 8032 fixture when signed then JCA matches the CryptoKit raw representation`() {
        val signingKey = FixedSigningKey()
        val expectedSignature = hex(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
        )

        assertContentEquals(expectedSignature, signingKey.sign(byteArrayOf()))
        assertEquals(true, JdkSyncCryptoProvider().verifyEd25519(signingKey.publicKey, byteArrayOf(), expectedSignature))
    }

    @Test
    fun `given wrong context key signature or framing when decoded then the typed failure preserves format boundaries`() {
        val provider = JdkSyncCryptoProvider()
        val signingKey = checkNotNull(provider.createSigningKey())
        val operation = SyncOperation(
            operationId = BundleId(testIdentifier(110)),
            context = testContext,
            authorId = AuthorId(testIdentifier(111)),
            publicSigningKey = signingKey.publicKey,
            authorSequence = 1,
            clock = HybridLogicalClock(10, 0),
            payload = SyncOperationPayload.AuthorRegister,
        )
        val transportKey = checkNotNull(TransportKey.fromBytes(ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES) { 1 }))
        val wrongKey = checkNotNull(TransportKey.fromBytes(ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES) { 2 }))
        val codec = EncryptedBundleCodec(provider)
        val bundle = assertIs<PrepareBundleResult.Success>(
            codec.prepare(operation, transportKey, signingKey, ByteArray(SyncFormatLimits.BUNDLE_SALT_BYTES) { 3 }),
        ).bundle
        val bytes = bundle.copyBytes()
        val wrongContext = testContext.copy(workspaceId = WorkspaceId(testIdentifier(112)))
        val alteredSignature = checkNotNull(
            EncryptedBundle.fromBytes(bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }),
        )

        assertEquals(RemoteBundleFailure.WRONG_CONTEXT, assertFailure(codec.decode(bundle, wrongContext, transportKey)))
        assertEquals(RemoteBundleFailure.AUTHENTICATION_FAILED, assertFailure(codec.decode(bundle, testContext, wrongKey)))
        assertEquals(RemoteBundleFailure.INVALID_SIGNATURE, assertFailure(codec.decode(alteredSignature, testContext, transportKey)))
        assertEquals(
            RemoteBundleFailure.MALFORMED,
            assertFailure(
                codec.decode(checkNotNull(EncryptedBundle.fromBytes(bytes.dropLast(1).toByteArray())), testContext, transportKey),
            ),
        )
        assertEquals(
            RemoteBundleFailure.MALFORMED,
            assertFailure(codec.decode(checkNotNull(EncryptedBundle.fromBytes(bytes + 0)), testContext, transportKey)),
        )
        signingKey.close()
        transportKey.close()
        wrongKey.close()
    }
}

private class FixedSigningKey : SyncSigningKey {
    override val publicKey = checkNotNull(
        app.posato.feature.sync.domain.PublicSigningKey.fromBytes(
            hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
        ),
    )
    private val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(
        PKCS8EncodedKeySpec(hex("302e020100300506032b6570042204209d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")),
    )

    override fun sign(message: ByteArray): ByteArray {
        return Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(message)
            sign()
        }
    }

    override fun close() = Unit
}

private const val GOLDEN_SALT = "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f"
private const val GOLDEN_DERIVED_KEY = "aa3441bb28b450359ff32b31776874e526e174e72208e0662300cea16de8e420"
private const val GOLDEN_PLAINTEXT = "50534f3100010000000000004000800000000000006400000000000040008000000000000001" +
    "00000000000040008000000000000002000000000000400080000000000000030000000000004000" +
    "8000000000000065d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a" +
    "000000000000000100000000000003e8000201"
private const val GOLDEN_BUNDLE = "50534531000100010000000000004000800000000000006400000000000040008000000000000001" +
    "00000000000040008000000000000002000000000000400080000000000000032021222324252627" +
    "28292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f00000099a53a393e42b32330bd4e06c" +
    "467e215e61c3897d95c3361fc9827a08fbfa3f211bff6514a765e426ad482b77423d7a769a1f10a" +
    "2d40a5a8c2288996f423eb906fbcff947f770d9398fba0450aa141f50353dd8604e932d5fbb98841" +
    "a39f9b27e92e29eb6a065e26179cba89af5cd4391471854f01b28cd2f62c531513bffca3858266f7" +
    "dc0aea5a1193f93317afa4c48fc07dca67ce55a81be0cfca18d2aea339ae43dba4dc6bad6bd46bd3" +
    "e88617c16362289efb2088b59fd4041a4d049bca7241d764ef499c25c11ad37cfdfa27c71e9b8527" +
    "8b488c9b680e"

private fun assertFailure(result: DecodeBundleResult): RemoteBundleFailure {
    return assertIs<DecodeBundleResult.Failure>(result).reason
}

private fun hex(value: String): ByteArray {
    return value.chunked(2).map { byte -> byte.toInt(16).toByte() }.toByteArray()
}
