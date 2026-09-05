package app.posato.provisioning.local

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.core.Subprocess
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
 */
object LocalDevices {
    fun parseMacUdid(json: String): String? {
        val hardware = decode(json)?.get("SPHardwareDataType")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        return hardware["provisioning_UDID"]?.jsonPrimitive?.content
            ?: hardware["platform_UUID"]?.jsonPrimitive?.content
    }

    fun parseConnectedIphones(json: String): List<String> {
        val devices = decode(json)?.get("result")?.jsonObject?.get("devices")?.jsonArray ?: return emptyList()
        return devices
            .map { entry -> entry.jsonObject }
            .filter { device -> device["hardwareProperties"]?.jsonObject?.get("platform")?.jsonPrimitive?.content == "iOS" }
            .filter { device -> device["connectionProperties"]?.jsonObject?.get("tunnelState")?.jsonPrimitive?.content == "connected" }
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

/** Runs the two local tools that know these identifiers. Everything it reads is registered as a secret at once. */
class LocalDeviceReader(
    private val subprocess: Subprocess,
    private val register: (String) -> Unit,
) {
    fun mac(): LocalDevice? {
        val output = subprocess.run(listOf("/usr/sbin/system_profiler", "SPHardwareDataType", "-json"))
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
