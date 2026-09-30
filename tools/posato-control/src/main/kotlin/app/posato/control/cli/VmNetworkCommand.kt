package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import app.posato.control.vm.shellQuote
import app.posato.control.vm.vmAdminPassword
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class VmNetworkCommand :
    ControlCommand(
        "network",
        "Turn every network service in the guest off or on, or create, enable, disable, or remove one named " +
            "service, for offline, reconnection, and service-change runs; never touches the host.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, legacy, or ventura.").default(VmLine.PRIMARY.id)
    private val state by option("--state", help = "off disables every network service; on enables them all.").choice("off", "on")
    private val service by option("--service", help = "Name of the one network service --action changes.")
    private val action by option("--action", help = "What to do with --service.").choice("create", "enable", "disable", "remove")
    private val device by option("--device", help = "Hardware port a created service uses.").default("en0")

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val serviceName = service
        val serviceAction = action
        val request = when {
            state != null && serviceName == null && serviceAction == null -> NetworkRequest.All(state == "on")
            state == null && !serviceName.isNullOrBlank() && serviceAction != null -> NetworkRequest.One(serviceName, serviceAction, device)
            else -> throw ControlException(ErrorCode.USAGE, "vm network needs either --state, or --service with --action.")
        }
        val script = "sudo -S -p '' /bin/sh -c ${shellQuote(request.script())}"
        val output = Tart(session.context).exec(line.cloneName, script, stdin = vmAdminPassword(session.context) + "\n")
        val services = listedServices(output.stdout)
        if (!output.succeeded || !request.reached(services)) {
            throw ControlException(
                ErrorCode.COMMAND_FAILED,
                "${request.description} in ${line.cloneName} failed with exit code ${output.exitCode}.",
                "Check that the guest runs and that the administrator password in the login Keychain is current.",
            )
        }
        return buildJsonObject {
            put("vm", line.cloneName)
            request.describe(this)
            putJsonArray("services") {
                for (service in services) {
                    addJsonObject {
                        put("name", service.name)
                        put("enabled", service.enabled)
                    }
                }
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

    fun reached(services: List<GuestNetworkService>): Boolean

    fun describe(result: JsonObjectBuilder)

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

        override fun reached(services: List<GuestNetworkService>): Boolean {
            return services.isNotEmpty() && services.all { service -> service.enabled == enabled }
        }

        override fun describe(result: JsonObjectBuilder) {
            result.put("network", state)
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

        override fun reached(services: List<GuestNetworkService>): Boolean {
            val listed = services.firstOrNull { service -> service.name == name }
            return when (action) {
                "create", "enable" -> listed?.enabled == true
                "disable" -> listed?.enabled == false
                else -> listed == null
            }
        }

        override fun describe(result: JsonObjectBuilder) {
            result.put("service", name)
            result.put("action", action)
        }
    }
}

private const val LIST_SERVICES = "networksetup -listallnetworkservices"

private const val DISABLED_MARK = "*"
