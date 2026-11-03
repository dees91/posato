package app.posato.desktop.macos

import app.posato.feature.enforcement.DomainWindowPlan
import app.posato.feature.enforcement.WindowSegment
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object BrowserDomainConfigureLimits {
    const val MAXIMUM_DOMAIN_COUNT: Int = 1_024
    const val MINIMUM_DOMAIN_LENGTH: Int = 3
    const val MAXIMUM_DOMAIN_LENGTH: Int = 253
    const val MINIMUM_LABEL_LENGTH: Int = 1
    const val MAXIMUM_LABEL_LENGTH: Int = 63
    const val MAXIMUM_ASCII_CODE: Int = 128
    const val BYTE_MASK: Int = 0xFF
    const val PORT_BYTE_SHIFT: Int = 8
    const val RESPONSE_SIZE_BYTES: Int = 7
    const val RESPONSE_PORT_HIGH_INDEX: Int = 5
    const val RESPONSE_PORT_LOW_INDEX: Int = 6
    const val RESULT_SIZE_BYTES: Int = 5
    const val NO_END_TIME_FLAG: Int = 0
    const val HAS_END_TIME_FLAG: Int = 1
}

internal data class BrowserDomainConfigurePayload(
    val domains: List<String>,
    val sessionEndEpochMilliseconds: Long?,
    /** The window plans of held websites (TARGETS-013); the helper blocks a website without a valid one. */
    val windows: List<DomainWindowPlan> = emptyList(),
) {
    init {
        require(domains.size in 1..BrowserDomainConfigureLimits.MAXIMUM_DOMAIN_COUNT)
        require(domains.toSet().size == domains.size)
        require(domains.all(::isCanonicalExactDomain))
        sessionEndEpochMilliseconds?.let { end ->
            require(end >= 0)
        }
    }

    fun encode(): ByteArray {
        val encodedDomains = domains.map { domain -> domain.encodeToByteArray() }
        val endTimeBytes = if (sessionEndEpochMilliseconds == null) 1 else 9
        val baseSize = 2 + encodedDomains.sumOf { encoded -> 1 + encoded.size } + endTimeBytes
        val windowSection = windowSection()
        // Plans that do not fit the frame are left out, so every website stays blocked rather than the start failing.
        val sendsWindows = windowSection != null && baseSize + windowSection.size <= MAXIMUM_PAYLOAD_BYTES
        val size = baseSize + if (sendsWindows) windowSection.size else 0
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(encodedDomains.size.toShort())
        encodedDomains.forEach { encoded ->
            require(encoded.size in 1..UByte.MAX_VALUE.toInt())
            buffer.put(encoded.size.toByte())
            buffer.put(encoded)
        }
        if (sessionEndEpochMilliseconds == null) {
            buffer.put(0)
        } else {
            buffer.put(1)
            buffer.putLong(sessionEndEpochMilliseconds)
        }
        if (sendsWindows) {
            buffer.put(windowSection)
        }
        return buffer.array()
    }

    /** The optional window section, or null without plans or with a plan the frame cannot carry. */
    private fun windowSection(): ByteArray? {
        if (windows.isEmpty() || windows.size > domains.size) {
            return null
        }
        val plans = windows.map { plan -> plan.domain.encodeToByteArray() to plan.segments }
        if (plans.any { (domain, segments) -> domain.size !in 1..UByte.MAX_VALUE.toInt() || segments.size > UByte.MAX_VALUE.toInt() }) {
            return null
        }
        if (windows.any { plan -> plan.segments.any { segment -> !segment.fitsWire() } }) {
            return null
        }
        val size = WINDOW_SECTION_HEADER_BYTES + plans.sumOf { (domain, segments) -> 2 + domain.size + segments.size * SEGMENT_BYTES }
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(1)
        buffer.putShort(plans.size.toShort())
        plans.forEach { (domain, segments) ->
            buffer.put(domain.size.toByte())
            buffer.put(domain)
            buffer.put(segments.size.toByte())
            segments.forEach { segment ->
                buffer.putLong(segment.untilEpochMillis)
                buffer.put(segment.periodMinutes.toByte())
                buffer.put(segment.lengthMinutes.toByte())
            }
        }
        return buffer.array()
    }

    override fun toString(): String {
        return "BrowserDomainConfigurePayload(redacted)"
    }

    companion object {
        fun decode(payload: ByteArray): BrowserDomainConfigurePayload {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            require(buffer.remaining() >= 2)
            val count = buffer.short.toInt() and 0xFFFF
            require(count in 1..BrowserDomainConfigureLimits.MAXIMUM_DOMAIN_COUNT)
            val domains = buildList {
                repeat(count) {
                    require(buffer.remaining() >= 1)
                    val length = buffer.get().toInt() and BrowserDomainConfigureLimits.BYTE_MASK
                    require(length in 1..BrowserDomainConfigureLimits.MAXIMUM_DOMAIN_LENGTH)
                    require(buffer.remaining() >= length)
                    val encoded = ByteArray(length)
                    buffer.get(encoded)
                    add(encoded.decodeToString())
                }
            }
            require(buffer.remaining() >= 1)
            val hasEndTime = buffer.get().toInt() and BrowserDomainConfigureLimits.BYTE_MASK
            val sessionEndEpochMilliseconds = readSessionEndTime(buffer, hasEndTime)
            val windows = if (buffer.hasRemaining()) readWindows(buffer, count) else emptyList()
            require(!buffer.hasRemaining())
            return BrowserDomainConfigurePayload(domains, sessionEndEpochMilliseconds, windows)
        }

        private fun readWindows(
            buffer: ByteBuffer,
            maximumPlans: Int,
        ): List<DomainWindowPlan> {
            require(buffer.get().toInt() == 1)
            require(buffer.remaining() >= 2)
            val count = buffer.short.toInt() and 0xFFFF
            require(count in 1..maximumPlans)
            return List(count) {
                require(buffer.remaining() >= 1)
                val length = buffer.get().toInt() and BrowserDomainConfigureLimits.BYTE_MASK
                require(length in 1..BrowserDomainConfigureLimits.MAXIMUM_DOMAIN_LENGTH && buffer.remaining() >= length + 1)
                val domain = ByteArray(length).also(buffer::get).decodeToString()
                val segments = buffer.get().toInt() and BrowserDomainConfigureLimits.BYTE_MASK
                require(buffer.remaining() >= segments * SEGMENT_BYTES)
                DomainWindowPlan(
                    domain,
                    List(segments) {
                        val until = buffer.long
                        val period = buffer.get().toInt() and BrowserDomainConfigureLimits.BYTE_MASK
                        val length = buffer.get().toInt() and BrowserDomainConfigureLimits.BYTE_MASK
                        WindowSegment(until, period, length)
                    },
                )
            }
        }

        private fun readSessionEndTime(
            buffer: ByteBuffer,
            hasEndTime: Int,
        ): Long? {
            if (hasEndTime == BrowserDomainConfigureLimits.NO_END_TIME_FLAG) {
                return null
            }
            if (hasEndTime == BrowserDomainConfigureLimits.HAS_END_TIME_FLAG) {
                require(buffer.remaining() >= Long.SIZE_BYTES)
                return buffer.long
            }
            error("Invalid configure end-time flag")
        }
    }
}

internal data class BrowserDomainConfigureResponse(
    val result: HelperResult,
    val port: UShort,
) {
    init {
        if (result.outcome == HelperResult.Outcome.Success) {
            require(port > 0u)
        } else {
            require(port == 0.toUShort())
        }
    }

    override fun toString(): String {
        return "BrowserDomainConfigureResponse(redacted)"
    }

    companion object {
        fun decode(payload: ByteArray): BrowserDomainConfigureResponse {
            require(payload.size == BrowserDomainConfigureLimits.RESPONSE_SIZE_BYTES)
            val high = payload[BrowserDomainConfigureLimits.RESPONSE_PORT_HIGH_INDEX].toInt() and
                BrowserDomainConfigureLimits.BYTE_MASK
            val low = payload[BrowserDomainConfigureLimits.RESPONSE_PORT_LOW_INDEX].toInt() and
                BrowserDomainConfigureLimits.BYTE_MASK
            val port = ((high shl BrowserDomainConfigureLimits.PORT_BYTE_SHIFT) or low).toUShort()
            val result = HelperResult.decode(payload.copyOf(BrowserDomainConfigureLimits.RESULT_SIZE_BYTES))
            return BrowserDomainConfigureResponse(result, port)
        }
    }
}

internal fun isCanonicalExactDomain(domain: String): Boolean {
    val length = domain.length
    if (length !in BrowserDomainConfigureLimits.MINIMUM_DOMAIN_LENGTH..BrowserDomainConfigureLimits.MAXIMUM_DOMAIN_LENGTH) {
        return false
    }
    if (!domain.all(::isCanonicalDomainCharacter)) {
        return false
    }
    val labels = domain.split('.', limit = Int.MAX_VALUE)
    val last = labels.lastOrNull() ?: return false
    return labels.size >= 2 &&
        last.any { character -> !character.isDigit() } &&
        labels.all(::isCanonicalDomainLabel)
}

private fun isCanonicalDomainCharacter(character: Char): Boolean {
    if (character.code >= BrowserDomainConfigureLimits.MAXIMUM_ASCII_CODE) {
        return false
    }
    return isDomainBodyCharacter(character)
}

private fun isDomainBodyCharacter(character: Char): Boolean {
    return character in 'a'..'z' || character.isDigit() || isDomainSymbol(character)
}

private fun isDomainSymbol(character: Char): Boolean {
    return character == '-' || character == '.'
}

private fun isCanonicalDomainLabel(label: String): Boolean {
    val length = label.length
    if (length !in BrowserDomainConfigureLimits.MINIMUM_LABEL_LENGTH..BrowserDomainConfigureLimits.MAXIMUM_LABEL_LENGTH) {
        return false
    }
    val first = label.first()
    val last = label.last()
    val validEdges = first.isLetterOrDigit() && last.isLetterOrDigit()
    return validEdges && label.all { character -> character.isLetterOrDigit() || character == '-' }
}

/** The frame's room for a configure payload after the message header (MacOsHelperProtocol). */
private const val MAXIMUM_PAYLOAD_BYTES: Int = MacOsHelperProtocol.MAXIMUM_FRAME_BYTES - MacOsHelperProtocol.HEADER_BYTES

/** The marker byte and the plan count. */
private const val WINDOW_SECTION_HEADER_BYTES: Int = 3

/** The until instant, the period, and the length. */
private const val SEGMENT_BYTES: Int = 10

private fun WindowSegment.fitsWire(): Boolean {
    return untilEpochMillis >= 0 && periodMinutes in 0..UByte.MAX_VALUE.toInt() && lengthMinutes in 0..UByte.MAX_VALUE.toInt()
}
