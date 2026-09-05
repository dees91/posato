package app.posato.provisioning.cli

import app.posato.provisioning.decide.DeviceDecision
import app.posato.provisioning.decide.DeviceDecisions
import app.posato.provisioning.decide.DeviceOutcome
import app.posato.provisioning.model.ApplePlatform
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAC_NAME = "Posato Development Mac"
private const val IPHONE_NAME = "Posato Development iPhone"

class DevicesCommand : CliktCommand(name = "devices") {
    override fun help(context: Context): String = "Inspect and register the development devices this Mac can see."

    override fun run() = Unit
}

/**
 * Registers this Mac and every connected iPhone that the account does not already hold.
 *
 * The names sent to Apple are fixed rather than taken from the host or the device, so nothing personal is uploaded
 * alongside the identifier. No identifier is printed, returned, or written: the result says only what happened.
 */
class DevicesRegisterCommand :
    ProvisioningCommand(
        "register",
        "Registers this Mac and any connected iPhone in the Apple developer account when they are missing.",
    ) {
    override fun execute(session: Session): JsonElement {
        val client = session.ascClient()
        val local = listOfNotNull(session.devices.mac()) + session.devices.connectedIphones()
        val decisions = DeviceDecisions.decide(local, client.devices())
        decisions.filter { decision -> decision.requiresCreate }.forEach { decision ->
            val name = if (decision.device.platform == ApplePlatform.IOS) IPHONE_NAME else MAC_NAME
            client.createDevice(name, decision.device.platform, decision.device.udid)
        }
        return report(decisions)
    }

    private fun report(decisions: List<DeviceDecision>): JsonElement = buildJsonObject {
        put("mac", outcome(decisions, ApplePlatform.MACOS))
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
