package app.posato.control.android

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A phone with Wi-Fi out of range and mobile data off keeps only Posato's VPN, and every website then reads as
 * unreachable, which looks like a product defect. The networks come from `dumpsys connectivity`; counting the VPN or
 * a network that is not connected would hide that state again.
 */
class AndroidConnectivityTest {
    @Test
    fun `connected Wi-Fi and mobile data count as networks`() {
        val dump =
            """
            NetworkAgents: 2
              NetworkAgentInfo{network{100}  handle{1}  ni{WIFI CONNECTED extra: } created=2026-11-06T09:36:05.099Z  nc{[ Transports: WIFI Capabilities: INTERNET&NOT_VPN&VALIDATED OwnerUid: 1000 UnderlyingNetworks: Null]}  factorySerialNumber=6}
              NetworkAgentInfo{network{101}  handle{2}  ni{MOBILE[LTE] CONNECTED extra: example.apn} created=2026-11-06T09:36:05.724Z  nc{[ Transports: CELLULAR Capabilities: INTERNET&NOT_VPN&VALIDATED UnderlyingNetworks: Null]}  factorySerialNumber=5}
            """.trimIndent()

        assertEquals(listOf("WIFI", "CELLULAR"), connectedNetworks(dump))
    }

    @Test
    fun `a VPN alone or a network that is not connected is no network`() {
        val dump =
            """
            NetworkAgents: 2
              NetworkAgentInfo{network{110}  handle{3}  ni{VPN[] CONNECTED extra: } created=2026-11-06T10:00:00.000Z  nc{[ Transports: VPN Capabilities: INTERNET&NOT_METERED OwnerUid: 10200 UnderlyingNetworks: []]}  factorySerialNumber=7}
              NetworkAgentInfo{network{111}  handle{4}  ni{WIFI CONNECTING extra: } created=2026-11-06T10:00:01.000Z  nc{[ Transports: WIFI Capabilities: NOT_VPN UnderlyingNetworks: Null]}  factorySerialNumber=8}
            """.trimIndent()

        assertEquals(emptyList<String>(), connectedNetworks(dump))
    }
}
