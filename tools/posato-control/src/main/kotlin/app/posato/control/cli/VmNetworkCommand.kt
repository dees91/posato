package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import app.posato.control.vm.parseBypassDomains
import app.posato.control.vm.shellQuote
import app.posato.control.vm.vmAdminPassword
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class VmNetworkCommand :
    ControlCommand(
        "network",
        "Turn every network service in the guest off or on, create, enable, disable, or remove one named service, " +
            "or read or set its proxy bypass domains, for offline, reconnection, service-change, and proxy-exception " +
            "runs; never touches the host.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, legacy, or ventura.").default(VmLine.PRIMARY.id)
    private val state by option("--state", help = "off disables every network service; on enables them all.").choice("off", "on")
    private val service by option("--service", help = "Name of the one network service --action changes.")
    private val action by option("--action", help = "What to do with --service; show-bypass only reads its bypass domains.")
        .choice("create", "enable", "disable", "remove", SHOW_BYPASS)
    private val bypassDomains by option("--bypass-domains", help = "Comma-separated proxy bypass domains to set on --service, in order.")
    private val bypassEmpty by option("--bypass-empty", help = "Clear the proxy bypass domains of --service.").flag()
    private val bypassAbsent by option(
        "--bypass-absent",
        help = "Remove the ExceptionsList key of --service entirely, which --bypass-empty cannot do.",
    ).flag()
    private val device by option("--device", help = "Hardware port a created service uses.").default("en0")

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val request = networkRequest(state, service, action, bypassChange(bypassDomains, bypassEmpty, bypassAbsent), device)
        val script = "sudo -S -p '' /bin/sh -c ${shellQuote(request.script())}"
        val output = Tart(session.context).exec(line.cloneName, script, stdin = vmAdminPassword(session.context) + "\n")
        if (!output.succeeded || !request.reached(output.stdout)) {
            throw ControlException(
                ErrorCode.COMMAND_FAILED,
                "${request.description} in ${line.cloneName} failed with exit code ${output.exitCode}.",
                "Check that the guest runs and that the administrator password in the login Keychain is current.",
            )
        }
        return buildJsonObject {
            put("vm", line.cloneName)
            request.describe(this, output.stdout)
        }
    }
}

/** The requested bypass change: null when none is requested. */
private fun bypassChange(
    domains: String?,
    empty: Boolean,
    absent: Boolean
): BypassChange? {
    if (listOf(domains != null, empty, absent).count { requested -> requested } > 1) {
        throw ControlException(ErrorCode.USAGE, "Pass only one of --bypass-domains, --bypass-empty, and --bypass-absent.")
    }
    return when {
        absent -> BypassChange.Absent
        empty -> BypassChange.Domains(emptyList())
        domains != null -> BypassChange.Domains(domains.split(',').map(String::trim).filter(String::isNotEmpty))
        else -> null
    }
}

private sealed interface BypassChange {
    data class Domains(
        val domains: List<String>
    ) : BypassChange

    data object Absent : BypassChange
}

private fun networkRequest(
    state: String?,
    service: String?,
    action: String?,
    bypass: BypassChange?,
    device: String
): NetworkRequest {
    val name = service?.takeIf(String::isNotBlank)
    val request = when {
        name == null -> state?.takeIf { action == null && bypass == null }?.let { NetworkRequest.All(it == "on") }
        state != null -> null
        bypass != null -> NetworkRequest.Bypass(name, bypass).takeIf { action == null }
        action == SHOW_BYPASS -> NetworkRequest.Bypass(name, null)
        action != null -> NetworkRequest.One(name, action, device)
        else -> null
    }
    return request ?: throw ControlException(
        ErrorCode.USAGE,
        "vm network needs either --state, or --service with --action, --bypass-domains, --bypass-empty, or --bypass-absent.",
    )
}

private fun JsonObjectBuilder.putServices(stdout: String) {
    putJsonArray("services") {
        for (service in listedServices(stdout)) {
            addJsonObject {
                put("name", service.name)
                put("enabled", service.enabled)
            }
        }
    }
}

private data class GuestNetworkService(
    val name: String,
    val enabled: Boolean,
)

private fun listedServices(listing: String): List<GuestNetworkService> {
    return listing.lines()
        .drop(1)
        .filter { line -> line.isNotBlank() }
        .map { line -> GuestNetworkService(line.removePrefix(DISABLED_MARK), !line.startsWith(DISABLED_MARK)) }
}

private sealed interface NetworkRequest {
    val description: String

    fun script(): String

    fun reached(stdout: String): Boolean

    fun describe(
        result: JsonObjectBuilder,
        stdout: String
    )

    data class All(
        val enabled: Boolean
    ) : NetworkRequest {
        private val state = if (enabled) "on" else "off"

        override val description = "Turning the network $state"

        override fun script(): String {
            return "networksetup -listallnetworkservices | tail -n +2 | sed 's/^\\*//' | " +
                "while IFS= read -r service; do networksetup -setnetworkserviceenabled \"\$service\" $state || exit 1; done || exit 1; " +
                LIST_SERVICES
        }

        override fun reached(stdout: String): Boolean {
            val services = listedServices(stdout)
            return services.isNotEmpty() && services.all { service -> service.enabled == enabled }
        }

        override fun describe(
            result: JsonObjectBuilder,
            stdout: String
        ) {
            result.put("network", state)
            result.putServices(stdout)
        }
    }

    data class One(
        val name: String,
        val action: String,
        val device: String
    ) : NetworkRequest {
        override val description = "Network service action $action for \"$name\""

        override fun script(): String {
            val quoted = shellQuote(name)
            val change = when (action) {
                "create" -> "networksetup -createnetworkservice $quoted ${shellQuote(device)}"
                "enable" -> "networksetup -setnetworkserviceenabled $quoted on"
                "disable" -> "networksetup -setnetworkserviceenabled $quoted off"
                else -> "networksetup -removenetworkservice $quoted"
            }
            return "$change || exit 1; $LIST_SERVICES"
        }

        override fun reached(stdout: String): Boolean {
            val listed = listedServices(stdout).firstOrNull { service -> service.name == name }
            return when (action) {
                "create", "enable" -> listed?.enabled == true
                "disable" -> listed?.enabled == false
                else -> listed == null
            }
        }

        override fun describe(
            result: JsonObjectBuilder,
            stdout: String
        ) {
            result.put("service", name)
            result.put("action", action)
            result.putServices(stdout)
        }
    }

    /** Reads, or changes and then reads, one service's proxy bypass domains; `change` null only reads them. */
    data class Bypass(
        val name: String,
        val change: BypassChange?
    ) : NetworkRequest {
        override val description = if (change == null) "Reading the bypass domains of \"$name\"" else "Changing the bypass domains of \"$name\""

        override fun script(): String {
            val quoted = shellQuote(name)
            val read = "networksetup -getproxybypassdomains $quoted"
            return when (change) {
                null -> {
                    read
                }

                BypassChange.Absent -> {
                    REMOVE_EXCEPTIONS_KEY.replace(SERVICE_PLACEHOLDER, quoted) + " || exit 1; $read"
                }

                is BypassChange.Domains -> {
                    val values = if (change.domains.isEmpty()) "Empty" else change.domains.joinToString(" ") { shellQuote(it) }
                    "networksetup -setproxybypassdomains $quoted $values || exit 1; $read"
                }
            }
        }

        override fun reached(stdout: String): Boolean = when (change) {
            null -> true
            BypassChange.Absent -> parseBypassDomains(stdout).isEmpty()
            is BypassChange.Domains -> parseBypassDomains(stdout) == change.domains
        }

        override fun describe(
            result: JsonObjectBuilder,
            stdout: String
        ) {
            result.put("service", name)
            result.putJsonArray("bypassDomains") { parseBypassDomains(stdout).forEach { domain -> add(JsonPrimitive(domain)) } }
        }
    }
}

private const val SHOW_BYPASS = "show-bypass"

private const val SERVICE_PLACEHOLDER = "@SERVICE@"

/**
 * Removes `ExceptionsList` from the named service's stored proxies with `scutil --prefs`, which `networksetup` cannot
 * do, and fails unless the key is gone afterwards.
 */
private val REMOVE_EXCEPTIONS_KEY =
    """
    id=''
    for path in ${'$'}(printf 'list /NetworkServices\n' | scutil --prefs | awk '{print ${'$'}4}'); do
      name=${'$'}(printf 'get %s\nd.show\n' "${'$'}path" | scutil --prefs | sed -n 's/^  UserDefinedName : //p')
      if [ "${'$'}name" = @SERVICE@ ]; then id="${'$'}path"; fi
    done
    [ -n "${'$'}id" ] || exit 1
    printf 'lock\nget %s/Proxies\nd.remove ExceptionsList\nset %s/Proxies\ncommit\napply\nunlock\nquit\n' "${'$'}id" "${'$'}id" | scutil --prefs || exit 1
    ! printf 'get %s/Proxies\nd.show\n' "${'$'}id" | scutil --prefs | grep -q ExceptionsList
    """.trimIndent()

private const val LIST_SERVICES = "networksetup -listallnetworkservices"

private const val DISABLED_MARK = "*"
