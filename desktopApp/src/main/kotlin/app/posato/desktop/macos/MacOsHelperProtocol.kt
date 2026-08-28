@file:Suppress("MagicNumber")

package app.posato.desktop.macos

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal object MacOsHelperProtocol {
    const val MAXIMUM_FRAME_BYTES: Int = 512 * 1024
    const val IDENTIFIER_BYTES: Int = 16
    const val MAXIMUM_OPERATIONS: Int = 256
    const val HEADER_BYTES: Int = 68
    const val REQUIRED_CAPABILITIES: Long = 1L
    private const val MAGIC: Int = 0x5053544F
    private const val MAJOR_VERSION: Short = 1

    fun encode(message: HelperMessage): ByteArray {
        require(message.connectionIdentifier.size == IDENTIFIER_BYTES)
        require(message.sessionIdentifier.size == IDENTIFIER_BYTES)
        require(message.requestIdentifier.size == IDENTIFIER_BYTES)
        require(message.payload.size + HEADER_BYTES <= MAXIMUM_FRAME_BYTES)
        return ByteBuffer.allocate(HEADER_BYTES + message.payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(MAGIC)
            .putShort(MAJOR_VERSION)
            .put(message.kind.code)
            .put(message.operation.code)
            .putInt(message.sequence)
            .putInt(message.deadlineMilliseconds)
            .put(message.connectionIdentifier)
            .put(message.sessionIdentifier)
            .put(message.requestIdentifier)
            .putInt(message.payload.size)
            .put(message.payload)
            .array()
    }

    fun decode(encoded: ByteArray): HelperMessage {
        require(encoded.size in HEADER_BYTES..MAXIMUM_FRAME_BYTES)
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        require(buffer.int == MAGIC)
        require(buffer.short == MAJOR_VERSION)
        val kindCode = buffer.get()
        val kind = HelperMessageKind.entries.singleOrNull { it.code == kindCode }
            ?: error("Unknown message kind")
        val operationCode = buffer.get()
        val operation = HelperOperation.entries.singleOrNull { it.code == operationCode }
            ?: error("Unknown operation")
        val sequence = buffer.int
        val deadline = buffer.int
        require(deadline in 0..120_000)
        val connection = ByteArray(IDENTIFIER_BYTES).also(buffer::get)
        val session = ByteArray(IDENTIFIER_BYTES).also(buffer::get)
        val request = ByteArray(IDENTIFIER_BYTES).also(buffer::get)
        val payloadSize = buffer.int
        require(payloadSize >= 0 && payloadSize == buffer.remaining())
        val payload = ByteArray(payloadSize).also(buffer::get)
        return HelperMessage(
            kind = kind,
            operation = operation,
            sequence = sequence,
            deadlineMilliseconds = deadline,
            connectionIdentifier = connection,
            sessionIdentifier = session,
            requestIdentifier = request,
            payload = payload,
        )
    }

    fun canonicalInputDigest(
        operation: HelperOperation,
        payload: ByteArray,
    ): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(byteArrayOf(operation.code) + payload)
    }

    fun capabilityPayload(): ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(REQUIRED_CAPABILITIES)
            .array()
    }
}

internal enum class HelperMessageKind(
    val code: Byte,
) {
    Hello(1),
    Welcome(2),
    Request(3),
    Response(4),
    Cancel(5),
}

internal enum class HelperOperation(
    val code: Byte,
) {
    None(0),
    Status(1),
    Enable(2),
    Repair(3),
    Apply(4),
    Restore(5),
    Disable(6),
    Remove(7),
    Reconcile(8),
    Renew(9),
}

internal data class HelperMessage(
    val kind: HelperMessageKind,
    val operation: HelperOperation,
    val sequence: Int,
    val deadlineMilliseconds: Int,
    val connectionIdentifier: ByteArray,
    val sessionIdentifier: ByteArray,
    val requestIdentifier: ByteArray,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        return other is HelperMessage &&
            kind == other.kind &&
            operation == other.operation &&
            sequence == other.sequence &&
            deadlineMilliseconds == other.deadlineMilliseconds &&
            connectionIdentifier.contentEquals(other.connectionIdentifier) &&
            sessionIdentifier.contentEquals(other.sessionIdentifier) &&
            requestIdentifier.contentEquals(other.requestIdentifier) &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        return sequence
    }
}
