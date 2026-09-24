package app.posato.provisioning.cli

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.decide.DeviceDecision
import app.posato.provisioning.decide.DeviceDecisions
import app.posato.provisioning.decide.DeviceOutcome
import app.posato.provisioning.model.ApplePlatform
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAC_NAME = "Posato Development Mac"
private const val IPHONE_NAME = "Posato Development iPhone"
private const val VM_NAME = "Posato Verification VM"

class DevicesCommand : CliktCommand(name = "devices") {
    override fun help(context: Context): String = "Inspect and register the development devices this Mac can see."

    override fun run() = Unit
}

/**
 * Registers this Mac, every connected iPhone, and each named running Tart verification VM that the account does not
 * already hold.
 *
 * The names sent to Apple are fixed rather than taken from the host or the device, so nothing personal is uploaded
 * alongside the identifier. No identifier is printed, returned, or written: the result says only what happened.
 */
class DevicesRegisterCommand :
    ProvisioningCommand(
        "register",
        "Registers this Mac, any connected iPhone, and the named Tart VMs in the Apple developer account when they are missing.",
    ) {
    private val tartVms by option(
        "--tart-vm",
        help = "Also register this running Tart macOS guest (repeatable), such as a golden verification VM.",
    ).multiple()

    override fun execute(session: Session): JsonElement {
        val client = session.ascClient()
        val mac = listOfNotNull(session.devices.mac())
        val vms = tartVms.map { name -> session.devices.tartVm(name) ?: throw unreadableVm(name) }
        // A clone taken before it was first booted shares its source's identifier; register it once.
        val local = (mac + session.devices.connectedIphones() + vms).distinctBy { device -> device.udid }
        val decisions = DeviceDecisions.decide(local, client.devices())
        decisions.filter { decision -> decision.requiresCreate }.forEach { decision ->
            val name = when {
                decision.device.platform == ApplePlatform.IOS -> IPHONE_NAME
                decision.device in vms -> VM_NAME
                else -> MAC_NAME
            }
            client.createDevice(name, decision.device.platform, decision.device.udid)
        }
        return report(decisions.filter { it.device !in vms }, decisions.filter { it.device in vms })
    }

    private fun unreadableVm(name: String) = ProvisioningException(
        ErrorCode.DEVICE_UNAVAILABLE,
        "Could not read the provisioning identifier of the Tart VM '$name'.",
        "Start it with `tart run $name --no-graphics` and confirm `tart exec $name true` succeeds.",
    )

    private fun report(
        decisions: List<DeviceDecision>,
        vms: List<DeviceDecision>
    ): JsonElement = buildJsonObject {
        put("mac", outcome(decisions, ApplePlatform.MACOS))
        put("vms", buildJsonArray { vms.forEach { decision -> add(JsonPrimitive(name(decision.outcome))) } })
        put(
            "iphones",
            buildJsonArray {
                decisions.filter { it.device.platform == ApplePlatform.IOS }.forEach { decision -> add(JsonPrimitive(name(decision.outcome))) }
            },
        )
    }

    private fun outcome(
        decisions: List<DeviceDecision>,
        platform: ApplePlatform
    ): String = decisions
        .firstOrNull { decision -> decision.device.platform == platform }
        ?.let { decision -> name(decision.outcome) }
        ?: "absent"

    private fun name(outcome: DeviceOutcome): String = outcome.name.lowercase().replace('_', '-')
}
