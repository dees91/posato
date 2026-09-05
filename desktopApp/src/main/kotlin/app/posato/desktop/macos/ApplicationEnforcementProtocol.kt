package app.posato.desktop.macos

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Objects

internal object ApplicationEnforcementLimits {
    const val MAXIMUM_APPLICATION_COUNT: Int = 64
    const val MAXIMUM_REQUIREMENT_BYTES: Int = 4_096
    const val BYTE_MASK: Int = 0xFF
    const val RESPONSE_SIZE_BYTES: Int = 7
    const val RESPONSE_COUNT_HIGH_INDEX: Int = 5
    const val RESPONSE_COUNT_LOW_INDEX: Int = 6
    const val RESULT_SIZE_BYTES: Int = 5
    const val NO_END_TIME_FLAG: Int = 0
    const val HAS_END_TIME_FLAG: Int = 1
    const val UNSIGNED_SHORT_MASK: Int = 0xFFFF
    const val COUNT_BYTE_SHIFT: Int = 8
}

private fun isValidRequirement(requirement: ByteArray): Boolean {
    return requirement.isNotEmpty() && requirement.size <= ApplicationEnforcementLimits.MAXIMUM_REQUIREMENT_BYTES
}

internal data class ApplicationEnforcementPayload(
    val requirements: List<ByteArray>,
    val sessionEndEpochMilliseconds: Long?,
) {
    init {
        require(requirements.size <= ApplicationEnforcementLimits.MAXIMUM_APPLICATION_COUNT)
        require(requirements.all(::isValidRequirement))
        require(requirements.map(ByteArray::toList).toSet().size == requirements.size)
        sessionEndEpochMilliseconds?.let { end ->
            require(end >= 0)
        }
    }

    fun encode(): ByteArray {
        val endTimeBytes = if (sessionEndEpochMilliseconds == null) 1 else 9
        val size = 2 + requirements.sumOf { requirement -> 2 + requirement.size } + endTimeBytes
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(requirements.size.toShort())
        requirements.forEach { requirement ->
            buffer.putShort(requirement.size.toShort())
            buffer.put(requirement)
        }
        if (sessionEndEpochMilliseconds == null) {
            buffer.put(0)
        } else {
            buffer.put(1)
            buffer.putLong(sessionEndEpochMilliseconds)
        }
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ApplicationEnforcementPayload) {
            return false
        }
        return requirements.size == other.requirements.size &&
            requirements.zip(other.requirements).all { (left, right) -> left.contentEquals(right) } &&
            sessionEndEpochMilliseconds == other.sessionEndEpochMilliseconds
    }

    override fun hashCode(): Int {
        return Objects.hash(requirements.map(ByteArray::toList), sessionEndEpochMilliseconds)
    }

    override fun toString(): String {
        return "ApplicationEnforcementPayload(redacted)"
    }

    companion object {
        fun decode(payload: ByteArray): ApplicationEnforcementPayload {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            require(buffer.remaining() >= Short.SIZE_BYTES)
            val count = buffer.short.toInt() and ApplicationEnforcementLimits.UNSIGNED_SHORT_MASK
            require(count in 0..ApplicationEnforcementLimits.MAXIMUM_APPLICATION_COUNT)
            val requirements = buildList {
                repeat(count) {
                    require(buffer.remaining() >= Short.SIZE_BYTES)
                    val length = buffer.short.toInt() and ApplicationEnforcementLimits.UNSIGNED_SHORT_MASK
                    require(length in 1..ApplicationEnforcementLimits.MAXIMUM_REQUIREMENT_BYTES)
                    require(buffer.remaining() >= length)
                    val requirement = ByteArray(length)
                    buffer.get(requirement)
                    add(requirement)
                }
            }
            require(buffer.remaining() >= 1)
            val hasEndTime = buffer.get().toInt() and ApplicationEnforcementLimits.BYTE_MASK
            val sessionEndEpochMilliseconds = readSessionEndTime(buffer, hasEndTime)
            require(!buffer.hasRemaining())
            return ApplicationEnforcementPayload(requirements, sessionEndEpochMilliseconds)
        }

        private fun readSessionEndTime(
            buffer: ByteBuffer,
            hasEndTime: Int,
        ): Long? {
            if (hasEndTime == ApplicationEnforcementLimits.NO_END_TIME_FLAG) {
                return null
            }
            if (hasEndTime == ApplicationEnforcementLimits.HAS_END_TIME_FLAG) {
                require(buffer.remaining() >= Long.SIZE_BYTES)
                return buffer.long
            }
            error("Invalid application end-time flag")
        }
    }
}

internal data class ApplicationEnforcementResponse(
    val result: HelperResult,
    val acceptedCount: Int,
) {
    init {
        if (result.outcome == HelperResult.Outcome.Success) {
            require(acceptedCount >= 0)
        } else {
            require(acceptedCount == 0)
        }
    }

    override fun toString(): String {
        return "ApplicationEnforcementResponse(redacted)"
    }

    companion object {
        fun decode(payload: ByteArray): ApplicationEnforcementResponse {
            require(payload.size == ApplicationEnforcementLimits.RESPONSE_SIZE_BYTES)
            val high = payload[ApplicationEnforcementLimits.RESPONSE_COUNT_HIGH_INDEX].toInt() and
                ApplicationEnforcementLimits.BYTE_MASK
            val low = payload[ApplicationEnforcementLimits.RESPONSE_COUNT_LOW_INDEX].toInt() and
                ApplicationEnforcementLimits.BYTE_MASK
            val acceptedCount = (high shl ApplicationEnforcementLimits.COUNT_BYTE_SHIFT) or low
            val result = HelperResult.decode(payload.copyOf(ApplicationEnforcementLimits.RESULT_SIZE_BYTES))
            return ApplicationEnforcementResponse(result, acceptedCount)
        }
    }
}
