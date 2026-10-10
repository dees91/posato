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
        val binding = (ports.resolveBinding() as? BindingResolution.Available)?.binding ?: return PairingOfferResult.Unavailable
        val anchor = (ports.readAnchor(binding) as? AnchorReadResult.Found)?.anchor ?: return PairingOfferResult.Unavailable
        val item = (ports.readItem(binding, anchor.account()) as? KeyItemReadResult.Found)?.value ?: return PairingOfferResult.Unavailable
        val workspace = ports.workspaceDirectory(binding) ?: return PairingOfferResult.Unavailable
        val codeBytes = crypto.randomBytes(CODE_BYTES)?.takeIf { it.size == CODE_BYTES } ?: return PairingOfferResult.Unavailable
        val itemBytes = item.copyBytes()
        return try {
            dismiss()
            sweepExpired(workspace)
            val expiresAt = now() + OFFER_LIFETIME_MILLIS
            val derived = derive(codeBytes) ?: return PairingOfferResult.Unavailable
            val nonce = crypto.randomBytes(SyncFormatLimits.AES_NONCE_BYTES) ?: return PairingOfferResult.Unavailable
            val header = offerMagic + byteArrayOf(OFFER_VERSION.toByte()) + expiresAt.toBytes() + nonce
            val sealed = crypto.sealAesGcm(derived.key, nonce, header + derived.name, itemBytes) ?: return PairingOfferResult.Unavailable
            derived.key.fill(0)
            val file = workspace.child(PAIRING_DIRECTORY).child(derived.name.toHex() + OFFER_SUFFIX)
            if (withContext(ioDispatcher) { files.writeExclusive(file, header + sealed) } != ExclusiveWrite.CREATED) {
                return PairingOfferResult.Unavailable
            }
            currentOffer = file
            PairingOfferResult.Offered(PairingOffer(encodeCode(codeBytes), expiresAt))
        } finally {
            codeBytes.fill(0)
            itemBytes.fill(0)
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
}

private fun WorkspaceAnchor.account(): KeyAccount {
    return checkNotNull(KeyAccount.fromText(BootstrapEncoding.identifierToAccountText(workspaceId.value)))
}

internal fun encodeCode(bytes: ByteArray): String {
    val builder = StringBuilder()
    var buffer = 0
    var bits = 0
    for (byte in bytes) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
        bits += 8
        while (bits >= 5) {
            builder.append(BASE32[(buffer ushr (bits - 5)) and 31])
            bits -= 5
        }
    }
    if (bits > 0) builder.append(BASE32[(buffer shl (5 - bits)) and 31])
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
        buffer = (buffer shl 5) or BASE32.indexOf(character)
        bits += 5
        if (bits >= 8 && index < CODE_BYTES) {
            result[index++] = (buffer ushr (bits - 8)).toByte()
            bits -= 8
        }
        buffer = buffer and ((1 shl bits) - 1)
    }
    return result.takeIf { buffer == 0 }
}

private fun checkValue(body: CharSequence): Int {
    return body.foldIndexed(0) { index, sum, character -> (sum + (2 * index + 1) * BASE32.indexOf(character)) % 32 }
}

private fun Long.toBytes(): ByteArray {
    return ByteArray(Long.SIZE_BYTES) { index -> (this ushr (56 - index * 8)).toByte() }
}

private fun ByteArray.toLong(): Long {
    return fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xFF) }
}
