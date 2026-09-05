package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException

private const val SEQUENCE_TAG = 0x30
private const val INTEGER_TAG = 0x02
private const val LONG_FORM_ONE_BYTE = 0x81
private const val SHORT_FORM_LIMIT = 0x80
private const val BYTE_MASK = 0xFF
private const val COMPONENT_LENGTH = 32
private const val JOSE_LENGTH = 64

/** A DER integer may carry one leading zero byte to keep it positive, so 33 bytes is the largest valid magnitude. */
private const val MAX_COMPONENT_BYTES = 33

/**
 * The conversion between what the JDK signs and what JOSE accepts.
 *
 * `SHA256withECDSA` emits an ASN.1 `SEQUENCE { INTEGER r, INTEGER s }`, while ES256 requires the raw concatenation
 * `r || s` with each component padded to exactly 32 bytes. Two encodings differ in ways that only show up in a
 * fraction of signatures: DER prefixes a zero byte when the high bit is set, and it drops leading zero bytes when a
 * component happens to be small. Getting either wrong produces a token that App Store Connect rejects roughly one
 * run in a hundred, so both are handled here and pinned by tests.
 */
object EcdsaSignature {
    fun derToJose(der: ByteArray): ByteArray {
        val reader = DerReader(der)
        ensure(reader.readByte() == SEQUENCE_TAG) { "it does not start with a DER sequence" }
        val sequenceLength = reader.readLength()
        ensure(sequenceLength == reader.remaining()) { "its sequence length does not match the signature size" }
        val r = reader.readInteger()
        val s = reader.readInteger()
        ensure(reader.remaining() == 0) { "it carries trailing bytes" }
        val jose = ByteArray(JOSE_LENGTH)
        r.copyInto(jose, COMPONENT_LENGTH - r.size)
        s.copyInto(jose, JOSE_LENGTH - s.size)
        return jose
    }

    /** One throw site, so every rejection carries the same category and no branch can forget to fail closed. */
    private fun ensure(
        condition: Boolean,
        reason: () -> String
    ) {
        if (!condition) throw malformed(reason())
    }

    private fun malformed(reason: String): ProvisioningException = ProvisioningException(
        ErrorCode.ASC_TOKEN_FAILED,
        "The signature this Mac produced could not be converted to the JOSE form because $reason.",
        "Rerun the command; if it repeats, the configured key is not a supported elliptic-curve key.",
    )

    private class DerReader(
        private val bytes: ByteArray
    ) {
        private var index = 0

        fun remaining(): Int = bytes.size - index

        fun readByte(): Int {
            ensure(remaining() >= 1) { "it ends before the value it declares" }
            val value = bytes[index].toInt() and BYTE_MASK
            index += 1
            return value
        }

        fun readLength(): Int {
            val first = readByte()
            if (first < SHORT_FORM_LIMIT) return first
            ensure(first == LONG_FORM_ONE_BYTE) { "it declares a length this parser does not accept" }
            return readByte()
        }

        fun readInteger(): ByteArray {
            ensure(readByte() == INTEGER_TAG) { "one of its components is not a DER integer" }
            val length = readLength()
            ensure(length in 1..MAX_COMPONENT_BYTES) { "one of its components has an unusable length" }
            ensure(remaining() >= length) { "one of its components ends early" }
            val magnitude = bytes.copyOfRange(index, index + length)
            index += length
            val stripped = if (magnitude.first() == ZERO_BYTE) magnitude.copyOfRange(1, magnitude.size) else magnitude
            ensure(stripped.size <= COMPONENT_LENGTH) { "one of its components is larger than the curve allows" }
            return stripped
        }

        private companion object {
            const val ZERO_BYTE: Byte = 0
        }
    }
}
