package app.posato.control.apple

import app.posato.control.backend.Backend
import app.posato.control.backend.BuildOptions
import app.posato.control.backend.BuildResult
import app.posato.control.backend.Evidence
import app.posato.control.backend.Interaction
import app.posato.control.backend.LaunchOptions
import app.posato.control.backend.LaunchResult
import app.posato.control.backend.Lifecycle
import app.posato.control.backend.LogsResult
import app.posato.control.backend.ResetPlan
import app.posato.control.backend.StatusResult
import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ConfigurationSource
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.LaunchedProcess
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target
import app.posato.control.model.DoctorCheck
import app.posato.control.model.Severity
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

class DeviceSession(
    private val context: RunContext,
    val devicectl: Devicectl,
    private val explicitUdid: String?,
) {
    private var resolved: String? = null

    fun udid(): String {
        resolved?.let { return it }
        val preference = explicitUdid ?: context.configuration.value(ConfigurationKey.DEVICE)
        val devices = devicectl.listDevices()
        val chosen = select(preference, devices) ?: throw missingDevice(preference)
        if (!chosen.connected) {
            throw ControlException(
                ErrorCode.NO_CONNECTED_DEVICE,
                "The device '${chosen.name}' is paired but not connected.",
                "Connect and unlock the iPhone.",
            )
        }
        return chosen.udid.also { resolved = it }
    }

    private fun select(
        preference: String?,
        devices: List<PhysicalDevice>
    ): PhysicalDevice? =
        if (preference != null) devices.firstOrNull { it.udid == preference || it.name == preference } else devices.firstOrNull { it.connected }

    private fun missingDevice(preference: String?): ControlException = if (preference != null) {
        ControlException(ErrorCode.NO_CONNECTED_DEVICE, "No paired device matches '$preference'.", "Run `posato-control devices list`.")
    } else {
        ControlException(ErrorCode.NO_CONNECTED_DEVICE, "No iPhone is connected.", "Connect and unlock the iPhone, then trust this Mac.")
    }

    fun isInstalled(udid: String): Boolean = devicectl.installedBundles(udid).contains(IOS_BUNDLE_ID)
}

class DeviceLifecycle(
    private val context: RunContext,
    private val session: DeviceSession,
    private val xcodeBuild: XcodeBuild,
    private val stateStore: RunStateStore,
    private val evidence: Evidence,
) : Lifecycle {
    override fun doctor(): List<DoctorCheck> {
        val checks = mutableListOf(teamCheck())
        val devices = session.devicectl.listDevices()
        val connected = devices.filter { it.connected }
        checks.add(connectionCheck(devices, connected))
        connected.firstOrNull()?.let { device -> checks.add(installedCheck(device.udid)) }
        checks.add(driverCheck())
        return checks
    }

    private fun teamCheck(): DoctorCheck {
        val source = context.configuration.source(ConfigurationKey.DEVELOPMENT_TEAM)
        return if (source == ConfigurationSource.ABSENT) {
            DoctorCheck.fail(
                "device.team",
                "No Apple development team is configured.",
                "Add posato.apple.developmentTeam=<team id> to the ignored local.properties file.",
            )
        } else {
            DoctorCheck.pass("device.team", "The Apple development team comes from ${source.name.lowercase().replace('_', ' ')}.")
        }
    }

    private fun connectionCheck(
        devices: List<PhysicalDevice>,
        connected: List<PhysicalDevice>
    ): DoctorCheck = if (connected.isEmpty()) {
        DoctorCheck.fail("device.connected", "No iPhone is connected (${devices.size} paired).", "Connect and unlock the iPhone.", Severity.WARN)
    } else {
        val device = connected.first()
        DoctorCheck.pass(
            "device.connected",
            "Connected: ${device.model ?: "iPhone"} on iOS ${device.osVersion ?: "?"}; developer mode ${device.developerMode ?: "unknown"}.",
        )
    }

    private fun installedCheck(udid: String): DoctorCheck = if (session.isInstalled(udid)) {
        DoctorCheck.pass("device.installed", "Posato is installed on the device.")
    } else {
        DoctorCheck.fail(
            "device.installed",
            "Posato is not installed on the device.",
            "Run `build -t device` then `install -t device`.",
            Severity.WARN,
        )
    }

    private fun driverCheck(): DoctorCheck = if (xcodeBuild.driverTestRun(Target.DEVICE) != null) {
        DoctorCheck.pass("device.driver", "The device driver is built.")
    } else {
        DoctorCheck.fail(
            "device.driver",
            "The device driver is not built yet.",
            "Run `build -t device --driver`; interaction commands build it on demand.",
            Severity.INFO,
        )
    }

    override fun build(options: BuildOptions): BuildResult {
        val started = System.currentTimeMillis()
        val app = xcodeBuild.buildApp(Target.DEVICE, options.configuration, null)
        val driver = if (options.driver) xcodeBuild.buildDriver(Target.DEVICE, null) else null
        return BuildResult(
            context.layout.relativize(app),
            "development",
            driver?.let { context.layout.relativize(it) },
            System.currentTimeMillis() - started,
        )
    }

    override fun install(): StatusResult {
        val udid = session.udid()
        val app = xcodeBuild.appProduct(Target.DEVICE, "Debug")
        if (!app.exists()) {
            throw ControlException(
                ErrorCode.APP_NOT_STAGED,
                "No device build at ${context.layout.relativize(app)}.",
                "Run `posato-control build -t device` first.",
            )
        }
        session.devicectl.install(udid, app)
        return status()
    }

    override fun launch(options: LaunchOptions): LaunchResult {
        if (options.build) {
            build(BuildOptions())
            install()
        }
        val udid = session.udid()
        if (!session.isInstalled(
                udid,
            )
        ) {
            throw ControlException(
                ErrorCode.APP_NOT_INSTALLED,
                "Posato is not installed on the device.",
                "Run `posato-control install -t device` first.",
            )
        }
        if (options.fresh) {
            evidence.reset(dryRun = false, keepInstall = false)
            install()
        }
        val logPath = context.artifactPath("device-console.log")
        val consolePid: Long?
        val pid: Long?
        if (options.captureLogs) {
            consolePid = session.devicectl.startConsole(udid, IOS_BUNDLE_ID, logPath).pid()
            pid = null
            context.recordArtifact(logPath)
        } else {
            consolePid = null
            pid = session.devicectl.launch(udid, IOS_BUNDLE_ID, options.arguments, options.environment)
        }
        stateStore.update(
            Target.DEVICE,
            LaunchedProcess(
                pid = pid,
                udid = udid,
                logPath = logPath.toString().takeIf {
                    options.captureLogs
                },
                runId = context.runId,
                consolePid = consolePid,
            ),
        )
        return LaunchResult(pid = pid, udid = udid, logPath = logPath.takeIf { options.captureLogs }?.let { context.layout.relativize(it) })
    }

    override fun terminate(): StatusResult {
        val udid = session.udid()
        val tracked = stateStore.load().device
        tracked?.consolePid?.let { consolePid -> ProcessHandle.of(consolePid).ifPresent { it.destroy() } }
        tracked?.pid?.let { session.devicectl.terminate(udid, it) }
        stateStore.update(Target.DEVICE, null)
        return status()
    }

    override fun status(): StatusResult {
        val udid = session.udid()
        val tracked = stateStore.load().device
        val consoleAlive = tracked?.consolePid?.let { consolePid -> ProcessHandle.of(consolePid).map { it.isAlive }.orElse(false) } ?: false
        return StatusResult(
            installed = session.isInstalled(udid),
            running = tracked?.pid != null || consoleAlive,
            pid = tracked?.pid,
            udid = udid,
            appPath = xcodeBuild.appProduct(Target.DEVICE, "Debug").takeIf { it.exists() }?.let { context.layout.relativize(it) },
            signingMode = "development",
            logPath = tracked?.logPath?.takeIf { consoleAlive },
            lastRunId = tracked?.runId,
        )
    }
}

class DeviceEvidence(
    private val context: RunContext,
    private val session: DeviceSession,
    private val xcodeBuild: XcodeBuild,
    private val stateStore: RunStateStore,
    private val interaction: IosInteraction,
) : Evidence {
    override fun screenshot(
        name: String,
        out: Path?
    ): Path {
        val artifact = context.layout.root.resolve(interaction.screenshotArtifact(name))
        if (out == null) return artifact
        java.nio.file.Files.createDirectories(out.toAbsolutePath().parent)
        java.nio.file.Files.copy(artifact, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return context.recordArtifact(out)
    }

    override fun logs(
        tail: Int,
        streamSeconds: Int?
    ): LogsResult {
        if (streamSeconds !=
            null
        ) {
            throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "Log streaming is only available on the Simulator.",
                "Launch with `--capture-logs` to keep a console attachment, then read `logs -t device`.",
            )
        }
        val logPath = stateStore.load().device?.logPath?.let { Path.of(it) }
            ?: throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "No console log was captured for the device.",
                "Launch with `launch -t device --capture-logs`.",
            )
        return LogsResult(context.layout.relativize(logPath), if (logPath.exists()) logPath.readLines().takeLast(tail) else emptyList())
    }

    override fun databasePaths(): List<Path> = throw ControlException(
        ErrorCode.UNSUPPORTED_ON_TARGET,
        "The device database is not readable from the Mac.",
        "Use `reset -t device --yes` (uninstall) and verify state through the UI instead.",
    )

    override fun reset(
        dryRun: Boolean,
        keepInstall: Boolean
    ): ResetPlan {
        if (keepInstall) {
            throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "The device keeps its data only by reinstalling.",
                "Run `reset -t device --yes` and then `install -t device`.",
            )
        }
        val udid = session.udid()
        val installed = session.isInstalled(udid)
        if (dryRun || !installed) return ResetPlan(emptyList(), uninstall = installed, performed = !dryRun && !installed)
        stateStore.load().device?.consolePid?.let { consolePid -> ProcessHandle.of(consolePid).ifPresent { it.destroy() } }
        stateStore.update(Target.DEVICE, null)
        session.devicectl.uninstall(udid, IOS_BUNDLE_ID)
        return ResetPlan(emptyList(), uninstall = true, performed = true)
    }

    override fun cleanup(
        dryRun: Boolean,
        purgeDerivedData: Boolean
    ): List<String> {
        val actions = mutableListOf<String>()
        val tracked = stateStore.load().device
        tracked?.consolePid?.let { actions.add("stop the device console attachment (pid $it)") }
        val derived = listOf(context.layout.derivedData(Target.DEVICE.id), xcodeBuild.driverDerivedData(Target.DEVICE)).filter { it.exists() }
        if (purgeDerivedData) derived.forEach { actions.add("delete ${context.layout.relativize(it)}") }
        if (!dryRun) {
            tracked?.consolePid?.let { consolePid -> ProcessHandle.of(consolePid).ifPresent { it.destroy() } }
            stateStore.update(Target.DEVICE, null)
            if (purgeDerivedData) derived.forEach { directory -> directory.toFile().deleteRecursively() }
        }
        return actions
    }
}

class DeviceBackend private constructor(
    lifecycle: Lifecycle,
    evidence: Evidence,
    interaction: Interaction,
) : Backend,
    Lifecycle by lifecycle,
    Evidence by evidence,
    Interaction by interaction {
    override val target: Target = Target.DEVICE

    companion object {
        fun create(
            context: RunContext,
            udid: String?
        ): DeviceBackend {
            val session = DeviceSession(context, Devicectl(context), udid)
            val xcodeBuild = XcodeBuild(context)
            val stateStore = RunStateStore(context.layout)
            val interaction = IosInteraction(IosDriverRunner(context, xcodeBuild, Target.DEVICE), session::udid, IOS_BUNDLE_ID)
            val evidence = DeviceEvidence(context, session, xcodeBuild, stateStore, interaction)
            val lifecycle = DeviceLifecycle(context, session, xcodeBuild, stateStore, evidence)
            return DeviceBackend(lifecycle, evidence, interaction)
        }
    }
}
