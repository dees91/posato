package app.posato.android

/**
 * One DNS question carried in an IPv4 UDP packet from the device's VPN interface (ADR 0010). Only an uncompressed
 * single question is read; anything else is left for the system to retry, never guessed at.
 */
internal class DnsPacket private constructor(
    private val packet: ByteArray,
    private val dnsOffset: Int,
    private val questionEnd: Int,
    val name: String,
    val type: Int,
    val destinationPort: Int,
) {
    val dns: ByteArray
        get() = packet.copyOfRange(dnsOffset, packet.size)

    /** An answer that names no reachable address: `0.0.0.0` for A, `::` for AAAA, and no records for other types. */
    fun blockedReply(): ByteArray {
        val question = packet.copyOfRange(dnsOffset + DNS_HEADER, questionEnd)
        val address = when (type) {
            TYPE_A -> ByteArray(IPV4_ADDRESS)
            TYPE_AAAA -> ByteArray(IPV6_ADDRESS)
            else -> null
        }
        val header = byteArrayOf(
            packet[dnsOffset],
            packet[dnsOffset + 1],
            FLAGS_RESPONSE_HIGH,
            FLAGS_RESPONSE_LOW,
            0,
            1,
            0,
            if (address == null) 0 else 1,
            0,
            0,
            0,
            0,
        )
        val answer = address?.let {
            byteArrayOf(NAME_POINTER_HIGH, NAME_POINTER_LOW, 0, type.toByte(), 0, 1, 0, 0, 0, TTL_SECONDS, 0, it.size.toByte()) + it
        } ?: ByteArray(0)
        return wrapReply(header + question + answer)
    }

    /** Sends [reply] back to the asker: the addresses and ports of the question, swapped. */
    fun wrapReply(reply: ByteArray): ByteArray {
        val ipHeader = IPV4_HEADER
        val total = ipHeader + UDP_HEADER + reply.size
        val result = ByteArray(total)
        result[0] = IPV4_VERSION_AND_LENGTH
        result[2] = (total shr BYTE).toByte()
        result[3] = total.toByte()
        result[8] = DEFAULT_TTL
        result[9] = PROTOCOL_UDP
        packet.copyInto(result, 12, 16, 20)
        packet.copyInto(result, 16, 12, 16)
        val checksum = checksum(result, 0, ipHeader)
        result[10] = (checksum shr BYTE).toByte()
        result[11] = checksum.toByte()
        val udpStart = ihl()
        packet.copyInto(result, ipHeader, udpStart + 2, udpStart + 4)
        packet.copyInto(result, ipHeader + 2, udpStart, udpStart + 2)
        result[ipHeader + 4] = ((UDP_HEADER + reply.size) shr BYTE).toByte()
        result[ipHeader + 5] = (UDP_HEADER + reply.size).toByte()
        reply.copyInto(result, ipHeader + UDP_HEADER)
        return result
    }

    private fun ihl(): Int {
        return (packet[0].toInt() and LOW_NIBBLE) * WORD
    }

    companion object {
        private const val IPV4_HEADER = 20
        private const val UDP_HEADER = 8
        private const val DNS_HEADER = 12
        private const val PROTOCOL_UDP: Byte = 17
        private const val IPV4_VERSION_AND_LENGTH: Byte = 0x45
        private const val DEFAULT_TTL: Byte = 64
        private const val TYPE_A = 1
        private const val TYPE_AAAA = 28
        private const val IPV4_ADDRESS = 4
        private const val IPV6_ADDRESS = 16
        private const val TTL_SECONDS: Byte = 30
        private const val FLAGS_RESPONSE_HIGH: Byte = 0x81.toByte()
        private const val FLAGS_RESPONSE_LOW: Byte = 0x80.toByte()
        private const val NAME_POINTER_HIGH: Byte = 0xC0.toByte()
        private const val NAME_POINTER_LOW: Byte = 0x0C
        private const val LOW_NIBBLE = 0x0F
        private const val WORD = 4
        private const val BYTE = 8
        private const val MAX_LABEL = 63
        private const val QUESTION_TAIL = 4

        fun parse(packet: ByteArray): DnsPacket? {
            if (packet.size < IPV4_HEADER + UDP_HEADER + DNS_HEADER || packet[0].toInt() shr WORD != WORD) return null
            if (packet[9] != PROTOCOL_UDP) return null
            val udp = (packet[0].toInt() and LOW_NIBBLE) * WORD
            val dns = udp + UDP_HEADER
            if (packet.size < dns + DNS_HEADER || unsigned16(packet, dns + 4) != 1) return null
            val labels = mutableListOf<String>()
            var position = dns + DNS_HEADER
            while (true) {
                val length = packet.getOrNull(position)?.toInt()?.and(0xFF) ?: return null
                if (length == 0) break
                if (length > MAX_LABEL || position + 1 + length > packet.size) return null
                labels += packet.decodeToString(position + 1, position + 1 + length).lowercase()
                position += 1 + length
            }
            val questionEnd = position + 1 + QUESTION_TAIL
            if (labels.isEmpty() || questionEnd > packet.size) return null
            val type = unsigned16(packet, position + 1)
            return DnsPacket(packet, dns, questionEnd, labels.joinToString("."), type, unsigned16(packet, udp + 2))
        }

        private fun unsigned16(
            bytes: ByteArray,
            offset: Int,
        ): Int {
            return ((bytes[offset].toInt() and 0xFF) shl BYTE) or (bytes[offset + 1].toInt() and 0xFF)
        }

        private fun checksum(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            var sum = 0
            for (index in offset until offset + length step 2) sum += unsigned16(bytes, index)
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            return sum.inv() and 0xFFFF
        }
    }
}
