package app.posato.windows.helper

import java.net.Inet6Address
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterPlanTest {
    @Test
    fun `given hosts when named then each host and its www counterpart are resolved once`() {
        assertEquals(
            listOf("example.org", "www.example.org", "www.news.example"),
            FilterPlan.names(listOf("example.org", "www.news.example", "example.org")),
        )
    }

    @Test
    fun `given public addresses when planned then each is blocked once in its own family`() {
        val plan = FilterPlan.blockable(listOf(address("93.184.216.34"), address("2606:2800:220:1::1"), address("93.184.216.34")))

        assertEquals(setOf(address("93.184.216.34"), address("2606:2800:220:1::1")), plan)
    }

    @Test
    fun `given addresses no pause may filter when planned then none of them is blocked`() {
        val local = listOf(
            "127.0.0.1",
            "127.255.0.9",
            "0.0.0.0",
            "0.1.2.3",
            "169.254.10.1",
            "224.0.0.251",
            "239.255.255.250",
            "255.255.255.255",
            "10.0.0.2",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.9",
            "100.64.0.1",
            "100.127.255.254",
            "::1",
            "::",
            "fe80::1",
            "febf::1",
            "ff02::fb",
            "fc00::1",
            "fd12:3456::1",
        ).map(::address)

        assertEquals(emptySet(), FilterPlan.blockable(local))
        assertEquals(
            setOf(address("172.32.0.1"), address("100.128.0.1"), address("11.0.0.1")),
            FilterPlan.blockable(listOf(address("172.32.0.1"), address("100.128.0.1"), address("11.0.0.1"))),
        )
    }

    @Test
    fun `given IPv4-mapped IPv6 addresses when planned then they count as the IPv4 address they carry`() {
        val mappedLoopback = mapped(127, 0, 0, 1)
        val mappedPrivate = mapped(192, 168, 0, 1)
        val mappedPublic = mapped(93, 184, 216, 34)

        val plan = FilterPlan.blockable(listOf(mappedLoopback, mappedPrivate, mappedPublic))

        assertEquals(setOf(address("93.184.216.34")), plan)
    }

    private fun address(literal: String): InetAddress = InetAddress.getByName(literal)

    private fun mapped(vararg octets: Int): InetAddress {
        val bytes = ByteArray(16)
        bytes[10] = 0xFF.toByte()
        bytes[11] = 0xFF.toByte()
        octets.forEachIndexed { index, octet -> bytes[12 + index] = octet.toByte() }
        return Inet6Address.getByAddress(null, bytes, -1)
    }
}
