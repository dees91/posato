package app.posato.provisioning.local

import app.posato.provisioning.core.CommandRunner
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.ApplePlatform
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.readText

data class LocalDevice(
    val udid: String,
    val platform: ApplePlatform,
)

/**
 * The identifiers Apple accepts when registering a device, read from this Mac.
 *
 * Both sources are easy to get wrong in the same way. A Mac's Provisioning UDID is not its Platform UUID: on Apple
 * silicon they are different values of different lengths, and registering the Platform UUID produces an account
 * record no profile will ever match. An iPhone's Provisioning UDID is `hardwareProperties.udid` and not the
 * `identifier` field, which is a devicectl connection identifier. Both parsers take the right field and are pinned
 * by tests over recorded shapes.
 *
 * "Connected" means wired. A phone paired over the local network also reports a tunnel, and that tunnel's state
 * flips between readings, so selecting on it would register a different set of devices depending on when the
 * command ran. Requiring the cable makes the selection deterministic and keeps a phone that merely shares the
 * network from consuming one of the team's limited device slots.
 */
object LocalDevices {
    fun parseMacUdid(json: String): String? {
        val hardware = decode(json)?.get("SPHardwareDataType")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        // There is deliberately no fall back to platform_UUID. Apple accepts it, so the registration would appear to
        // succeed, doctor would report the Mac as registered, and every profile built afterwards would name a device
        // record that matches nothing -- while permanently consuming one of the team's device slots. Reporting
        // nothing lets doctor say the identifier could not be read, which is recoverable.
        return hardware["provisioning_UDID"]?.jsonPrimitive?.content
    }

    fun parseConnectedIphones(json: String): List<String> {
        val devices = decode(json)?.get("result")?.jsonObject?.get("devices")?.jsonArray ?: return emptyList()
        return devices
            .map { entry -> entry.jsonObject }
            .filter { device -> device["hardwareProperties"]?.jsonObject?.get("platform")?.jsonPrimitive?.content == "iOS" }
            .filter { device -> device["connectionProperties"]?.jsonObject?.get("transportType")?.jsonPrimitive?.content == "wired" }
            .mapNotNull { device -> device["hardwareProperties"]?.jsonObject?.get("udid")?.jsonPrimitive?.content }
            .distinct()
    }

    private fun decode(json: String): JsonObject? = try {
        ProvisioningJson.lenient.decodeFromString(JsonObject.serializer(), json)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Runs the local tools that know these identifiers. Everything it reads is registered as a secret at once. */
class LocalDeviceReader(
    private val subprocess: CommandRunner,
    private val register: (String) -> Unit,
) {
    fun mac(): LocalDevice? = hardware(emptyList())

    /**
     * A running Tart macOS guest, read through `tart exec` and `tart-guest-agent`. Clones of that guest inherit its
     * identifier, so one registration covers every disposable clone of a golden VM that never runs beside them.
     */
    fun tartVm(name: String): LocalDevice? = hardware(listOf("tart", "exec", name))

    private fun hardware(prefix: List<String>): LocalDevice? {
        val output = subprocess.run(prefix + listOf("/usr/sbin/system_profiler", "SPHardwareDataType", "-json"))
        if (!output.succeeded) return null
        return LocalDevices.parseMacUdid(output.stdout)?.let { udid ->
            register(udid)
            LocalDevice(udid, ApplePlatform.MACOS)
        }
    }

    fun connectedIphones(): List<LocalDevice> {
        val output = Files.createTempFile("posato-provisioning-devices", ".json")
        return try {
            val result = subprocess.run(listOf("/usr/bin/xcrun", "devicectl", "list", "devices", "--json-output", output.toString()))
            result.requireSuccess(ErrorCode.DEVICE_UNAVAILABLE, "Listing connected devices", "Confirm Xcode command line tools are installed.")
            LocalDevices.parseConnectedIphones(output.readText()).map { udid ->
                register(udid)
                LocalDevice(udid, ApplePlatform.IOS)
            }
        } finally {
            Files.deleteIfExists(output)
        }
    }
}
