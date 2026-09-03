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
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.LaunchedProcess
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target
import app.posato.control.model.DoctorCheck
import app.posato.control.model.Severity
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readLines

const val IOS_BUNDLE_ID = "app.posato.ios"

class SimulatorSession(
    private val context: RunContext,
    val simctl: Simctl,
    private val explicitUdid: String?,
) {
    private var resolved: String? = null

    fun udid(): String {
        resolved?.let { return it }
        val preference = explicitUdid ?: context.configuration.value(ConfigurationKey.SIMULATOR)
        val devices = simctl.listDevices()
        val chosen = when {
            preference != null -> devices.firstOrNull { it.udid == preference || it.name == preference }
                ?: throw ControlException(
                    ErrorCode.NO_BOOTED_SIMULATOR,
                    "No available simulator matches '$preference'.",
                    "Run `posato-control devices list`.",
                )

            else -> devices.firstOrNull { it.state == "Booted" }
                ?: throw ControlException(
                    ErrorCode.NO_BOOTED_SIMULATOR,
                    "No iOS simulator is booted.",
                    "Run `posato-control devices boot` or set posato.control.simulator in local.properties.",
                )
        }
        if (chosen.state != "Booted") simctl.boot(chosen.udid)
        return chosen.udid.also { resolved = it }
    }

    fun isRunning(udid: String): Boolean = ProcessHandle.allProcesses().anyMatch { handle ->
        handle.info().command().map { it.contains("/Devices/$udid/") && it.endsWith("/Posato.app/Posato") }.orElse(false)
    }

    fun container(udid: String): Path? = simctl.appContainer(udid, IOS_BUNDLE_ID, "data")

    fun isInstalled(udid: String): Boolean = simctl.appContainer(udid, IOS_BUNDLE_ID, "app") != null
}

class SimulatorLifecycle(
    private val context: RunContext,
    private val session: SimulatorSession,
    private val xcodeBuild: XcodeBuild,
    private val stateStore: RunStateStore,
    private val evidence: Evidence,
) : Lifecycle {
    override fun doctor(): List<DoctorCheck> {
        val devices = session.simctl.listDevices()
        val booted = devices.filter { it.state == "Booted" }
        val checks = mutableListOf(
            if (devices.isEmpty()) {
                DoctorCheck.fail(
                    "simulator.runtime",
                    "No iOS simulator runtime is available.",
                    "Install an iOS runtime in Xcode > Settings > Components.",
                )
            } else {
                DoctorCheck.pass("simulator.runtime", "${devices.size} iOS simulators available; ${booted.size} booted.")
            },
        )
        val udid = booted.firstOrNull()?.udid
        if (udid != null) {
            val installed = session.isInstalled(udid)
            checks.add(
                if (installed) {
                    DoctorCheck.pass(
                        "simulator.installed",
                        "Posato is installed on the booted simulator.",
                    )
                } else {
                    DoctorCheck.fail(
                        "simulator.installed",
                        "Posato is not installed on the booted simulator.",
                        "Run `build -t simulator` then `install -t simulator`.",
                        Severity.WARN,
                    )
                },
            )
            checks.add(
                DoctorCheck.pass(
                    "simulator.running",
                    if (session.isRunning(udid)) "Posato is running on the booted simulator." else "Posato is not running on the booted simulator.",
                ),
            )
        } else {
            checks.add(DoctorCheck.fail("simulator.booted", "No simulator is booted.", "Run `posato-control devices boot`.", Severity.WARN))
        }
        checks.add(driverCheck())
        return checks
    }

    private fun driverCheck(): DoctorCheck = if (xcodeBuild.driverTestRun(Target.SIMULATOR) != null) {
        DoctorCheck.pass("simulator.driver", "The simulator driver is built.")
    } else {
        DoctorCheck.fail(
            "simulator.driver",
            "The simulator driver is not built yet.",
            "Run `build -t simulator --driver`; interaction commands build it on demand.",
            Severity.INFO,
        )
    }

    override fun build(options: BuildOptions): BuildResult {
        val started = System.currentTimeMillis()
        val udid = session.udid()
        val app = xcodeBuild.buildApp(Target.SIMULATOR, options.configuration, udid)
        val driver = if (options.driver) xcodeBuild.buildDriver(Target.SIMULATOR, udid) else null
        return BuildResult(
            context.layout.relativize(app),
            "unsigned",
            driver?.let { context.layout.relativize(it) },
            System.currentTimeMillis() - started,
        )
    }

    override fun install(): StatusResult {
        val udid = session.udid()
        val app = xcodeBuild.appProduct(Target.SIMULATOR, "Debug")
        if (!app.exists()) {
            throw ControlException(
                ErrorCode.APP_NOT_STAGED,
                "No simulator build at ${context.layout.relativize(app)}.",
                "Run `posato-control build -t simulator` first.",
            )
        }
        session.simctl.install(udid, app)
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
                "Posato is not installed on the simulator.",
                "Run `posato-control install -t simulator` first.",
            )
        }
        if (options.fresh) evidence.reset(dryRun = false, keepInstall = true)
        val stdout = context.artifactPath("simulator-app.log")
        val stderr = context.artifactPath("simulator-app.err.log")
        val pid = session.simctl.launch(udid, IOS_BUNDLE_ID, stdout, stderr, options.arguments, options.environment)
        stateStore.update(Target.SIMULATOR, LaunchedProcess(pid = pid, udid = udid, logPath = stdout.toString(), runId = context.runId))
        context.recordArtifact(stdout)
        return LaunchResult(pid = pid, udid = udid, logPath = context.layout.relativize(stdout))
    }

    override fun terminate(): StatusResult {
        val udid = session.udid()
        session.simctl.terminate(udid, IOS_BUNDLE_ID)
        stateStore.update(Target.SIMULATOR, null)
        return status()
    }

    override fun status(): StatusResult {
        val udid = session.udid()
        val tracked = stateStore.load().simulator
        val running = session.isRunning(udid)
        return StatusResult(
            installed = session.isInstalled(udid),
            running = running,
            pid = tracked?.pid?.takeIf { running },
            udid = udid,
            appPath = xcodeBuild.appProduct(Target.SIMULATOR, "Debug").takeIf { it.exists() }?.let { context.layout.relativize(it) },
            containerPath = session.container(udid)?.toString(),
            signingMode = "unsigned",
            logPath = tracked?.logPath?.takeIf { running },
            lastRunId = tracked?.runId,
        )
    }
}

class SimulatorEvidence(
    private val context: RunContext,
    private val session: SimulatorSession,
    private val xcodeBuild: XcodeBuild,
    private val stateStore: RunStateStore,
) : Evidence {
    override fun screenshot(
        name: String,
        out: Path?
    ): Path {
        val destination = out ?: context.artifactPath("screenshots", "$name.png")
        Files.createDirectories(destination.toAbsolutePath().parent)
        session.simctl.screenshot(session.udid(), destination)
        return context.recordArtifact(destination)
    }

    override fun logs(
        tail: Int,
        streamSeconds: Int?
    ): LogsResult {
        if (streamSeconds != null) return LogsResult(null, session.simctl.streamLog(session.udid(), streamSeconds).takeLast(tail))
        val tracked = stateStore.load().simulator ?: return LogsResult(null, emptyList())
        val stdout = tracked.logPath?.let { Path.of(it) } ?: return LogsResult(null, emptyList())
        val stderr = stdout.resolveSibling(stdout.fileName.toString().removeSuffix(".log") + ".err.log")
        val lines = listOf(stdout, stderr).filter { it.exists() }.flatMap { it.readLines() }.takeLast(tail)
        return LogsResult(context.layout.relativize(stdout), lines)
    }

    override fun databasePaths(): List<Path> {
        val container = session.container(session.udid())
            ?: throw ControlException(
                ErrorCode.APP_NOT_INSTALLED,
                "Posato is not installed on the simulator.",
                "Run `posato-control install -t simulator` first.",
            )
        return listOf(container.resolve("Library").resolve("Application Support").resolve("Posato").resolve("posato-policy.db"))
    }

    override fun reset(
        dryRun: Boolean,
        keepInstall: Boolean
    ): ResetPlan {
        val udid = session.udid()
        if (!session.isInstalled(udid)) return ResetPlan(emptyList(), uninstall = false, performed = !dryRun)
        val deletions = if (keepInstall) {
            databasePaths().flatMap { database ->
                listOf(database) + listOf("-wal", "-shm", "-journal").map { suffix -> database.resolveSibling(database.fileName.toString() + suffix) }
            }.filter { it.exists() }
        } else {
            emptyList()
        }
        if (dryRun) return ResetPlan(deletions.map { it.toString() }, uninstall = !keepInstall, performed = false)
        session.simctl.terminate(udid, IOS_BUNDLE_ID)
        stateStore.update(Target.SIMULATOR, null)
        if (!keepInstall) {
            session.simctl.uninstall(udid, IOS_BUNDLE_ID)
            return ResetPlan(emptyList(), uninstall = true, performed = true)
        }
        val backup = context.artifactPath("backup", "simulator")
        Files.createDirectories(backup)
        deletions.forEach { file ->
            Files.copy(file, backup.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
            Files.deleteIfExists(file)
        }
        context.recordArtifact(backup)
        return ResetPlan(deletions.map { it.toString() }, uninstall = false, performed = true, backupDirectory = context.layout.relativize(backup))
    }

    override fun cleanup(
        dryRun: Boolean,
        purgeDerivedData: Boolean
    ): List<String> {
        val actions = mutableListOf<String>()
        stateStore.load().simulator?.let { actions.add("terminate Posato on simulator ${it.udid}") }
        val derived = listOf(context.layout.derivedData(Target.SIMULATOR.id), xcodeBuild.driverDerivedData(Target.SIMULATOR)).filter { it.exists() }
        if (purgeDerivedData) derived.forEach { actions.add("delete ${context.layout.relativize(it)}") }
        if (!dryRun) {
            stateStore.load().simulator?.udid?.let { session.simctl.terminate(it, IOS_BUNDLE_ID) }
            stateStore.update(Target.SIMULATOR, null)
            if (purgeDerivedData) derived.forEach { directory -> directory.toFile().deleteRecursively() }
        }
        return actions
    }
}

class SimulatorBackend private constructor(
    lifecycle: Lifecycle,
    evidence: Evidence,
    interaction: Interaction,
) : Backend,
    Lifecycle by lifecycle,
    Evidence by evidence,
    Interaction by interaction {
    override val target: Target = Target.SIMULATOR

    companion object {
        fun create(
            context: RunContext,
            udid: String?
        ): SimulatorBackend {
            val session = SimulatorSession(context, Simctl(context), udid)
            val xcodeBuild = XcodeBuild(context)
            val stateStore = RunStateStore(context.layout)
            val evidence = SimulatorEvidence(context, session, xcodeBuild, stateStore)
            val lifecycle = SimulatorLifecycle(context, session, xcodeBuild, stateStore, evidence)
            val interaction = IosInteraction(IosDriverRunner(context, xcodeBuild, Target.SIMULATOR), session::udid, IOS_BUNDLE_ID) {
                evidence.reset(dryRun = false, keepInstall = true)
            }
            return SimulatorBackend(lifecycle, evidence, interaction)
        }
    }
}
