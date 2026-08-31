package app.posato.desktop.macos

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MacOsHelperProtocolTest {
    @Test
    fun `message round trip preserves all fields`() {
        val message = HelperMessage(
            kind = HelperMessageKind.Request,
            operation = HelperOperation.Apply,
            sequence = 7,
            deadlineMilliseconds = 9_000,
            connectionIdentifier = ByteArray(16) { 1 },
            sessionIdentifier = ByteArray(16) { 2 },
            requestIdentifier = ByteArray(16) { 3 },
            payload = byteArrayOf(0x45, 0x69),
        )

        val encoded = MacOsHelperProtocol.encode(message)

        assertEquals(
            "5053544f000103040000000700002328" +
                "01010101010101010101010101010101" +
                "02020202020202020202020202020202" +
                "03030303030303030303030303030303" +
                "000000024569",
            encoded.toHex(),
        )
        assertEquals(message, MacOsHelperProtocol.decode(encoded))
    }

    @Test
    fun `canonical digest ignores transport metadata`() {
        val payload = byteArrayOf(0x45, 0x69)

        assertContentEquals(
            MacOsHelperProtocol.canonicalInputDigest(HelperOperation.Apply, payload),
            MacOsHelperProtocol.canonicalInputDigest(HelperOperation.Apply, payload),
        )
    }

    @Test
    fun `unknown version is rejected`() {
        val encoded = MacOsHelperProtocol.encode(
            HelperMessage(
                kind = HelperMessageKind.Hello,
                operation = HelperOperation.None,
                sequence = 1,
                deadlineMilliseconds = 1,
                connectionIdentifier = ByteArray(16),
                sessionIdentifier = ByteArray(16),
                requestIdentifier = ByteArray(16),
                payload = byteArrayOf(),
            ),
        )
        encoded[5] = 2

        assertFailsWith<IllegalArgumentException> {
            MacOsHelperProtocol.decode(encoded)
        }
    }

    @Test
    fun `capability payload is fixed width and big endian`() {
        assertEquals("0000000000000003", MacOsHelperProtocol.capabilityPayload().toHex())
        assertEquals(true, MacOsHelperProtocol.supportsRequiredParentCapabilities(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 3)))
        assertEquals(false, MacOsHelperProtocol.supportsRequiredParentCapabilities(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1)))
    }

    @Test
    fun `cancel frame preserves the request being cancelled`() {
        val requestIdentifier = ByteArray(16) { 9 }
        val cancel = HelperMessage(
            kind = HelperMessageKind.Cancel,
            operation = HelperOperation.None,
            sequence = 3,
            deadlineMilliseconds = 250,
            connectionIdentifier = ByteArray(16) { 1 },
            sessionIdentifier = ByteArray(16) { 2 },
            requestIdentifier = requestIdentifier,
            payload = byteArrayOf(),
        )

        val decoded = MacOsHelperProtocol.decode(MacOsHelperProtocol.encode(cancel))

        assertEquals(HelperMessageKind.Cancel, decoded.kind)
        assertContentEquals(requestIdentifier, decoded.requestIdentifier)
    }

    @Test
    fun `lost reconcile response preserves original unknown request`() {
        val original = message(operation = HelperOperation.Apply, requestByte = 4)
        val reconcile = message(operation = HelperOperation.Reconcile, requestByte = 4)

        assertEquals(original, retainPendingUnknownRequest(original, reconcile))
        assertEquals(original, retainPendingUnknownRequest(null, original))
    }

    @Test
    fun `unknown repair is reconciled once`() {
        var reconciliations = 0
        val success = HelperResult(
            outcome = HelperResult.Outcome.Success,
            serviceState = HelperResult.State.Ready,
            ownershipPhase = HelperResult.Phase.Idle,
            requiredAction = HelperResult.RequiredAction.None,
            failure = HelperResult.Failure.None,
        )

        val result = completeRepair(HelperResult.unknownOutcome()) {
            reconciliations += 1
            success
        }

        assertEquals(success, result)
        assertEquals(1, reconciliations)
    }

    private fun message(
        operation: HelperOperation,
        requestByte: Byte,
    ): HelperMessage = HelperMessage(
        kind = HelperMessageKind.Request,
        operation = operation,
        sequence = 1,
        deadlineMilliseconds = 1,
        connectionIdentifier = ByteArray(16),
        sessionIdentifier = ByteArray(16),
        requestIdentifier = ByteArray(16) { requestByte },
        payload = byteArrayOf(),
    )

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = 16).padStart(length = 2, padChar = '0')
    }
}
