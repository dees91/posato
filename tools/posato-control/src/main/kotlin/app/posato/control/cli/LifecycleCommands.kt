package app.posato.control.cli

import app.posato.control.apple.Devicectl
import app.posato.control.apple.PhysicalDevice
import app.posato.control.apple.Simctl
import app.posato.control.apple.SimulatorDevice
import app.posato.control.backend.BuildOptions
import app.posato.control.backend.BuildResult
import app.posato.control.backend.LaunchOptions
import app.posato.control.backend.LaunchResult
import app.posato.control.backend.StatusResult
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

class BuildCommand : ControlCommand("build", "Build the application for the target (desktop: staged package; iOS: xcodebuild).") {
    private val signingIdentity by option("--signing-identity", help = "macOS signing identity override for the staged package.")
    private val verify by option("--verify", help = "Run the strict desktop packaging verification after staging.").flag()
    private val driver by option("--driver", help = "Also build the iOS XCUITest driver for the target.").flag()

    override fun execute(session: Session): JsonElement = ControlJson.pretty.encodeToJsonElement(
        BuildResult.serializer(),
        session.backend().build(BuildOptions(signingIdentity, verify, driver)),
    )
}

class InstallCommand : ControlCommand("install", "Install the built application on the simulator or device.") {
    override fun execute(session: Session): JsonElement =
        ControlJson.pretty.encodeToJsonElement(StatusResult.serializer(), session.backend().install())
}

class LaunchCommand : ControlCommand("launch", "Launch the application and track its process.") {
    private val fresh by option("--fresh", help = "Reset the application state before launching.").flag()
    private val captureLogs by option(
        "--capture-logs",
        help = "Keep a console attachment (device) or capture stdout/stderr (desktop, simulator).",
    ).flag()
    private val build by option("--build", help = "Build (and install) before launching.").flag()
    private val arguments by option("--arg", help = "Launch argument; repeatable.").multiple()
    private val environment by option("--env", help = "Launch environment variable KEY=VALUE; repeatable.").associate()

    override fun execute(session: Session): JsonElement = ControlJson.pretty.encodeToJsonElement(
        LaunchResult.serializer(),
        session.backend().launch(LaunchOptions(fresh, captureLogs, build, arguments, environment)),
    )
}

class TerminateCommand : ControlCommand("terminate", "Terminate the application process this tool started.") {
    override fun execute(session: Session): JsonElement =
        ControlJson.pretty.encodeToJsonElement(StatusResult.serializer(), session.backend().terminate())
}

class StatusCommand : ControlCommand("status", "Report installation, process, and signing state for the target.") {
    override fun execute(session: Session): JsonElement =
        ControlJson.pretty.encodeToJsonElement(StatusResult.serializer(), session.backend().status())
}

@Serializable
data class DeviceInventory(
    val simulators: List<SimulatorDevice>,
    val devices: List<PhysicalDevice>,
)

class DevicesCommand : CliktCommand(name = "devices") {
    override fun help(context: Context): String = "List, boot, or shut down simulators and list paired iPhones."

    override fun run() = Unit
}

class DevicesListCommand : ControlCommand("list", "List available simulators and paired physical devices.") {
    override fun execute(session: Session): JsonElement {
        val inventory = DeviceInventory(Simctl(session.context).listDevices(), Devicectl(session.context).listDevices())
        return ControlJson.pretty.encodeToJsonElement(DeviceInventory.serializer(), inventory)
    }
}

class DevicesBootCommand : ControlCommand("boot", "Boot a simulator (the configured one, or by --device-type name).") {
    private val deviceType by option("--device-type", help = "Simulator name, e.g. \"iPhone 17\".")

    override fun execute(session: Session): JsonElement {
        val simctl = Simctl(session.context)
        val devices = simctl.listDevices()
        val name = deviceType ?: session.options.udid ?: session.configuration.value(app.posato.control.core.ConfigurationKey.SIMULATOR)
        val chosen = if (name != null) {
            devices.firstOrNull { it.name == name || it.udid == name }
                ?: throw ControlException(
                    ErrorCode.NO_BOOTED_SIMULATOR,
                    "No available simulator is named '$name'.",
                    "Run `posato-control devices list`.",
                )
        } else {
            devices.firstOrNull { it.state == "Booted" } ?: devices.firstOrNull { it.name.startsWith("iPhone") } ?: devices.firstOrNull()
                ?: throw ControlException(ErrorCode.NO_BOOTED_SIMULATOR, "No iOS simulator is available.", "Install an iOS runtime in Xcode.")
        }
        simctl.boot(chosen.udid)
        return ControlJson.pretty.encodeToJsonElement(
            ListSerializer(SimulatorDevice.serializer()),
            simctl.listDevices().filter {
                it.state == "Booted"
            },
        )
    }
}

class DevicesShutdownCommand : ControlCommand("shutdown", "Shut down every booted simulator.") {
    override fun execute(session: Session): JsonElement {
        val simctl = Simctl(session.context)
        simctl.listDevices().filter { it.state == "Booted" }.forEach { simctl.shutdown(it.udid) }
        return ControlJson.pretty.encodeToJsonElement(ListSerializer(SimulatorDevice.serializer()), simctl.listDevices())
    }
}
