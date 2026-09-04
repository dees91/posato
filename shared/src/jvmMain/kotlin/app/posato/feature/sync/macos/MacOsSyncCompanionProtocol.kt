package app.posato.feature.sync.macos

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object MacOsSyncCompanionProtocol {
    const val MAGIC: Int = 0x5053594E
    const val MAJOR_VERSION: Short = 1
    const val HEADER_BYTES: Int = 40
    const val IDENTIFIER_BYTES: Int = 16
    const val BINDING_BYTES: Int = 32
    const val ACCOUNT_BYTES: Int = 36
    const val ITEM_BYTES: Int = 84
    const val MAXIMUM_PAYLOAD_BYTES: Int = 65_536
    const val MAXIMUM_FRAME_BYTES: Int = HEADER_BYTES + MAXIMUM_PAYLOAD_BYTES
    const val MAXIMUM_DEADLINE_MILLISECONDS: Int = 120_000
    const val KEYCHAIN_CAPABILITY: Long = 1L
    const val COMPANION_IDENTIFIER: String = "app.posato.macos.sync"
    const val APPLICATION_IDENTIFIER: String = "app.posato.macos"
    const val COMPANION_EXECUTABLE: String = "PosatoMacOSSync"
    const val RELATIVE_EXECUTABLE: String = "Contents/Helpers/PosatoMacOSSync.app/Contents/MacOS/PosatoMacOSSync"
    const val OPERATION_RESOLVE_BINDING: Byte = 1
    const val OPERATION_READ_ITEM: Byte = 2
    const val OPERATION_CREATE_ITEM: Byte = 3
    const val OPERATION_DELETE_ITEM: Byte = 4
    const val OUTCOME_FOUND: Byte = 1
    const val OUTCOME_MISSING: Byte = 2
    const val OUTCOME_CREATED: Byte = 3
    const val OUTCOME_IDENTICAL: Byte = 4
    const val OUTCOME_RETRYABLE: Byte = 5
    const val OUTCOME_ACCOUNT_CHANGED: Byte = 6
    const val OUTCOME_UNKNOWN: Byte = 7
    const val OUTCOME_INTEGRITY_FAILURE: Byte = 8
    const val OUTCOME_UNAVAILABLE: Byte = 9
    const val OUTCOME_RESTRICTED: Byte = 10
    const val OUTCOME_UNDETERMINED: Byte = 11
    const val OUTCOME_DELETED: Byte = 12

    fun encode(message: SyncCompanionMessage): ByteArray {
        require(message.requestIdentifier.size == IDENTIFIER_BYTES)
        require(message.payload.size <= MAXIMUM_PAYLOAD_BYTES)
        require(message.deadlineMilliseconds in 1..MAXIMUM_DEADLINE_MILLISECONDS)
        return ByteBuffer.allocate(HEADER_BYTES + message.payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(MAGIC)
            .putShort(MAJOR_VERSION)
            .put(message.operation.code)
            .put(message.requestIdentifier)
            .putInt(message.deadlineMilliseconds)
            .putLong(message.capabilities)
            .put(message.outcome?.code ?: 0)
            .putInt(message.payload.size)
            .put(message.payload)
            .array()
    }

    fun decode(encoded: ByteArray): SyncCompanionMessage {
        require(encoded.size in HEADER_BYTES..MAXIMUM_FRAME_BYTES)
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        require(buffer.int == MAGIC)
        require(buffer.short == MAJOR_VERSION)
        val operation = requireNotNull(SyncCompanionOperation.fromCode(buffer.get()))
        val requestIdentifier = ByteArray(IDENTIFIER_BYTES).also(buffer::get)
        require(requestIdentifier.any { byte -> byte != 0.toByte() })
        val deadline = buffer.int
        require(deadline in 1..MAXIMUM_DEADLINE_MILLISECONDS)
        val capabilities = buffer.long
        val outcomeByte = buffer.get()
        val outcome = if (outcomeByte == 0.toByte()) {
            null
        } else {
            requireNotNull(SyncCompanionOutcome.fromCode(outcomeByte))
        }
        val payloadSize = buffer.int
        require(payloadSize >= 0 && payloadSize == buffer.remaining())
        val payload = ByteArray(payloadSize).also(buffer::get)
        return SyncCompanionMessage(
            operation = operation,
            requestIdentifier = requestIdentifier,
            deadlineMilliseconds = deadline,
            capabilities = capabilities,
            outcome = outcome,
            payload = payload,
        )
    }

    fun keyPayload(
        binding: ByteArray,
        account: String,
        item: ByteArray? = null,
    ): ByteArray {
        require(binding.size == BINDING_BYTES)
        val accountBytes = account.encodeToByteArray()
        require(accountBytes.size == ACCOUNT_BYTES)
        return if (item == null) {
            binding + accountBytes
        } else {
            require(item.size == ITEM_BYTES)
            binding + accountBytes + item
        }
    }
}

internal enum class SyncCompanionOperation(
    val code: Byte,
) {
    ResolveBinding(MacOsSyncCompanionProtocol.OPERATION_RESOLVE_BINDING),
    ReadItem(MacOsSyncCompanionProtocol.OPERATION_READ_ITEM),
    CreateItem(MacOsSyncCompanionProtocol.OPERATION_CREATE_ITEM),
    DeleteItemAndVerifyAbsent(MacOsSyncCompanionProtocol.OPERATION_DELETE_ITEM),
    ;

    companion object {
        fun fromCode(code: Byte): SyncCompanionOperation? {
            return entries.singleOrNull { operation -> operation.code == code }
        }
    }
}

internal enum class SyncCompanionOutcome(
    val code: Byte,
) {
    Found(MacOsSyncCompanionProtocol.OUTCOME_FOUND),
    Missing(MacOsSyncCompanionProtocol.OUTCOME_MISSING),
    Created(MacOsSyncCompanionProtocol.OUTCOME_CREATED),
    Identical(MacOsSyncCompanionProtocol.OUTCOME_IDENTICAL),
    Retryable(MacOsSyncCompanionProtocol.OUTCOME_RETRYABLE),
    AccountChanged(MacOsSyncCompanionProtocol.OUTCOME_ACCOUNT_CHANGED),
    UnknownOutcome(MacOsSyncCompanionProtocol.OUTCOME_UNKNOWN),
    IntegrityFailure(MacOsSyncCompanionProtocol.OUTCOME_INTEGRITY_FAILURE),
    Unavailable(MacOsSyncCompanionProtocol.OUTCOME_UNAVAILABLE),
    Restricted(MacOsSyncCompanionProtocol.OUTCOME_RESTRICTED),
    Undetermined(MacOsSyncCompanionProtocol.OUTCOME_UNDETERMINED),
    DeletedAndAbsent(MacOsSyncCompanionProtocol.OUTCOME_DELETED),
    ;

    companion object {
        fun fromCode(code: Byte): SyncCompanionOutcome? {
            return entries.singleOrNull { outcome -> outcome.code == code }
        }
    }
}

internal class SyncCompanionMessage(
    val operation: SyncCompanionOperation,
    val requestIdentifier: ByteArray,
    val deadlineMilliseconds: Int,
    val capabilities: Long,
    val outcome: SyncCompanionOutcome?,
    val payload: ByteArray,
) {
    override fun toString(): String {
        return "SyncCompanionMessage(redacted)"
    }

    fun clear() {
        payload.fill(0)
        requestIdentifier.fill(0)
    }
}
