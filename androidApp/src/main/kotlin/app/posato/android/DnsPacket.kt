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
        val header = ByteArray(DNS_HEADER)
        packet.copyInto(header, 0, dnsOffset, dnsOffset + FIELD_16)
        header.put16(DNS_FLAGS, FLAGS_RESPONSE)
        header.put16(DNS_QUESTIONS, 1)
        header.put16(DNS_ANSWERS, if (address == null) 0 else 1)
        val answer = address?.let { rdata ->
            val record = ByteArray(ANSWER_FIXED)
            record.put16(ANSWER_NAME, NAME_POINTER_TO_QUESTION)
            record.put16(ANSWER_TYPE, type)
            record.put16(ANSWER_CLASS, CLASS_INTERNET)
            record.put16(ANSWER_TTL + FIELD_16, TTL_SECONDS)
            record.put16(ANSWER_LENGTH, rdata.size)
            record + rdata
        } ?: ByteArray(0)
        return wrapReply(header + question + answer)
    }

    /** Sends [reply] back to the asker: the addresses and ports of the question, swapped. */
    fun wrapReply(reply: ByteArray): ByteArray {
        val total = IPV4_HEADER + UDP_HEADER + reply.size
        val result = ByteArray(total)
        result[0] = IPV4_VERSION_AND_LENGTH
        result.put16(IP_TOTAL_LENGTH, total)
        result[IP_TTL] = DEFAULT_TTL
        result[IP_PROTOCOL] = PROTOCOL_UDP
        packet.copyInto(result, IP_SOURCE, IP_DESTINATION, IP_DESTINATION + IPV4_ADDRESS)
        packet.copyInto(result, IP_DESTINATION, IP_SOURCE, IP_SOURCE + IPV4_ADDRESS)
        result.put16(IP_CHECKSUM, checksum(result, IPV4_HEADER))
        val udpStart = ihl(packet)
        packet.copyInto(result, IPV4_HEADER + UDP_SOURCE_PORT, udpStart + UDP_DESTINATION_PORT, udpStart + UDP_DESTINATION_PORT + FIELD_16)
        packet.copyInto(result, IPV4_HEADER + UDP_DESTINATION_PORT, udpStart + UDP_SOURCE_PORT, udpStart + UDP_SOURCE_PORT + FIELD_16)
        result.put16(IPV4_HEADER + UDP_LENGTH, UDP_HEADER + reply.size)
        reply.copyInto(result, IPV4_HEADER + UDP_HEADER)
        return result
    }

    companion object {
        private const val IPV4_HEADER = 20
        private const val UDP_HEADER = 8
        private const val DNS_HEADER = 12
        private const val FIELD_16 = 2
        private const val IP_VERSION_4 = 4
        private const val IP_TOTAL_LENGTH = 2
        private const val IP_TTL = 8
        private const val IP_PROTOCOL = 9
        private const val IP_CHECKSUM = 10
        private const val IP_SOURCE = 12
        private const val IP_DESTINATION = 16
        private const val UDP_SOURCE_PORT = 0
        private const val UDP_DESTINATION_PORT = 2
        private const val UDP_LENGTH = 4
        private const val DNS_FLAGS = 2
        private const val DNS_QUESTIONS = 4
        private const val DNS_ANSWERS = 6
        private const val ANSWER_NAME = 0
        private const val ANSWER_TYPE = 2
        private const val ANSWER_CLASS = 4
        private const val ANSWER_TTL = 6
        private const val ANSWER_LENGTH = 10
        private const val ANSWER_FIXED = 12
        private const val PROTOCOL_UDP: Byte = 17
        private const val IPV4_VERSION_AND_LENGTH: Byte = 0x45
        private const val DEFAULT_TTL: Byte = 64
        private const val TYPE_A = 1
        private const val TYPE_AAAA = 28
        private const val CLASS_INTERNET = 1
        private const val IPV4_ADDRESS = 4
        private const val IPV6_ADDRESS = 16
        private const val TTL_SECONDS = 30
        private const val FLAGS_RESPONSE = 0x8180
        private const val NAME_POINTER_TO_QUESTION = 0xC00C
        private const val LOW_NIBBLE = 0x0F
        private const val NIBBLE = 4
        private const val WORD = 4
        private const val BYTE_MASK = 0xFF
        private const val FIELD_MASK = 0xFFFF
        private const val FIELD_BITS = 16
        private const val MAX_LABEL = 63
        private const val QUESTION_TAIL = 4

        fun parse(packet: ByteArray): DnsPacket? {
            if (packet.size < IPV4_HEADER + UDP_HEADER + DNS_HEADER || packet[0].toInt() shr NIBBLE != IP_VERSION_4) return null
            if (packet[IP_PROTOCOL] != PROTOCOL_UDP) return null
            val udp = ihl(packet)
            val dns = udp + UDP_HEADER
            if (packet.size < dns + DNS_HEADER || unsigned16(packet, dns + DNS_QUESTIONS) != 1) return null
            val (labels, end) = readName(packet, dns + DNS_HEADER) ?: return null
            val questionEnd = end + 1 + QUESTION_TAIL
            if (labels.isEmpty() || questionEnd > packet.size) return null
            val type = unsigned16(packet, end + 1)
            return DnsPacket(packet, dns, questionEnd, labels.joinToString("."), type, unsigned16(packet, udp + UDP_DESTINATION_PORT))
        }

        /** The lowercase labels of an uncompressed name and the offset of its terminating zero, or null if malformed. */
        private fun readName(
            packet: ByteArray,
            start: Int,
        ): Pair<List<String>, Int>? {
            val labels = mutableListOf<String>()
            var position = start
            var length = packet.getOrNull(position)?.toInt()?.and(BYTE_MASK)
            while (length != null && length != 0 && fits(packet, position, length)) {
                labels += packet.decodeToString(position + 1, position + 1 + length).lowercase()
                position += 1 + length
                length = packet.getOrNull(position)?.toInt()?.and(BYTE_MASK)
            }
            return if (length == 0) labels to position else null
        }

        private fun fits(
            packet: ByteArray,
            position: Int,
            length: Int,
        ): Boolean {
            return length <= MAX_LABEL && position + 1 + length <= packet.size
        }

        private fun ihl(packet: ByteArray): Int {
            return (packet[0].toInt() and LOW_NIBBLE) * WORD
        }

        private fun unsigned16(
            bytes: ByteArray,
            offset: Int,
        ): Int {
            return ((bytes[offset].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (bytes[offset + 1].toInt() and BYTE_MASK)
        }

        private fun ByteArray.put16(
            offset: Int,
            value: Int,
        ) {
            this[offset] = (value shr Byte.SIZE_BITS).toByte()
            this[offset + 1] = value.toByte()
        }

        /** The IPv4 header checksum: the ones' complement of the ones' complement sum of its 16-bit words. */
        private fun checksum(
            bytes: ByteArray,
            length: Int,
        ): Int {
            var sum = 0
            for (index in 0 until length step FIELD_16) sum += unsigned16(bytes, index)
            while (sum shr FIELD_BITS != 0) sum = (sum and FIELD_MASK) + (sum shr FIELD_BITS)
            return sum.inv() and FIELD_MASK
        }
    }
}
