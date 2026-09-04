package app.posato.feature.sync.macos

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object FakeSyncCompanionMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: "created"
        if (mode == "hang") {
            Thread.sleep(HANG_MILLISECONDS)
            return
        }
        if (mode == "silent") {
            return
        }
        val prefix = System.`in`.readNBytes(LENGTH_PREFIX_BYTES)
        if (prefix.size != LENGTH_PREFIX_BYTES) {
            return
        }
        val size = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN).int
        val encoded = System.`in`.readNBytes(size)
        if (mode == "malformed") {
            System.out.write(MALFORMED_FRAME)
            return
        }
        val request = MacOsSyncCompanionProtocol.decode(encoded)
        val requestIdentifier = if (mode == "wrong-identity") {
            ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 1 }
        } else {
            request.requestIdentifier
        }
        val outcome = when (mode) {
            "unavailable" -> SyncCompanionOutcome.Unavailable
            "found-binding" -> SyncCompanionOutcome.Found
            "wrong-identity" -> SyncCompanionOutcome.Created
            "full-page" -> SyncCompanionOutcome.Found
            else -> SyncCompanionOutcome.Created
        }
        val payload = if (mode == "found-binding") {
            ByteArray(MacOsSyncCompanionProtocol.BINDING_BYTES) { 7 }
        } else if (mode == "full-page") {
            fullPage()
        } else {
            ByteArray(0)
        }
        val response = MacOsSyncCompanionProtocol.encode(
            SyncCompanionMessage(
                operation = request.operation,
                requestIdentifier = requestIdentifier,
                deadlineMilliseconds = request.deadlineMilliseconds,
                capabilities = request.capabilities,
                outcome = outcome,
                payload = payload,
            ),
        )
        val framePrefix = ByteBuffer.allocate(LENGTH_PREFIX_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(response.size)
            .array()
        System.out.write(framePrefix)
        System.out.write(response)
    }

    private fun fullPage(): ByteArray {
        val cursor = ByteArray(MacOsSyncCompanionProtocol.CURSOR_BYTES) { 5 }
        val identifier = ByteArray(MacOsSyncCompanionProtocol.BUNDLE_IDENTIFIER_BYTES) { 9 }
        val bundle = ByteArray(MacOsSyncCompanionProtocol.BUNDLE_BYTES) { 7 }
        val buffer = ByteBuffer.allocate(MacOsSyncCompanionProtocol.MAXIMUM_RESPONSE_PAYLOAD_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(0)
        buffer.putInt(cursor.size)
        buffer.put(cursor)
        buffer.put(1)
        buffer.put(identifier)
        buffer.putInt(bundle.size)
        buffer.put(bundle)
        return buffer.array().copyOf(buffer.position())
    }

    private val MALFORMED_FRAME: ByteArray = byteArrayOf(0, 0, 0, 4, 1, 2, 3, 4)
    private const val LENGTH_PREFIX_BYTES: Int = 4
    private const val HANG_MILLISECONDS: Long = 60_000L
}
