package app.posato.feature.sync.data

import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.TransportKey
import app.posato.feature.sync.domain.WorkspaceId

private val envelopeMagic = "PSE1".encodeToByteArray()
private val bundleKeyDomain = "app.posato.sync.bundle-key.v1".encodeToByteArray()
private val signatureDomain = "app.posato.sync.signature.v1".encodeToByteArray()
private val implicitNonce = ByteArray(SyncFormatLimits.AES_NONCE_BYTES)
private const val ENVELOPE_FORMAT = 1
private const val ALGORITHM_SUITE = 1

internal data class BundleHeader(
    val bundleId: BundleId,
    val context: SyncContext,
    val salt: ByteArray,
    val ciphertextLength: Int,
) {
    init {
        require(salt.size == SyncFormatLimits.BUNDLE_SALT_BYTES)
    }

    override fun equals(other: Any?): Boolean {
        return other is BundleHeader &&
            bundleId == other.bundleId &&
            context == other.context &&
            salt.contentEquals(other.salt) &&
            ciphertextLength == other.ciphertextLength
    }

    override fun hashCode(): Int {
        return 31 * (31 * (31 * bundleId.hashCode() + context.hashCode()) + salt.contentHashCode()) + ciphertextLength
    }

    override fun toString(): String {
        return "BundleHeader(redacted)"
    }
}

internal sealed interface PrepareBundleResult {
    data class Success(
        val bundle: EncryptedBundle,
    ) : PrepareBundleResult

    data object InvalidOperation : PrepareBundleResult

    data object CryptographyFailure : PrepareBundleResult
}

internal enum class RemoteBundleFailure {
    UNSUPPORTED_VERSION,
    MALFORMED,
    OVERSIZED,
    WRONG_CONTEXT,
    INVALID_SIGNATURE,
    AUTHENTICATION_FAILED,
    INVALID_OPERATION,
}

internal sealed interface DecodeBundleResult {
    data class Success(
        val operation: SyncOperation,
        val header: BundleHeader,
    ) : DecodeBundleResult

    data class Failure(
        val reason: RemoteBundleFailure,
    ) : DecodeBundleResult
}

internal sealed interface InspectBundleHeaderResult {
    data class Success(
        val header: BundleHeader,
    ) : InspectBundleHeaderResult

    data class Failure(
        val reason: RemoteBundleFailure,
    ) : InspectBundleHeaderResult
}

internal class EncryptedBundleCodec(
    private val cryptoProvider: SyncCryptoProvider,
) {
    fun inspectHeader(bundle: EncryptedBundle): InspectBundleHeaderResult {
        val completeBytes = bundle.copyBytes()

        return when (val result = decodeHeader(completeBytes)) {
            is HeaderDecodeResult.Success -> InspectBundleHeaderResult.Success(result.header)
            is HeaderDecodeResult.Failure -> InspectBundleHeaderResult.Failure(result.reason)
        }
    }

    fun prepare(
        operation: SyncOperation,
        transportKey: TransportKey,
        signingKey: SyncSigningKey,
        salt: ByteArray,
    ): PrepareBundleResult {
        val validIdentity = salt.size == SyncFormatLimits.BUNDLE_SALT_BYTES && signingKey.publicKey == operation.publicSigningKey
        val plaintext = operation.takeIf { validIdentity }?.let(SyncOperationCodec::encode)
        val header = plaintext?.let { encoded ->
            BundleHeader(operation.operationId, operation.context, salt.copyOf(), encoded.size + SyncFormatLimits.AES_TAG_BYTES)
        }
        val headerBytes = header?.let(::encodeHeader)
        val key = header?.let { deriveBundleKey(transportKey, it) }
        val ciphertextAndTag = seal(key, headerBytes, plaintext)
        val validCiphertext = ciphertextAndTag?.takeIf { encrypted -> encrypted.size == header?.ciphertextLength }
        val signature = sign(signingKey, headerBytes, validCiphertext)
        val completeBytes = if (headerBytes != null && validCiphertext != null && signature != null) {
            headerBytes + validCiphertext + signature
        } else {
            null
        }

        return when {
            !validIdentity || plaintext == null -> PrepareBundleResult.InvalidOperation

            completeBytes == null -> PrepareBundleResult.CryptographyFailure

            else -> EncryptedBundle.fromBytes(completeBytes)
                ?.let { bundle -> PrepareBundleResult.Success(bundle) }
                ?: PrepareBundleResult.InvalidOperation
        }
    }

    fun decode(
        bundle: EncryptedBundle,
        expectedContext: SyncContext,
        transportKey: TransportKey,
    ): DecodeBundleResult {
        val completeBytes = bundle.copyBytes()
        val headerResult = decodeHeader(completeBytes)
        val header = (headerResult as? HeaderDecodeResult.Success)?.header
        val headerFailure = (headerResult as? HeaderDecodeResult.Failure)?.reason
        val wrongContext = header != null && header.context != expectedContext
        val expectedSize = header?.let { SyncFormatLimits.HEADER_BYTES + it.ciphertextLength + SyncFormatLimits.SIGNATURE_BYTES }
        val malformedSize = expectedSize != null && completeBytes.size != expectedSize
        val parts = header?.takeUnless { wrongContext || malformedSize }?.let { splitBundle(completeBytes, it) }
        val key = header?.takeIf { parts != null }?.let { deriveBundleKey(transportKey, it) }
        val plaintext = open(key, parts)
        val operation = decodePlaintext(plaintext)
        val validOperation = operation?.takeIf { it.operationId == header?.bundleId && it.context == header.context }
        val signatureIsValid = verifySignature(validOperation, parts)

        return decodedResult(
            headerFailure,
            wrongContext,
            malformedSize,
            plaintext != null,
            validOperation,
            header,
            signatureIsValid,
        )
    }

    fun encodeHeader(header: BundleHeader): ByteArray {
        val writer = CanonicalWriter(SyncFormatLimits.HEADER_BYTES)
        writer.writeBytes(envelopeMagic)
        writer.writeU16(ENVELOPE_FORMAT)
        writer.writeU16(ALGORITHM_SUITE)
        writer.writeBytes(header.bundleId.value.copyBytes())
        writer.writeBytes(header.context.workspaceId.value.copyBytes())
        writer.writeBytes(header.context.transportEpochId.value.copyBytes())
        writer.writeBytes(header.context.keyEpochId.value.copyBytes())
        writer.writeBytes(header.salt)
        writer.writeU32(header.ciphertextLength.toLong())

        return writer.toByteArray()
    }

    private fun deriveBundleKey(
        transportKey: TransportKey,
        header: BundleHeader
    ): ByteArray? {
        val info = bundleKeyDomain +
            header.context.workspaceId.value.copyBytes() +
            header.context.transportEpochId.value.copyBytes() +
            header.context.keyEpochId.value.copyBytes() +
            header.bundleId.value.copyBytes()

        return transportKey.useBytes { keyBytes ->
            cryptoProvider.hkdfSha256(keyBytes, header.salt, info) ?: ByteArray(0)
        }.takeIf { key -> key.size == SyncFormatLimits.AES_KEY_BYTES }
    }

    private fun seal(
        key: ByteArray?,
        header: ByteArray?,
        plaintext: ByteArray?
    ): ByteArray? {
        if (key == null || header == null || plaintext == null) return null
        return try {
            cryptoProvider.sealAesGcm(key, implicitNonce, header, plaintext)
        } catch (_: Exception) {
            null
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
    }

    private fun sign(
        signingKey: SyncSigningKey,
        header: ByteArray?,
        ciphertext: ByteArray?
    ): ByteArray? {
        if (header == null || ciphertext == null) return null
        return try {
            signingKey.sign(signaturePreimage(header, ciphertext))
        } catch (_: Exception) {
            null
        }?.takeIf { it.size == SyncFormatLimits.SIGNATURE_BYTES }
    }

    private fun open(
        key: ByteArray?,
        parts: BundleParts?
    ): ByteArray? {
        if (key == null || parts == null) return null
        return try {
            cryptoProvider.openAesGcm(key, implicitNonce, parts.header, parts.ciphertextAndTag)
        } catch (_: Exception) {
            null
        } finally {
            key.fill(0)
        }
    }

    private fun decodePlaintext(plaintext: ByteArray?): SyncOperation? {
        return plaintext?.let { decrypted ->
            try {
                SyncOperationCodec.decode(decrypted)
            } finally {
                decrypted.fill(0)
            }
        }
    }

    private fun verifySignature(
        operation: SyncOperation?,
        parts: BundleParts?
    ): Boolean {
        if (operation == null || parts == null) return false
        return try {
            cryptoProvider.verifyEd25519(
                operation.publicSigningKey,
                signaturePreimage(parts.header, parts.ciphertextAndTag),
                parts.signature,
            )
        } catch (_: Exception) {
            false
        }
    }
}

private fun decodedResult(
    headerFailure: RemoteBundleFailure?,
    wrongContext: Boolean,
    malformedSize: Boolean,
    authenticated: Boolean,
    operation: SyncOperation?,
    header: BundleHeader?,
    signatureIsValid: Boolean,
): DecodeBundleResult = when {
    headerFailure != null -> DecodeBundleResult.Failure(headerFailure)
    wrongContext -> DecodeBundleResult.Failure(RemoteBundleFailure.WRONG_CONTEXT)
    malformedSize -> DecodeBundleResult.Failure(RemoteBundleFailure.MALFORMED)
    !authenticated -> DecodeBundleResult.Failure(RemoteBundleFailure.AUTHENTICATION_FAILED)
    operation == null -> DecodeBundleResult.Failure(RemoteBundleFailure.INVALID_OPERATION)
    !signatureIsValid -> DecodeBundleResult.Failure(RemoteBundleFailure.INVALID_SIGNATURE)
    else -> DecodeBundleResult.Success(operation, checkNotNull(header))
}

private data class BundleParts(
    val header: ByteArray,
    val ciphertextAndTag: ByteArray,
    val signature: ByteArray
)

private fun splitBundle(
    bytes: ByteArray,
    header: BundleHeader
): BundleParts {
    val ciphertextEnd = SyncFormatLimits.HEADER_BYTES + header.ciphertextLength
    return BundleParts(
        bytes.copyOfRange(0, SyncFormatLimits.HEADER_BYTES),
        bytes.copyOfRange(SyncFormatLimits.HEADER_BYTES, ciphertextEnd),
        bytes.copyOfRange(ciphertextEnd, bytes.size),
    )
}

private sealed interface HeaderDecodeResult {
    data class Success(
        val header: BundleHeader,
    ) : HeaderDecodeResult

    data class Failure(
        val reason: RemoteBundleFailure,
    ) : HeaderDecodeResult
}

private fun decodeHeader(completeBytes: ByteArray): HeaderDecodeResult {
    val reader = completeBytes
        .takeIf { it.size >= SyncFormatLimits.HEADER_BYTES }
        ?.copyOfRange(0, SyncFormatLimits.HEADER_BYTES)
        ?.let(::CanonicalReader)
    val validMagic = reader?.readBytes(envelopeMagic.size)?.contentEquals(envelopeMagic) == true
    val format = reader?.readU16()
    val suite = reader?.readU16()
    val bundleId = reader?.readHeaderIdentifier()?.let(::BundleId)
    val workspaceId = reader?.readHeaderIdentifier()?.let(::WorkspaceId)
    val transportEpochId = reader?.readHeaderIdentifier()?.let(::TransportEpochId)
    val keyEpochId = reader?.readHeaderIdentifier()?.let(::KeyEpochId)
    val salt = reader?.readBytes(SyncFormatLimits.BUNDLE_SALT_BYTES)
    val ciphertextLength = reader?.readU32()
        ?.takeIf { length ->
            length in
                SyncFormatLimits.AES_TAG_BYTES.toLong()..(SyncFormatLimits.PLAINTEXT_BYTES + SyncFormatLimits.AES_TAG_BYTES).toLong()
        }
        ?.toInt()
    val supported = format == ENVELOPE_FORMAT && suite == ALGORITHM_SUITE
    val fields = listOf(bundleId, workspaceId, transportEpochId, keyEpochId, salt, ciphertextLength)
    val complete = validMagic && fields.all { it != null } && reader.remaining == 0

    return when {
        validMagic && format != null && suite != null && !supported -> {
            HeaderDecodeResult.Failure(RemoteBundleFailure.UNSUPPORTED_VERSION)
        }

        !complete -> {
            HeaderDecodeResult.Failure(RemoteBundleFailure.MALFORMED)
        }

        else -> {
            HeaderDecodeResult.Success(
                BundleHeader(
                    checkNotNull(bundleId),
                    SyncContext(checkNotNull(workspaceId), checkNotNull(transportEpochId), checkNotNull(keyEpochId)),
                    checkNotNull(salt),
                    checkNotNull(ciphertextLength),
                ),
            )
        }
    }
}

private fun CanonicalReader.readHeaderIdentifier(): SyncIdentifier? {
    return readBytes(SyncFormatLimits.IDENTIFIER_BYTES)?.let(SyncIdentifier::fromUuidV4Bytes)
}

private fun signaturePreimage(
    header: ByteArray,
    ciphertextAndTag: ByteArray
): ByteArray {
    val writer = CanonicalWriter(signatureDomain.size + 8 + header.size + ciphertextAndTag.size)
    writer.writeBytes(signatureDomain)
    writer.writeU32(header.size.toLong())
    writer.writeBytes(header)
    writer.writeU32(ciphertextAndTag.size.toLong())
    writer.writeBytes(ciphertextAndTag)

    return writer.toByteArray()
}
