package app.posato.feature.transfer.data

import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.data.hkdfSha256
import app.posato.feature.sync.domain.SyncFormatLimits

/**
 * PBKDF2 rounds for container version 1. A reader accepts only this count for version 1, so a changed header
 * can never make it derive with an attacker's count; a later version raises it with a new version byte.
 */
internal const val EXPORT_ITERATIONS_V1: Int = 600_000

private const val CONTAINER_VERSION: Int = 1
private const val SALT_BYTES: Int = 16
private const val KEY_BYTES: Int = 32
private val magic = "PSX1".encodeToByteArray()
private val keyInfo = "app.posato.export.v1/key".encodeToByteArray()
private val headerBytes = magic.size + 1 + Int.SIZE_BYTES + SALT_BYTES + SyncFormatLimits.AES_NONCE_BYTES

/** What opening a file gives: its JSON, or why it cannot be read. */
internal sealed interface PauseSetOpenResult {
    /** A plain file, read as it is. */
    class Plain(
        val json: ByteArray,
    ) : PauseSetOpenResult

    /** A coded file that the code opened. */
    class Opened(
        val json: ByteArray,
    ) : PauseSetOpenResult

    /** A coded file and no code yet. */
    data object CodeNeeded : PauseSetOpenResult

    /** The code doesn't match, or the file was changed: GCM cannot tell the two apart. */
    data object CodeMismatch : PauseSetOpenResult

    /** A coded file with a broken header or length. */
    data object Damaged : PauseSetOpenResult

    /** A coded file from a newer Posato. */
    data object NewerVersion : PauseSetOpenResult
}

/**
 * The coded container: `PSX1`, the container version, the PBKDF2 count, a salt, and a nonce, then AES-256-GCM
 * of the JSON with that whole header as associated data. The key is PBKDF2 over the code's nine characters,
 * separated for this use with HKDF.
 */
internal class PauseSetFileSeal(
    private val crypto: SyncCryptoProvider,
) {
    fun seal(
        json: ByteArray,
        code: String,
    ): ByteArray? {
        val normalized = ExportCode.normalize(code) ?: return null
        val salt = crypto.randomBytes(SALT_BYTES)?.takeIf { it.size == SALT_BYTES } ?: return null
        val nonce = crypto.randomBytes(SyncFormatLimits.AES_NONCE_BYTES)?.takeIf { it.size == SyncFormatLimits.AES_NONCE_BYTES } ?: return null
        val header = magic + byteArrayOf(CONTAINER_VERSION.toByte()) + EXPORT_ITERATIONS_V1.toBytes() + salt + nonce
        val key = deriveKey(normalized, salt) ?: return null
        return try {
            crypto.sealAesGcm(key, nonce, header, json)?.let { body -> header + body }
        } finally {
            key.fill(0)
        }
    }

    fun open(
        bytes: ByteArray,
        code: String?,
    ): PauseSetOpenResult {
        if (!isSealed(bytes)) {
            return PauseSetOpenResult.Plain(bytes)
        }
        if (bytes.size <= magic.size) {
            return PauseSetOpenResult.Damaged
        }
        if (bytes[magic.size].toInt() > CONTAINER_VERSION) {
            return PauseSetOpenResult.NewerVersion
        }
        if (bytes[magic.size].toInt() != CONTAINER_VERSION || bytes.size < headerBytes + SyncFormatLimits.AES_TAG_BYTES ||
            bytes.copyOfRange(magic.size + 1, magic.size + 1 + Int.SIZE_BYTES).toInt() != EXPORT_ITERATIONS_V1
        ) {
            return PauseSetOpenResult.Damaged
        }
        val normalized =
            code?.let(ExportCode::normalize) ?: return if (code == null) PauseSetOpenResult.CodeNeeded else PauseSetOpenResult.CodeMismatch
        val header = bytes.copyOfRange(0, headerBytes)
        val salt = header.copyOfRange(magic.size + 1 + Int.SIZE_BYTES, magic.size + 1 + Int.SIZE_BYTES + SALT_BYTES)
        val nonce = header.copyOfRange(headerBytes - SyncFormatLimits.AES_NONCE_BYTES, headerBytes)
        val key = deriveKey(normalized, salt) ?: return PauseSetOpenResult.CodeMismatch
        return try {
            crypto.openAesGcm(key, nonce, header, bytes.copyOfRange(headerBytes, bytes.size))
                ?.let(PauseSetOpenResult::Opened)
                ?: PauseSetOpenResult.CodeMismatch
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(
        code: String,
        salt: ByteArray,
    ): ByteArray? {
        val password = code.encodeToByteArray()
        val master = crypto.pbkdf2HmacSha256(password, salt, EXPORT_ITERATIONS_V1, KEY_BYTES).also { password.fill(0) } ?: return null
        return try {
            crypto.hkdfSha256(master, salt, keyInfo)
        } finally {
            master.fill(0)
        }
    }

    companion object {
        /** A coded file starts with its magic; anything else is read as a plain file. */
        fun isSealed(bytes: ByteArray): Boolean {
            return bytes.size >= magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)
        }
    }
}

/** The code Posato shows once when it seals a file: nine characters of the pairing alphabet, 45 bits. */
internal object ExportCode {
    const val LENGTH: Int = 9

    private const val ALPHABET: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val ALPHABET_MASK: Int = 0x1F
    private const val GROUP_CHARACTERS: Int = 3

    /** Nine characters drawn uniformly: each takes five bits of one random byte. */
    fun generate(crypto: SyncCryptoProvider): String? {
        val random = crypto.randomBytes(LENGTH)?.takeIf { it.size == LENGTH } ?: return null
        return try {
            random.joinToString("") { byte -> ALPHABET[byte.toInt() and ALPHABET_MASK].toString() }
        } finally {
            random.fill(0)
        }
    }

    /** The code as typed, forgiving case, spaces, hyphens, and the digits 0, 1, and 8; null when it cannot be one. */
    fun normalize(typed: String): String? {
        val normalized = typed.uppercase()
            .filter { character -> character != ' ' && character != '-' }
            .map { character ->
                when (character) {
                    '0' -> 'O'
                    '1' -> 'I'
                    '8' -> 'B'
                    else -> character
                }
            }
            .joinToString("")
        return normalized.takeIf { it.length == LENGTH && it.all { character -> character in ALPHABET } }
    }

    /** Three groups of three, as it is shown and read aloud. */
    fun grouped(code: String): String {
        return code.chunked(GROUP_CHARACTERS).joinToString(" ")
    }
}

private fun Int.toBytes(): ByteArray {
    return ByteArray(Int.SIZE_BYTES) { index -> (this ushr ((Int.SIZE_BYTES - 1 - index) * Byte.SIZE_BITS)).toByte() }
}

private fun ByteArray.toInt(): Int {
    return fold(0) { value, byte -> (value shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK) }
}

private const val BYTE_MASK: Int = 0xFF
