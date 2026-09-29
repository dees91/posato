package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import app.posato.control.vm.shellQuote
import app.posato.control.vm.vmAdminPassword
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class VmNetworkCommand :
    ControlCommand(
        "network",
        "Turn every network service in the guest off or on, for offline and reconnection runs; never touches the host.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val state by option("--state", help = "off disables every network service; on enables them all.").choice("off", "on").required()

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val script = "sudo -S -p '' /bin/sh -c ${shellQuote(toggleScript(state))}"
        val output = Tart(session.context).exec(line.cloneName, script, stdin = vmAdminPassword(session.context) + "\n")
        val services = listedServices(output.stdout)
        val wanted = state == "on"
        if (!output.succeeded || services.isEmpty() || services.any { service -> service.enabled != wanted }) {
            throw ControlException(
                ErrorCode.COMMAND_FAILED,
                "Turning the network $state in ${line.cloneName} failed with exit code ${output.exitCode}.",
                "Check that the guest runs and that the administrator password in the login Keychain is current.",
            )
        }
        return buildJsonObject {
            put("vm", line.cloneName)
            put("network", state)
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

private fun toggleScript(state: String): String {
    return "networksetup -listallnetworkservices | tail -n +2 | sed 's/^\\*//' | " +
        "while IFS= read -r service; do networksetup -setnetworkserviceenabled \"\$service\" $state || exit 1; done || exit 1; " +
        "networksetup -listallnetworkservices"
}

private const val DISABLED_MARK = "*"
