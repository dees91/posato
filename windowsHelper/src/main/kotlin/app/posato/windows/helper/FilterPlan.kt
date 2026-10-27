package app.posato.windows.helper

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Which addresses a pause blocks: those that the paused hosts and their `www` names resolve to. Local, private, and
 * multicast addresses are never blocked, so a pause cannot cut the computer off its own network, and a running pause
 * only adds addresses, up to [MAX_ADDRESSES].
 */
internal object FilterPlan {
    const val MAX_ADDRESSES = 16_384
    private const val WWW = "www."

    fun names(hosts: List<String>): List<String> {
        return hosts.flatMap { host -> if (host.startsWith(WWW)) listOf(host) else listOf(host, WWW + host) }.distinct()
    }

    /** [current] followed by every blockable address of [resolved] that is new, in order, up to the limit. */
    fun extend(
        current: Set<InetAddress>,
        resolved: Collection<InetAddress>,
    ): Set<InetAddress> {
        val plan = LinkedHashSet(current)
        for (address in resolved) {
            if (plan.size >= MAX_ADDRESSES) break
            normalize(address).takeIf(::isBlockable)?.let(plan::add)
        }
        return plan
    }

    /** An IPv4-mapped IPv6 address reaches the IPv4 address it carries, so it is planned and judged as that address. */
    private fun normalize(address: InetAddress): InetAddress {
        val bytes = address.address
        val mapped = address is Inet6Address && bytes.size == IPV6_BYTES &&
            (0 until MAPPED_PREFIX).all { bytes[it] == 0.toByte() } && bytes[MAPPED_PREFIX] == FF && bytes[MAPPED_PREFIX + 1] == FF
        return if (mapped) InetAddress.getByAddress(bytes.copyOfRange(IPV6_BYTES - IPV4_BYTES, IPV6_BYTES)) else address
    }

    private fun isBlockable(address: InetAddress): Boolean {
        val local = address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress
        return !local && when (address) {
            is Inet4Address -> !isReservedV4(address.address)
            else -> !isReservedV6(address.address)
        }
    }

    private fun isReservedV4(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and BYTE
        val second = bytes[1].toInt() and BYTE
        return first == 0 || first == LOOPBACK || first == PRIVATE_A ||
            (first == PRIVATE_B && second in PRIVATE_B_SECOND) ||
            (first == PRIVATE_C_FIRST && second == PRIVATE_C_SECOND) ||
            (first == SHARED_FIRST && second in SHARED_SECOND) ||
            (first == LINK_LOCAL_FIRST && second == LINK_LOCAL_SECOND) ||
            first >= MULTICAST_AND_ABOVE
    }

    /** Unique local addresses (`fc00::/7`) are private; link-local, loopback, and multicast are caught above. */
    private fun isReservedV6(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and BYTE
        return first and UNIQUE_LOCAL_MASK == UNIQUE_LOCAL || first == V6_MULTICAST ||
            (first == LINK_LOCAL_V6_FIRST && (bytes[1].toInt() and LINK_LOCAL_V6_MASK) == LINK_LOCAL_V6_SECOND)
    }

    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private const val MAPPED_PREFIX = 10
    private const val FF = 0xFF.toByte()
    private const val BYTE = 0xFF
    private const val LOOPBACK = 127
    private const val PRIVATE_A = 10
    private const val PRIVATE_B = 172
    private val PRIVATE_B_SECOND = 16..31
    private const val PRIVATE_C_FIRST = 192
    private const val PRIVATE_C_SECOND = 168
    private const val SHARED_FIRST = 100
    private val SHARED_SECOND = 64..127
    private const val LINK_LOCAL_FIRST = 169
    private const val LINK_LOCAL_SECOND = 254
    private const val MULTICAST_AND_ABOVE = 224
    private const val UNIQUE_LOCAL_MASK = 0xFE
    private const val UNIQUE_LOCAL = 0xFC
    private const val V6_MULTICAST = 0xFF
    private const val LINK_LOCAL_V6_FIRST = 0xFE
    private const val LINK_LOCAL_V6_MASK = 0xC0
    private const val LINK_LOCAL_V6_SECOND = 0x80
}
