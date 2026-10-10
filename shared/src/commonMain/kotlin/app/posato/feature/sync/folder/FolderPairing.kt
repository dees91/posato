package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.BootstrapEncoding
import app.posato.feature.sync.bootstrap.KEYCHAIN_ITEM_BYTES
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemCreateResult
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.bootstrap.WorkspaceKeyItem
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.hkdfSha256
import app.posato.feature.sync.domain.SyncFormatLimits
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val CODE_BYTES: Int = 16
private const val CODE_CHARACTERS: Int = 26
private const val OFFER_VERSION: Int = 1
private const val OFFER_LIFETIME_MILLIS: Long = 10 * 60_000L
private const val OFFER_SUFFIX: String = ".ppair"
private const val MAXIMUM_OFFER_BYTES: Int = 1_024
private const val BASE32: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val CHARACTER_BITS: Int = 5
private const val CHARACTER_MASK: Int = 31
private const val BYTE_MASK: Int = 0xFF
private val offerMagic = "PSP1".encodeToByteArray()
private val pairingSalt = "app.posato.folder.pairing.v1".encodeToByteArray()
private val nameLabel = "offer-name".encodeToByteArray()
private val keyLabel = "offer-key".encodeToByteArray()

/**
 * One-time pairing codes of ADR 0010: a member seals the workspace key item
 * in the folder under a key derived from the code, and a joiner that knows
 * the code and sees the same `workspace` file takes it.
 */
internal class FolderPairing(
    private val ports: FolderSyncPorts,
    private val crypto: SyncCryptoProvider,
    private val files: FolderFileSystem,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) {
    private var currentOffer: String? = null

    suspend fun offer(): PairingOfferResult {
        val source = offerSource() ?: return PairingOfferResult.Unavailable
        val codeBytes = crypto.randomBytes(CODE_BYTES)?.takeIf { it.size == CODE_BYTES } ?: return PairingOfferResult.Unavailable
        return try {
            dismiss()
            sweepExpired(source.workspace)
            val expiresAt = now() + OFFER_LIFETIME_MILLIS
            val file = writeOffer(source.workspace, codeBytes, source.item, expiresAt)
            currentOffer = file ?: currentOffer
            if (file == null) PairingOfferResult.Unavailable else PairingOfferResult.Offered(PairingOffer(encodeCode(codeBytes), expiresAt))
        } finally {
            codeBytes.fill(0)
            source.item.fill(0)
        }
    }

    private suspend fun offerSource(): OfferSource? {
        val binding = (ports.resolveBinding() as? BindingResolution.Available)?.binding ?: return null
        val anchor = (ports.readAnchor(binding) as? AnchorReadResult.Found)?.anchor ?: return null
        val item = (ports.readItem(binding, anchor.account()) as? KeyItemReadResult.Found)?.value ?: return null
        val workspace = ports.workspaceDirectory(binding) ?: return null
        return OfferSource(workspace, item.copyBytes())
    }

    /** Writes the sealed offer and returns its file, or null when it could not be written. */
    private suspend fun writeOffer(
        workspace: String,
        codeBytes: ByteArray,
        itemBytes: ByteArray,
        expiresAt: Long,
    ): String? {
        val derived = derive(codeBytes) ?: return null
        try {
            val nonce = crypto.randomBytes(SyncFormatLimits.AES_NONCE_BYTES) ?: return null
            val header = offerMagic + byteArrayOf(OFFER_VERSION.toByte()) + expiresAt.toBytes() + nonce
            val sealed = crypto.sealAesGcm(derived.key, nonce, header + derived.name, itemBytes) ?: return null
            val file = workspace.child(PAIRING_DIRECTORY).child(derived.name.toHex() + OFFER_SUFFIX)
            val written = withContext(ioDispatcher) { files.writeExclusive(file, header + sealed) }
            return file.takeIf { written == ExclusiveWrite.CREATED }
        } finally {
            derived.key.fill(0)
        }
    }

    suspend fun dismiss() {
        val file = currentOffer ?: return
        withContext(ioDispatcher) { files.delete(file) }
        currentOffer = null
    }

    suspend fun sweepStaleOffers() {
        val binding = (ports.resolveBinding() as? BindingResolution.Available)?.binding ?: return
        val workspace = ports.workspaceDirectory(binding) ?: return
        sweepExpired(workspace)
    }

    suspend fun accept(code: String): PairingAcceptResult {
        val codeBytes = decodeCode(code) ?: return PairingAcceptResult.INVALID_CODE
        val binding = (ports.resolveBinding() as? BindingResolution.Available)?.binding ?: return PairingAcceptResult.UNAVAILABLE
        val anchor = (ports.readAnchor(binding) as? AnchorReadResult.Found)?.anchor ?: return PairingAcceptResult.UNAVAILABLE
        val workspace = ports.workspaceDirectory(binding) ?: return PairingAcceptResult.UNAVAILABLE
        val derived = derive(codeBytes).also { codeBytes.fill(0) } ?: return PairingAcceptResult.UNAVAILABLE
        val file = workspace.child(PAIRING_DIRECTORY).child(derived.name.toHex() + OFFER_SUFFIX)
        val stored = withContext(ioDispatcher) { if (files.isFile(file)) files.read(file, MAXIMUM_OFFER_BYTES) else null }
            ?: return PairingAcceptResult.NOT_FOUND
        val opened = openOffer(stored, derived)
        derived.key.fill(0)
        return when {
            opened == null -> PairingAcceptResult.REFUSED
            opened.expiresAtMillis < now() -> PairingAcceptResult.EXPIRED.also { opened.item.fill(0) }
            else -> store(opened.item, anchor, binding, file)
        }
    }

    private suspend fun store(
        itemBytes: ByteArray,
        anchor: WorkspaceAnchor,
        binding: AccountBinding,
        file: String,
    ): PairingAcceptResult {
        try {
            val item = WorkspaceKeyItem.fromBytes(itemBytes) ?: return PairingAcceptResult.REFUSED
            val decoded = BootstrapEncoding.decodeKeyItem(item) ?: return PairingAcceptResult.REFUSED
            decoded.clear()
            val sameWorkspace = decoded.workspaceId == anchor.workspaceId &&
                decoded.transportEpochId == anchor.transportEpochId &&
                decoded.keyEpochId == anchor.keyEpochId
            if (!sameWorkspace) return PairingAcceptResult.WRONG_WORKSPACE
            return when (ports.createItem(binding, anchor.account(), item)) {
                KeyItemCreateResult.Created, KeyItemCreateResult.AlreadyExists -> {
                    withContext(ioDispatcher) { files.delete(file) }
                    PairingAcceptResult.JOINED
                }

                else -> {
                    PairingAcceptResult.UNAVAILABLE
                }
            }
        } finally {
            itemBytes.fill(0)
        }
    }

    private fun openOffer(
        stored: ByteArray,
        derived: DerivedOffer,
    ): OpenedOffer? {
        val headerSize = offerMagic.size + 1 + Long.SIZE_BYTES + SyncFormatLimits.AES_NONCE_BYTES
        if (stored.size != headerSize + KEYCHAIN_ITEM_BYTES + SyncFormatLimits.AES_TAG_BYTES) return null
        val header = stored.copyOf(headerSize)
        if (!header.copyOf(offerMagic.size).contentEquals(offerMagic) || header[offerMagic.size].toInt() != OFFER_VERSION) return null
        val expiresAt = header.copyOfRange(offerMagic.size + 1, offerMagic.size + 1 + Long.SIZE_BYTES).toLong()
        val nonce = header.copyOfRange(headerSize - SyncFormatLimits.AES_NONCE_BYTES, headerSize)
        val item = crypto.openAesGcm(derived.key, nonce, header + derived.name, stored.copyOfRange(headerSize, stored.size)) ?: return null
        return OpenedOffer(item, expiresAt)
    }

    private suspend fun sweepExpired(workspace: String) {
        withContext(ioDispatcher) {
            val directory = workspace.child(PAIRING_DIRECTORY)
            files.names(directory).orEmpty().filter { it.endsWith(OFFER_SUFFIX) }.forEach { name ->
                val bytes = files.read(directory.child(name), MAXIMUM_OFFER_BYTES)
                val expiresAt = bytes?.takeIf { it.size >= offerMagic.size + 1 + Long.SIZE_BYTES }
                    ?.copyOfRange(offerMagic.size + 1, offerMagic.size + 1 + Long.SIZE_BYTES)?.toLong()
                if (expiresAt == null || expiresAt < now()) files.delete(directory.child(name))
            }
        }
    }

    private fun derive(codeBytes: ByteArray): DerivedOffer? {
        val name = crypto.hkdfSha256(codeBytes, pairingSalt, nameLabel)?.copyOf(SyncFormatLimits.IDENTIFIER_BYTES) ?: return null
        val key = crypto.hkdfSha256(codeBytes, pairingSalt, keyLabel) ?: return null
        return DerivedOffer(name, key)
    }

    private class DerivedOffer(
        val name: ByteArray,
        val key: ByteArray,
    )

    private class OpenedOffer(
        val item: ByteArray,
        val expiresAtMillis: Long,
    )

    private class OfferSource(
        val workspace: String,
        val item: ByteArray,
    )
}

private fun WorkspaceAnchor.account(): KeyAccount {
    return checkNotNull(KeyAccount.fromText(BootstrapEncoding.identifierToAccountText(workspaceId.value)))
}

internal fun encodeCode(bytes: ByteArray): String {
    val builder = StringBuilder()
    var buffer = 0
    var bits = 0
    for (byte in bytes) {
        buffer = (buffer shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK)
        bits += Byte.SIZE_BITS
        while (bits >= CHARACTER_BITS) {
            builder.append(BASE32[(buffer ushr (bits - CHARACTER_BITS)) and CHARACTER_MASK])
            bits -= CHARACTER_BITS
        }
    }
    if (bits > 0) builder.append(BASE32[(buffer shl (CHARACTER_BITS - bits)) and CHARACTER_MASK])
    builder.append(BASE32[checkValue(builder)])
    return builder.toString()
}

internal fun decodeCode(text: String): ByteArray? {
    val normalized = text.uppercase().filter { it != ' ' && it != '-' }
    if (normalized.length != CODE_CHARACTERS + 1 || normalized.any { it !in BASE32 }) return null
    val body = normalized.substring(0, CODE_CHARACTERS)
    if (BASE32[checkValue(body)] != normalized.last()) return null
    val result = ByteArray(CODE_BYTES)
    var buffer = 0
    var bits = 0
    var index = 0
    for (character in body) {
        buffer = (buffer shl CHARACTER_BITS) or BASE32.indexOf(character)
        bits += CHARACTER_BITS
        if (bits >= Byte.SIZE_BITS && index < CODE_BYTES) {
            result[index++] = (buffer ushr (bits - Byte.SIZE_BITS)).toByte()
            bits -= Byte.SIZE_BITS
        }
        buffer = buffer and ((1 shl bits) - 1)
    }
    return result.takeIf { buffer == 0 }
}

/** Weights each character by its odd position, so any single typing error changes the check character. */
private fun checkValue(body: CharSequence): Int {
    return body.foldIndexed(0) { index, sum, character -> (sum + (2 * index + 1) * BASE32.indexOf(character)) % BASE32.length }
}

private fun Long.toBytes(): ByteArray {
    return ByteArray(Long.SIZE_BYTES) { index -> (this ushr (Long.SIZE_BITS - Byte.SIZE_BITS * (index + 1))).toByte() }
}

private fun ByteArray.toLong(): Long {
    return fold(0L) { value, byte -> (value shl Byte.SIZE_BITS) or (byte.toLong() and BYTE_MASK.toLong()) }
}
