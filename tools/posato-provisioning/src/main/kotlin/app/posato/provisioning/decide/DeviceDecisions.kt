package app.posato.provisioning.decide

import app.posato.provisioning.local.LocalDevice
import app.posato.provisioning.model.DeviceResource

/** What registering this device would do, decided before anything is sent. */
enum class DeviceOutcome { REGISTERED, ALREADY_REGISTERED, DISABLED }

data class DeviceDecision(
    val device: LocalDevice,
    val outcome: DeviceOutcome,
) {
    val requiresCreate: Boolean get() = outcome == DeviceOutcome.REGISTERED
}

/**
 * Whether each device this Mac can see is already in the account.
 *
 * A device the account holds but has disabled is reported rather than created again: creating it a second time
 * would either fail as a conflict or consume another of the team's limited device slots, and only the maintainer
 * can decide to re-enable it in the portal.
 */
object DeviceDecisions {
    fun decide(
        local: List<LocalDevice>,
        registered: List<DeviceResource>
    ): List<DeviceDecision> = local.map { device ->
        val existing = registered.firstOrNull { resource -> resource.attributes.udid.equals(device.udid, ignoreCase = true) }
        val outcome = when {
            existing == null -> DeviceOutcome.REGISTERED
            existing.enabled -> DeviceOutcome.ALREADY_REGISTERED
            else -> DeviceOutcome.DISABLED
        }
        DeviceDecision(device, outcome)
    }
}
