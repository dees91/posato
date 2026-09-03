package app.posato.control.apple

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import java.time.Duration

@Serializable
data class SimulatorDevice(
    val udid: String,
    val name: String,
    val state: String,
    val runtime: String,
)

class Simctl(
    private val context: RunContext
) {
    fun listDevices(): List<SimulatorDevice> {
        val output = context.subprocess.run(listOf(XCRUN, "simctl", "list", "devices", "available", "-j"))
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Listing simulators")
        return try {
            val devices = ControlJson.lenient.parseToJsonElement(output.stdout).jsonObject["devices"]?.jsonObject ?: return emptyList()
            devices.entries.filter { (runtime, _) -> runtime.contains("iOS") }.flatMap { (runtime, list) ->
                list.jsonArray.map { element ->
                    val device = element.jsonObject
                    SimulatorDevice(
                        udid = device.getValue("udid").jsonPrimitive.content,
                        name = device.getValue("name").jsonPrimitive.content,
                        state = device.getValue("state").jsonPrimitive.content,
                        runtime = runtime.substringAfterLast('.'),
                    )
                }
            }
        } catch (exception: SerializationException) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "Unreadable simctl output: ${exception.message}", cause = exception)
        }
    }

    fun boot(udid: String) {
        val output = context.subprocess.run(listOf(XCRUN, "simctl", "boot", udid))
        if (!output.succeeded && !output.stderr.contains("current state: Booted")) {
            output.requireSuccess(ErrorCode.COMMAND_FAILED, "Booting the simulator")
        }
        context.subprocess.run(listOf(XCRUN, "simctl", "bootstatus", udid, "-b"), timeout = BOOT_TIMEOUT)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Waiting for the simulator to boot")
        context.subprocess.run(listOf("/usr/bin/open", "-a", "Simulator"))
    }

    fun shutdown(udid: String) {
        context.subprocess.run(listOf(XCRUN, "simctl", "shutdown", udid)).requireSuccess(ErrorCode.COMMAND_FAILED, "Shutting the simulator down")
    }

    fun install(
        udid: String,
        app: Path
    ) {
        context.subprocess.run(listOf(XCRUN, "simctl", "install", udid, app.toString()), timeout = INSTALL_TIMEOUT)
            .requireSuccess(ErrorCode.INSTALL_FAILED, "Installing on the simulator")
    }

    fun uninstall(
        udid: String,
        bundleId: String
    ) {
        context.subprocess.run(
            listOf(XCRUN, "simctl", "uninstall", udid, bundleId),
        ).requireSuccess(ErrorCode.COMMAND_FAILED, "Uninstalling from the simulator")
    }

    fun launch(
        udid: String,
        bundleId: String,
        stdout: Path,
        stderr: Path,
        arguments: List<String>,
        environment: Map<String, String>
    ): Long? {
        val command =
            listOf(XCRUN, "simctl", "launch", "--terminate-running-process", "--stdout=$stdout", "--stderr=$stderr", udid, bundleId) + arguments
        val childEnvironment = environment.mapKeys { (key, _) -> "SIMCTL_CHILD_$key" }
        val output = context.subprocess.run(command, environment = childEnvironment)
            .requireSuccess(
                ErrorCode.COMMAND_FAILED,
                "Launching on the simulator",
                "Install the application first with `posato-control install -t simulator`.",
            )
        return output.stdout.trim().substringAfterLast(':').trim().toLongOrNull()
    }

    fun terminate(
        udid: String,
        bundleId: String
    ) {
        context.subprocess.run(listOf(XCRUN, "simctl", "terminate", udid, bundleId))
    }

    fun appContainer(
        udid: String,
        bundleId: String,
        kind: String
    ): Path? {
        val output = context.subprocess.run(listOf(XCRUN, "simctl", "get_app_container", udid, bundleId, kind))
        return output.stdout.trim().takeIf { output.succeeded && it.isNotEmpty() }?.let { Path.of(it) }
    }

    fun screenshot(
        udid: String,
        destination: Path
    ) {
        context.subprocess.run(listOf(XCRUN, "simctl", "io", udid, "screenshot", destination.toString()))
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Capturing the simulator screen")
    }

    fun streamLog(
        udid: String,
        seconds: Int
    ): List<String> {
        val command = listOf(XCRUN, "simctl", "spawn", udid, "log", "stream", "--style", "compact", "--predicate", "process == \"Posato\"")
        val output = context.subprocess.captureFor(command, Duration.ofSeconds(seconds.toLong()))
        return output.lines().filter { it.isNotBlank() }
    }

    companion object {
        const val XCRUN = "/usr/bin/xcrun"
        private val BOOT_TIMEOUT = Duration.ofMinutes(3)
        private val INSTALL_TIMEOUT = Duration.ofMinutes(3)
    }
}
