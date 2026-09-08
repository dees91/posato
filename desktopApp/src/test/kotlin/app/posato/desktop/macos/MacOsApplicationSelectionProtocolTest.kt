package app.posato.desktop.macos

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MacOsApplicationSelectionProtocolTest {
    @Test
    fun `successful selection payload restores bounded identities`() {
        val name = "Browser".encodeToByteArray()
        val requirement = byteArrayOf(1, 2, 3)
        val payload = ByteBuffer.allocate(3 + 2 + name.size + 2 + requirement.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(1)
            .putShort(1)
            .putShort(name.size.toShort())
            .put(name)
            .putShort(requirement.size.toShort())
            .put(requirement)
            .array()

        val application = assertIs<MacOsApplicationPickerResult.Success>(
            MacOsApplicationSelectionProtocol.decode(payload),
        ).applications.single()

        assertEquals("Browser", application.displayName)
        assertContentEquals(requirement, application.designatedRequirement)
        assertEquals("SelectedMacOsApplication(redacted)", application.toString())
    }

    @Test
    fun `cancel and rejection outcomes require an empty batch`() {
        assertEquals(
            MacOsApplicationPickerResult.Cancelled,
            MacOsApplicationSelectionProtocol.decode(byteArrayOf(2, 0, 0)),
        )
        assertEquals(
            MacOsApplicationPickerResult.SelfSelection,
            MacOsApplicationSelectionProtocol.decode(byteArrayOf(3, 0, 0)),
        )
        assertEquals(
            MacOsApplicationPickerResult.SystemApplication,
            MacOsApplicationSelectionProtocol.decode(byteArrayOf(7, 0, 0)),
        )
        assertFailsWith<IllegalArgumentException> {
            MacOsApplicationSelectionProtocol.decode(byteArrayOf(2, 0, 1))
        }
        assertFailsWith<IllegalStateException> {
            MacOsApplicationSelectionProtocol.decode(byteArrayOf(8, 0, 0))
        }
    }

    @Test
    fun `selection deadline is isolated from lifecycle deadline`() {
        val selection = message(HelperOperation.SelectApplications, MacOsHelperProtocol.MAXIMUM_SELECTION_DEADLINE_MILLISECONDS)
        assertEquals(selection, MacOsHelperProtocol.decode(MacOsHelperProtocol.encode(selection)))

        val lifecycle = message(HelperOperation.Apply, MacOsHelperProtocol.MAXIMUM_SELECTION_DEADLINE_MILLISECONDS)
        assertFailsWith<IllegalArgumentException> {
            MacOsHelperProtocol.decode(MacOsHelperProtocol.encode(lifecycle))
        }
    }

    private fun message(
        operation: HelperOperation,
        deadline: Int
    ): HelperMessage {
        return HelperMessage(
            kind = HelperMessageKind.Request,
            operation = operation,
            sequence = 1,
            deadlineMilliseconds = deadline,
            connectionIdentifier = ByteArray(16),
            sessionIdentifier = ByteArray(16),
            requestIdentifier = ByteArray(16),
            payload = byteArrayOf(),
        )
    }
}
