package app.posato.feature.sync.data

import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.PublicSigningKey
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.TransportKey
import app.posato.feature.sync.domain.WorkspaceId

class IosFormatOneCryptoVerifier(
    cryptoProvider: IosCryptoProvider,
) {
    private val provider = IosSyncCryptoProvider(cryptoProvider)

    fun verify(): Boolean {
        val transportKey = checkNotNull(TransportKey.fromBytes(ByteArray(SyncFormatLimits.TRANSPORT_KEY_BYTES) { it.toByte() }))
        return try {
            val operation = SyncOperation(
                operationId = BundleId(identifier(100)),
                context = SyncContext(
                    WorkspaceId(identifier(1)),
                    TransportEpochId(identifier(2)),
                    KeyEpochId(identifier(3)),
                ),
                authorId = AuthorId(identifier(101)),
                publicSigningKey = checkNotNull(PublicSigningKey.fromBytes(hex(GOLDEN_PUBLIC_KEY))),
                authorSequence = 1,
                clock = HybridLogicalClock(1_000, 2),
                payload = SyncOperationPayload.AuthorRegister,
            )
            val decoded = EncryptedBundleCodec(provider).decode(
                checkNotNull(EncryptedBundle.fromBytes(hex(GOLDEN_BUNDLE))),
                operation.context,
                transportKey,
            )

            decoded is DecodeBundleResult.Success && decoded.operation == operation
        } finally {
            transportKey.close()
        }
    }

    private fun identifier(value: Int): SyncIdentifier {
        val bytes = ByteArray(SyncFormatLimits.IDENTIFIER_BYTES)
        bytes[UUID_VERSION_INDEX] = UUID_VERSION_FOUR
        bytes[UUID_VARIANT_INDEX] = UUID_RFC_VARIANT.toByte()
        bytes[UUID_VALUE_INDEX] = value.toByte()

        return checkNotNull(SyncIdentifier.fromUuidV4Bytes(bytes))
    }

    private fun hex(value: String): ByteArray = value
        .chunked(HEX_CHARACTERS_PER_BYTE)
        .map { it.toInt(HEX_RADIX).toByte() }
        .toByteArray()
}

private const val UUID_VERSION_INDEX = 6
private const val UUID_VARIANT_INDEX = 8
private const val UUID_VALUE_INDEX = 15
private const val UUID_VERSION_FOUR: Byte = 0x40
private const val UUID_RFC_VARIANT = 0x80
private const val HEX_CHARACTERS_PER_BYTE = 2
private const val HEX_RADIX = 16
private const val GOLDEN_PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
private const val GOLDEN_BUNDLE = "50534531000100010000000000004000800000000000006400000000000040008000000000000001" +
    "00000000000040008000000000000002000000000000400080000000000000032021222324252627" +
    "28292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f00000099a53a393e42b32330bd4e06c" +
    "467e215e61c3897d95c3361fc9827a08fbfa3f211bff6514a765e426ad482b77423d7a769a1f10a" +
    "2d40a5a8c2288996f423eb906fbcff947f770d9398fba0450aa141f50353dd8604e932d5fbb98841" +
    "a39f9b27e92e29eb6a065e26179cba89af5cd4391471854f01b28cd2f62c531513bffca3858266f7" +
    "dc0aea5a1193f93317afa4c48fc07dca67ce55a81be0cfca18d2aea339ae43dba4dc6bad6bd46bd3" +
    "e88617c16362289efb2088b59fd4041a4d049bca7241d764ef499c25c11ad37cfdfa27c71e9b8527" +
    "8b488c9b680e"
