package app.posato.desktop.macos

import app.posato.feature.targets.data.LocalApplicationMappingLimits
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

internal object MacOsApplicationSelectionProtocol {
    private const val MINIMUM_PAYLOAD_BYTES: Int = 3
    private const val SUCCESS: Byte = 1
    private const val CANCELLED: Byte = 2
    private const val SELF_SELECTION: Byte = 3
    private const val INVALID_OR_UNSIGNED: Byte = 4
    private const val CAPACITY: Byte = 5
    private const val FAILURE: Byte = 6
    private const val SYSTEM_APPLICATION: Byte = 7
    const val MAXIMUM_REQUIREMENT_BYTES: Int = 4_096

    fun decode(payload: ByteArray): MacOsApplicationPickerResult {
        require(payload.size >= MINIMUM_PAYLOAD_BYTES)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val outcome = buffer.get()
        val count = buffer.short.toUShort().toInt()
        if (outcome != SUCCESS) {
            require(count == 0 && !buffer.hasRemaining())
            return when (outcome) {
                CANCELLED -> MacOsApplicationPickerResult.Cancelled
                SELF_SELECTION -> MacOsApplicationPickerResult.SelfSelection
                INVALID_OR_UNSIGNED -> MacOsApplicationPickerResult.InvalidOrUnsigned
                CAPACITY -> MacOsApplicationPickerResult.CapacityExceeded
                SYSTEM_APPLICATION -> MacOsApplicationPickerResult.SystemApplication
                FAILURE -> MacOsApplicationPickerResult.Failure
                else -> error("Unknown application selection outcome")
            }
        }
        require(count in 1..LocalApplicationMappingLimits.MAXIMUM_MAPPINGS)
        val applications = buildList(count) {
            repeat(count) {
                val displayName = buffer.readBoundedBytes(LocalApplicationMappingLimits.MAXIMUM_DISPLAY_NAME_BYTES).decodeStrictUtf8()
                val requirement = buffer.readBoundedBytes(MAXIMUM_REQUIREMENT_BYTES)
                require(displayName.isNotEmpty() && displayName == displayName.trim())
                require(displayName.none { character -> character.isControlCharacter() })
                add(SelectedMacOsApplication(displayName, requirement))
            }
        }
        require(!buffer.hasRemaining())

        return MacOsApplicationPickerResult.Success(applications)
    }

    private fun ByteBuffer.readBoundedBytes(maximumBytes: Int): ByteArray {
        require(remaining() >= Short.SIZE_BYTES)
        val size = short.toUShort().toInt()
        require(size in 1..maximumBytes && remaining() >= size)

        return ByteArray(size).also(::get)
    }

    private fun ByteArray.decodeStrictUtf8(): String {
        return Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    }

    private fun Char.isControlCharacter(): Boolean {
        return this in '\u0000'..'\u001F' || this in '\u007F'..'\u009F'
    }
}
