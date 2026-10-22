package app.posato.control.cli

import app.posato.control.android.AndroidBackend
import app.posato.control.android.AndroidDevice
import app.posato.control.android.AndroidGrants
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
import app.posato.control.core.Target
import app.posato.control.linux.LinuxBackend
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

class BuildCommand : ControlCommand("build", "Build the application for the target (desktop: staged package; iOS: xcodebuild).") {
    override val hostDesktopAllowed = true

    private val signingIdentity by option("--signing-identity", help = "macOS signing identity override for the staged package.")
    private val verify by option("--verify", help = "Run the strict desktop packaging verification after staging.").flag()
    private val driver by option("--driver", help = "Also build the iOS XCUITest driver for the target.").flag()
    private val verificationSeams by option(
        "--verification-seams",
        help = "Desktop: build the development package with the verification-only zone seams, for iCloud fixture runs only.",
    ).flag()

    override fun execute(session: Session): JsonElement = ControlJson.pretty.encodeToJsonElement(
        BuildResult.serializer(),
        session.backend().build(BuildOptions(signingIdentity, verify, driver, verificationSeams)),
    )
}

class InstallCommand : ControlCommand("install", "Install the built application on the simulator, device, Android, or the Linux clone.") {
    private val grants by option(
        "--grants",
        help = "Android: all (every grant, as for unattended runs), settings (only the Settings-page grants; " +
            "onboarding raises the VPN, notification, and battery requests), or none.",
    ).choice(AndroidGrants.choices)
    private val packageFile by option(
        "--package",
        help = "Android or Linux: install this built APK or .deb, such as a release candidate, instead of the development build.",
    ).path(mustExist = true, canBeDir = false)

    override fun execute(session: Session): JsonElement {
        val backend = session.backend()
        packageFile?.let { file ->
            when (backend) {
                is AndroidBackend -> backend.packageFile = file
                is LinuxBackend -> backend.packageFile = file
                else -> throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "--package applies to the android and linux targets.")
            }
        }
        grants?.let { chosen ->
            if (backend !is AndroidBackend) throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "--grants applies to the android target.")
            backend.grants = chosen
        }
        return ControlJson.pretty.encodeToJsonElement(StatusResult.serializer(), backend.install())
    }
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
    private val adopt by option(
        "--adopt",
        help = "Desktop in a VM: track the one running instance instead of starting one, such as the build an update relaunched.",
    ).flag()

    override fun execute(session: Session): JsonElement {
        if (adopt && session.target() != Target.DESKTOP) {
            throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "--adopt applies to the desktop target only.")
        }
        return ControlJson.pretty.encodeToJsonElement(
            LaunchResult.serializer(),
            session.backend().launch(LaunchOptions(fresh, captureLogs, build, arguments, environment, adopt)),
        )
    }
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
    /** Android phones and emulators adb lists, with their state (`device`, `unauthorized`, `offline`). */
    val android: List<AndroidListing> = emptyList(),
)

@Serializable
data class AndroidListing(
    val serial: String,
    val state: String,
    val model: String? = null,
)

class DevicesCommand : CliktCommand(name = "devices") {
    override fun help(context: Context): String = "List, boot, or shut down simulators and list paired iPhones and Android devices."

    override fun run() = Unit
}

class DevicesListCommand : ControlCommand("list", "List available simulators, paired iPhones, and Android devices.") {
    override fun execute(session: Session): JsonElement {
        val adb = session.context.subprocess.run(listOf(AndroidDevice.ADB, "devices", "-l")).stdout
        val inventory = DeviceInventory(Simctl(session.context).listDevices(), Devicectl(session.context).listDevices(), parseAdbDevices(adb))
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

/** `adb devices -l`: a header, then one line per device with its serial, state, and `key:value` details. */
internal fun parseAdbDevices(output: String): List<AndroidListing> = output.lines().drop(1).mapNotNull { line ->
    val fields = line.trim().split(Regex("\\s+"))
    if (fields.size < 2) return@mapNotNull null
    AndroidListing(fields[0], fields[1], fields.firstOrNull { it.startsWith("model:") }?.substringAfter("model:"))
}
