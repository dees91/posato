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

    @Test
    fun `given a reserved operation when decoded then decoding fails`() {
        val codes = listOf<Byte>(13, 14, 15)

        codes.forEach { code ->
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
            encoded[6] = code

            assertFails {
                MacOsSyncCompanionProtocol.decode(encoded)
            }
        }
    }

    @Test
    fun `given an oversized non fetch response when decoded then decoding fails`() {
        val message = SyncCompanionMessage(
            operation = SyncCompanionOperation.ReadAnchor,
            requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
            deadlineMilliseconds = 30_000,
            capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
            outcome = SyncCompanionOutcome.Found,
            payload = ByteArray(MacOsSyncCompanionProtocol.MAXIMUM_PAYLOAD_BYTES + 1),
        )

        assertFails {
            MacOsSyncCompanionProtocol.decode(MacOsSyncCompanionProtocol.encode(message))
        }
    }

    @Test
    fun `given cloud operations when encoded then the codec round trips`() {
        val operations = listOf(
            SyncCompanionOperation.FetchZone,
            SyncCompanionOperation.SaveZone,
            SyncCompanionOperation.ReadAnchor,
            SyncCompanionOperation.CreateAnchor,
            SyncCompanionOperation.SaveBundle,
            SyncCompanionOperation.FetchChanges,
            SyncCompanionOperation.DeleteWorkspaceRecords,
            SyncCompanionOperation.SweepBundlesIfAnchorMissing,
        )

        operations.forEach { operation ->
            val message = SyncCompanionMessage(
                operation = operation,
                requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                deadlineMilliseconds = 30_000,
                capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
                outcome = null,
                payload = ByteArray(0),
            )

            val decoded = MacOsSyncCompanionProtocol.decode(MacOsSyncCompanionProtocol.encode(message))

            assertEquals(operation, decoded.operation)
            assertEquals(MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY, decoded.capabilities)
        }
    }

    @Test
    fun `given new outcomes when encoded then the codec round trips`() {
        val outcomes = listOf(SyncCompanionOutcome.AlreadyExists, SyncCompanionOutcome.Conflict)

        outcomes.forEach { outcome ->
            val message = SyncCompanionMessage(
                operation = SyncCompanionOperation.SaveZone,
                requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                deadlineMilliseconds = 30_000,
                capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
                outcome = outcome,
                payload = ByteArray(0),
            )

            val decoded = MacOsSyncCompanionProtocol.decode(MacOsSyncCompanionProtocol.encode(message))

            assertEquals(outcome, decoded.outcome)
        }
    }

    @Test
    fun `given cloud payloads when built then sizes match the wire layouts`() {
        val binding = ByteArray(MacOsSyncCompanionProtocol.BINDING_BYTES) { 7 }

        assertEquals(32, MacOsSyncCompanionProtocol.cloudPayload(binding).size)
        assertEquals(80, MacOsSyncCompanionProtocol.anchorPayload(binding, ByteArray(48)).size)
        assertEquals(
            49,
            MacOsSyncCompanionProtocol.bundlePayload(binding, ByteArray(16), byteArrayOf(1)).size,
        )
        assertEquals(32, MacOsSyncCompanionProtocol.cursorPayload(binding, ByteArray(0)).size)
    }

    @Test
    fun `given an oversized bundle when built then building fails`() {
        val binding = ByteArray(MacOsSyncCompanionProtocol.BINDING_BYTES) { 7 }

        assertFails {
            MacOsSyncCompanionProtocol.bundlePayload(
                binding,
                ByteArray(16),
                ByteArray(MacOsSyncCompanionProtocol.BUNDLE_BYTES + 1),
            )
        }
        assertFails {
            MacOsSyncCompanionProtocol.cursorPayload(
                binding,
                ByteArray(MacOsSyncCompanionProtocol.CURSOR_BYTES + 1),
            )
        }
    }

    @Test
    fun `given a fetch response frame when decoded then the response bound applies`() {
        val message = SyncCompanionMessage(
            operation = SyncCompanionOperation.FetchChanges,
            requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
            deadlineMilliseconds = 30_000,
            capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
            outcome = SyncCompanionOutcome.Found,
            payload = ByteArray(MacOsSyncCompanionProtocol.MAXIMUM_RESPONSE_PAYLOAD_BYTES),
        )

        val decoded = MacOsSyncCompanionProtocol.decode(MacOsSyncCompanionProtocol.encode(message))

        assertEquals(MacOsSyncCompanionProtocol.MAXIMUM_RESPONSE_PAYLOAD_BYTES, decoded.payload.size)
    }

    @Test
    fun `given an oversized fetch request frame when decoded then it fails`() {
        val message = SyncCompanionMessage(
            operation = SyncCompanionOperation.FetchChanges,
            requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
            deadlineMilliseconds = 30_000,
            capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
            outcome = null,
            payload = ByteArray(MacOsSyncCompanionProtocol.MAXIMUM_PAYLOAD_BYTES + 1),
        )

        assertFails {
            MacOsSyncCompanionProtocol.decode(MacOsSyncCompanionProtocol.encode(message))
        }
    }
}
