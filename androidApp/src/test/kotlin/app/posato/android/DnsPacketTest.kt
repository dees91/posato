package app.posato.android

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DnsPacketTest {
    private fun query(
        name: String,
        type: Int = 1,
        id: Int = 0x1234,
    ): ByteArray {
        val question = name.split('.').flatMap { label -> listOf(label.length.toByte()) + label.toByteArray().toList() } + 0.toByte()
        val dns = byteArrayOf((id shr 8).toByte(), id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) + question.toByteArray() +
            byteArrayOf(0, type.toByte(), 0, 1)
        return ipv4Udp(byteArrayOf(10, 111, 0, 1), byteArrayOf(10, 111, 0, 2), 40000, 53, dns)
    }

    private fun ipv4Udp(
        source: ByteArray,
        destination: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val length = 20 + 8 + payload.size
        val header = byteArrayOf(0x45, 0, (length shr 8).toByte(), length.toByte(), 0, 0, 0, 0, 64, 17, 0, 0) + source + destination
        val udp = byteArrayOf(
            (sourcePort shr 8).toByte(),
            sourcePort.toByte(),
            (destinationPort shr 8).toByte(),
            destinationPort.toByte(),
            ((8 + payload.size) shr 8).toByte(),
            (8 + payload.size).toByte(),
            0,
            0,
        )
        return header + udp + payload
    }

    @Test
    fun `given a DNS query in an IPv4 UDP packet when parsed then its name, type, and addresses are read`() {
        val parsed = DnsPacket.parse(query("www.Example.org", type = 28))!!

        assertEquals("www.example.org", parsed.name)
        assertEquals(28, parsed.type)
        assertEquals(53, parsed.destinationPort)
    }

    @Test
    fun `given truncated, non UDP, or compressed questions when parsed then nothing is read`() {
        val packet = query("example.org")
        assertNull(DnsPacket.parse(packet.copyOf(30)))
        assertNull(DnsPacket.parse(packet.copyOf().also { it[9] = 6 }))
        val compressed = packet.copyOf().also { it[40] = 0xC0.toByte() }
        assertNull(DnsPacket.parse(compressed))
        assertNull(DnsPacket.parse(ByteArray(0)))
    }

    @Test
    fun `given a blocked A query when answered then the reply goes back to the asker with one unspecified address`() {
        val request = DnsPacket.parse(query("example.org", type = 1))!!
        val reply = request.blockedReply()

        assertContentEquals(byteArrayOf(10, 111, 0, 2), reply.copyOfRange(12, 16))
        assertContentEquals(byteArrayOf(10, 111, 0, 1), reply.copyOfRange(16, 20))
        assertEquals(53, ((reply[20].toInt() and 0xFF) shl 8) or (reply[21].toInt() and 0xFF))
        assertEquals(40000, ((reply[22].toInt() and 0xFF) shl 8) or (reply[23].toInt() and 0xFF))
        val dns = reply.copyOfRange(28, reply.size)
        assertEquals(0x12, dns[0].toInt())
        assertEquals(0x34, dns[1].toInt())
        assertEquals(1, dns[7].toInt())
        assertContentEquals(byteArrayOf(0, 4, 0, 0, 0, 0), dns.copyOfRange(dns.size - 6, dns.size))
        assertEquals(0, ipChecksum(reply.copyOf(20)))
    }

    @Test
    fun `given a blocked AAAA query when answered then the address is all zeros, and other types get no answer`() {
        val aaaa = DnsPacket.parse(query("example.org", type = 28))!!.blockedReply()
        val dns = aaaa.copyOfRange(28, aaaa.size)
        assertContentEquals(ByteArray(16), dns.copyOfRange(dns.size - 16, dns.size))
        val mx = DnsPacket.parse(query("example.org", type = 15))!!.blockedReply()
        assertEquals(0, mx[28 + 7].toInt())
    }

    @Test
    fun `given an upstream answer when wrapped then it returns to the asker as an IPv4 UDP packet`() {
        val request = DnsPacket.parse(query("open.example"))!!
        val upstream = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 0, 0, 0, 0, 0)
        val reply = request.wrapReply(upstream)

        assertEquals(20 + 8 + upstream.size, ((reply[2].toInt() and 0xFF) shl 8) or (reply[3].toInt() and 0xFF))
        assertContentEquals(upstream, reply.copyOfRange(28, reply.size))
    }

    private fun ipChecksum(header: ByteArray): Int {
        var sum = 0
        for (index in header.indices step 2) sum += ((header[index].toInt() and 0xFF) shl 8) or (header[index + 1].toInt() and 0xFF)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
