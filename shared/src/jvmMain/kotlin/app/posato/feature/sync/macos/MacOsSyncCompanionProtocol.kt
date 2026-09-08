package app.posato.feature.sync.macos

import app.posato.feature.sync.mailbox.MAILBOX_BUNDLE_BYTES
import app.posato.feature.sync.mailbox.MAILBOX_BUNDLE_IDENTIFIER_BYTES
import app.posato.feature.sync.mailbox.MAILBOX_CURSOR_BYTES
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
    const val MAXIMUM_PAYLOAD_BYTES: Int = 65_584
    const val MAXIMUM_RESPONSE_PAYLOAD_BYTES: Int = 81_946
    const val MAXIMUM_FRAME_BYTES: Int = HEADER_BYTES + MAXIMUM_PAYLOAD_BYTES
    const val MAXIMUM_RESPONSE_FRAME_BYTES: Int = HEADER_BYTES + MAXIMUM_RESPONSE_PAYLOAD_BYTES
    const val MAXIMUM_DEADLINE_MILLISECONDS: Int = 120_000
    const val KEYCHAIN_CAPABILITY: Long = 1L
    const val CLOUDKIT_CAPABILITY: Long = 2L
    const val ANCHOR_BYTES: Int = 48
    const val BUNDLE_IDENTIFIER_BYTES: Int = MAILBOX_BUNDLE_IDENTIFIER_BYTES
    const val BUNDLE_BYTES: Int = MAILBOX_BUNDLE_BYTES
    const val CURSOR_BYTES: Int = MAILBOX_CURSOR_BYTES
    const val COMPANION_IDENTIFIER: String = "app.posato.macos.sync"
    const val APPLICATION_IDENTIFIER: String = "app.posato.macos"
    const val COMPANION_EXECUTABLE: String = "PosatoMacOSSync"
    const val RELATIVE_EXECUTABLE: String = "Contents/Helpers/PosatoMacOSSync.app/Contents/MacOS/PosatoMacOSSync"
    const val OPERATION_RESOLVE_BINDING: Byte = 1
    const val OPERATION_READ_ITEM: Byte = 2
    const val OPERATION_CREATE_ITEM: Byte = 3
    const val OPERATION_DELETE_ITEM: Byte = 4
    const val OPERATION_FETCH_ZONE: Byte = 5
    const val OPERATION_SAVE_ZONE: Byte = 6
    const val OPERATION_READ_ANCHOR: Byte = 7
    const val OPERATION_CREATE_ANCHOR: Byte = 8
    const val OPERATION_SAVE_BUNDLE: Byte = 9
    const val OPERATION_FETCH_CHANGES: Byte = 10
    const val OPERATION_DELETE_ZONE: Byte = 11
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
    const val OUTCOME_ALREADY_EXISTS: Byte = 13
    const val OUTCOME_CONFLICT: Byte = 14

    fun encode(message: SyncCompanionMessage): ByteArray {
        require(message.requestIdentifier.size == IDENTIFIER_BYTES)
        require(message.payload.size <= MAXIMUM_RESPONSE_PAYLOAD_BYTES)
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
        require(encoded.size in HEADER_BYTES..MAXIMUM_RESPONSE_FRAME_BYTES)
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
        if (operation != SyncCompanionOperation.FetchChanges || outcome == null) {
            require(payloadSize <= MAXIMUM_PAYLOAD_BYTES)
        }
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

    fun cloudPayload(binding: ByteArray): ByteArray {
        require(binding.size == BINDING_BYTES)
        return binding.copyOf()
    }

    fun anchorPayload(
        binding: ByteArray,
        anchor: ByteArray,
    ): ByteArray {
        require(binding.size == BINDING_BYTES)
        require(anchor.size == ANCHOR_BYTES)
        return binding + anchor
    }

    fun bundlePayload(
        binding: ByteArray,
        identifier: ByteArray,
        bundle: ByteArray,
    ): ByteArray {
        require(binding.size == BINDING_BYTES)
        require(identifier.size == BUNDLE_IDENTIFIER_BYTES)
        require(bundle.size in 1..BUNDLE_BYTES)
        return binding + identifier + bundle
    }

    fun cursorPayload(
        binding: ByteArray,
        cursor: ByteArray,
    ): ByteArray {
        require(binding.size == BINDING_BYTES)
        require(cursor.size in 0..CURSOR_BYTES)
        return binding + cursor
    }
}

internal enum class SyncCompanionOperation(
    val code: Byte,
) {
    ResolveBinding(MacOsSyncCompanionProtocol.OPERATION_RESOLVE_BINDING),
    ReadItem(MacOsSyncCompanionProtocol.OPERATION_READ_ITEM),
    CreateItem(MacOsSyncCompanionProtocol.OPERATION_CREATE_ITEM),
    DeleteItemAndVerifyAbsent(MacOsSyncCompanionProtocol.OPERATION_DELETE_ITEM),
    FetchZone(MacOsSyncCompanionProtocol.OPERATION_FETCH_ZONE),
    SaveZone(MacOsSyncCompanionProtocol.OPERATION_SAVE_ZONE),
    ReadAnchor(MacOsSyncCompanionProtocol.OPERATION_READ_ANCHOR),
    CreateAnchor(MacOsSyncCompanionProtocol.OPERATION_CREATE_ANCHOR),
    SaveBundle(MacOsSyncCompanionProtocol.OPERATION_SAVE_BUNDLE),
    FetchChanges(MacOsSyncCompanionProtocol.OPERATION_FETCH_CHANGES),
    DeleteZoneAndVerifyAbsent(MacOsSyncCompanionProtocol.OPERATION_DELETE_ZONE),
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
    AlreadyExists(MacOsSyncCompanionProtocol.OUTCOME_ALREADY_EXISTS),
    Conflict(MacOsSyncCompanionProtocol.OUTCOME_CONFLICT),
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
