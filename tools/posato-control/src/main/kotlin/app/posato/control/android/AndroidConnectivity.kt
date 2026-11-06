package app.posato.control.android

private val NETWORK_STATE = Regex("""\bni\{\S+ (\w+)""")
private val TRANSPORTS = Regex("""Transports: ([A-Z_|]+)""")
private val PHYSICAL_TRANSPORTS = setOf("WIFI", "CELLULAR", "ETHERNET")

/**
 * The transports of every connected network other than a VPN in `dumpsys connectivity`, such as WIFI or CELLULAR. A
 * VPN, Posato's own included, carries traffic only over one of these, so an empty list means the device is offline.
 */
internal fun connectedNetworks(dumpsys: String): List<String> = dumpsys.lineSequence()
    .filter { "NetworkAgentInfo{" in it }
    .filter { NETWORK_STATE.find(it)?.groupValues?.get(1) == "CONNECTED" }
    .mapNotNull { line -> TRANSPORTS.find(line)?.groupValues?.get(1)?.split('|') }
    .filter { "VPN" !in it }
    .mapNotNull { transports -> transports.firstOrNull { it in PHYSICAL_TRANSPORTS } }
    .toList()
