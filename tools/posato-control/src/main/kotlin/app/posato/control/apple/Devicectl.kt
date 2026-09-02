package app.posato.control.apple

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Serializable
data class PhysicalDevice(
    val udid: String,
    val name: String,
    val model: String? = null,
    val osVersion: String? = null,
    val connected: Boolean,
    val developerMode: String? = null,
)

class Devicectl(
    private val context: RunContext
) {
    fun listDevices(): List<PhysicalDevice> {
        val result = json(listOf("list", "devices"), ErrorCode.COMMAND_FAILED, "Listing devices")
        val devices = result["devices"]?.jsonArray ?: return emptyList()
        return devices.map { element ->
            val device = element.jsonObject
            val properties = device["deviceProperties"]?.jsonObject
            val connection = device["connectionProperties"]?.jsonObject
            val hardware = device["hardwareProperties"]?.jsonObject
            PhysicalDevice(
                udid = device.getValue("identifier").jsonPrimitive.content,
                name = properties?.get("name")?.jsonPrimitive?.content ?: "unknown",
                model = hardware?.get("marketingName")?.jsonPrimitive?.content ?: hardware?.get("productType")?.jsonPrimitive?.content,
                osVersion = properties?.get("osVersionNumber")?.jsonPrimitive?.content,
                connected = connection?.get("tunnelState")?.jsonPrimitive?.content == "connected",
                developerMode = properties?.get("developerModeStatus")?.jsonPrimitive?.content,
            )
        }
    }

    fun install(
        udid: String,
        app: Path
    ) {
        json(
            listOf("device", "install", "app", "--device", udid, app.toString()),
            ErrorCode.INSTALL_FAILED,
            "Installing on the device",
            INSTALL_TIMEOUT,
        )
    }

    fun uninstall(
        udid: String,
        bundleId: String
    ) {
        json(listOf("device", "uninstall", "app", "--device", udid, bundleId), ErrorCode.COMMAND_FAILED, "Uninstalling from the device")
    }

    fun installedBundles(udid: String): List<String> {
        val result = json(listOf("device", "info", "apps", "--device", udid), ErrorCode.COMMAND_FAILED, "Listing device applications")
        return result["apps"]?.jsonArray?.mapNotNull { it.jsonObject["bundleIdentifier"]?.jsonPrimitive?.content } ?: emptyList()
    }

    fun launch(
        udid: String,
        bundleId: String,
        arguments: List<String>,
        environment: Map<String, String>
    ): Long? {
        val command = buildList {
            addAll(listOf("device", "process", "launch", "--device", udid, "--terminate-existing", "--activate"))
            if (environment.isNotEmpty()) {
                add("--environment-variables")
                add(ControlJson.compact.encodeToString(MapSerializer(String.serializer(), String.serializer()), environment))
            }
            add(bundleId)
            addAll(arguments)
        }
        val result = json(command, ErrorCode.COMMAND_FAILED, "Launching on the device", hint = "Unlock the iPhone and keep it connected.")
        return result["process"]?.jsonObject?.get("processIdentifier")?.jsonPrimitive?.content?.toLongOrNull()
    }

    fun startConsole(
        udid: String,
        bundleId: String,
        logFile: Path
    ): Process = context.subprocess.startDetached(
        listOf(
            Simctl.XCRUN,
            "devicectl",
            "device",
            "process",
            "launch",
            "--device",
            udid,
            "--terminate-existing",
            "--activate",
            "--console",
            bundleId,
        ),
        logFile,
    )

    fun terminate(
        udid: String,
        pid: Long
    ) {
        json(
            listOf("device", "process", "terminate", "--device", udid, "--pid", pid.toString()),
            ErrorCode.COMMAND_FAILED,
            "Terminating the device process",
        )
    }

    private fun json(
        arguments: List<String>,
        code: ErrorCode,
        what: String,
        timeout: Duration = DEFAULT_TIMEOUT,
        hint: String? = null
    ): JsonObject {
        val outputFile = Files.createTempFile("posato-control-devicectl", ".json")
        try {
            val command = listOf(Simctl.XCRUN, "devicectl") + arguments + listOf("--json-output", outputFile.toString())
            context.subprocess.run(command, timeout = timeout).requireSuccess(code, what, hint)
            val root = parse(Files.readString(outputFile))
            return root["result"]?.jsonObject ?: JsonObject(emptyMap())
        } finally {
            Files.deleteIfExists(outputFile)
        }
    }

    private fun parse(text: String): JsonObject = try {
        val element: JsonElement = ControlJson.lenient.parseToJsonElement(text)
        element.jsonObject
    } catch (exception: SerializationException) {
        throw ControlException(ErrorCode.COMMAND_FAILED, "Unreadable devicectl output: ${exception.message}", cause = exception)
    }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(90)
        val INSTALL_TIMEOUT: Duration = Duration.ofMinutes(5)
    }
}
