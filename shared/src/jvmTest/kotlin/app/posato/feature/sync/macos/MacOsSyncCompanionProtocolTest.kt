package app.posato.feature.sync.macos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class MacOsSyncCompanionProtocolTest {
    @Test
    fun `given a valid request when encoded then the codec round trips`() {
        val message = SyncCompanionMessage(
            operation = SyncCompanionOperation.ReadItem,
            requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
            deadlineMilliseconds = 5_000,
            capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
            outcome = null,
            payload = byteArrayOf(1, 2, 3),
        )

        val decoded = MacOsSyncCompanionProtocol.decode(MacOsSyncCompanionProtocol.encode(message))

        assertEquals(message.operation, decoded.operation)
        assertEquals(message.deadlineMilliseconds, decoded.deadlineMilliseconds)
        assertEquals(message.capabilities, decoded.capabilities)
        assertNull(decoded.outcome)
        assertEquals(listOf<Byte>(1, 2, 3), decoded.payload.toList())
        assertEquals("SyncCompanionMessage(redacted)", decoded.toString())
    }

    @Test
    fun `given a response when encoded then identity fields are echoed`() {
        val request = SyncCompanionMessage(
            operation = SyncCompanionOperation.CreateItem,
            requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
            deadlineMilliseconds = 5_000,
            capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
            outcome = null,
            payload = ByteArray(0),
        )
        val response = SyncCompanionMessage(
            operation = request.operation,
            requestIdentifier = request.requestIdentifier.copyOf(),
            deadlineMilliseconds = request.deadlineMilliseconds,
            capabilities = request.capabilities,
            outcome = SyncCompanionOutcome.Created,
            payload = ByteArray(0),
        )

        val decoded = MacOsSyncCompanionProtocol.decode(MacOsSyncCompanionProtocol.encode(response))

        assertEquals(SyncCompanionOutcome.Created, decoded.outcome)
        assertEquals(request.requestIdentifier.toList(), decoded.requestIdentifier.toList())
        assertEquals(request.operation, decoded.operation)
    }

    @Test
    fun `given an unknown operation when decoded then decoding fails`() {
        val encoded = MacOsSyncCompanionProtocol.encode(
            SyncCompanionMessage(
                operation = SyncCompanionOperation.ReadItem,
                requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                deadlineMilliseconds = 5_000,
                capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
                outcome = null,
                payload = ByteArray(0),
            ),
        )
        encoded[6] = 99

        assertFails {
            MacOsSyncCompanionProtocol.decode(encoded)
        }
    }
}
