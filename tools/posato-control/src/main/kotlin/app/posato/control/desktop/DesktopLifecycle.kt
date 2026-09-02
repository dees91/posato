package app.posato.control.desktop

import app.posato.control.backend.BuildOptions
import app.posato.control.backend.BuildResult
import app.posato.control.backend.LaunchOptions
import app.posato.control.backend.LaunchResult
import app.posato.control.backend.Lifecycle
import app.posato.control.backend.StatusResult
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.LaunchedProcess
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target
import app.posato.control.model.DoctorCheck
import app.posato.control.model.Severity

class DesktopLifecycle(
    private val context: RunContext,
    private val bridge: AxBridge,
    private val processes: DesktopProcesses,
    private val stateStore: RunStateStore,
    private val evidence: DesktopEvidence,
) : Lifecycle {
    override fun doctor(): List<DoctorCheck> = DesktopDoctor(context, bridge, processes).checks(status())

    override fun build(options: BuildOptions): BuildResult {
        val started = System.currentTimeMillis()
        GradleStager(context).stage(options.signingIdentity, options.verify)
        processes.requireStaged()
        return BuildResult(
            appPath = context.layout.relativize(context.layout.stagedDesktopApplication),
            signingMode = processes.signingMode(),
            durationMs = System.currentTimeMillis() - started,
        )
    }

    override fun install(): StatusResult {
        processes.requireStaged()
        return status()
    }

    override fun launch(options: LaunchOptions): LaunchResult {
        if (options.build) build(BuildOptions())
        processes.requireStaged()
        val tracked = stateStore.load().desktop
        tracked?.pid?.takeIf { processes.isAlive(it) }?.let { processes.terminate(it) }
        val foreign = processes.foreignPids(tracked?.pid)
        if (foreign.isNotEmpty()) {
            throw ControlException(
                ErrorCode.ALREADY_RUNNING,
                "A Posato desktop process that this tool did not start is running (pid ${foreign.first()}).",
                "Quit that instance first; the tool never terminates processes it did not launch.",
            )
        }
        if (options.fresh) evidence.reset(dryRun = false, keepInstall = true)
        val logPath = context.artifactPath("desktop-app.log")
        val process = context.subprocess.startDetached(
            listOf(processes.executablePath().toString()) + options.arguments,
            logPath,
            environment = options.environment,
        )
        val windowId = awaitWindow(process.pid())
        stateStore.update(
            Target.DESKTOP,
            LaunchedProcess(pid = process.pid(), logPath = logPath.toString(), windowId = windowId, runId = context.runId),
        )
        context.recordArtifact(logPath)
        return LaunchResult(pid = process.pid(), logPath = context.layout.relativize(logPath), windowId = windowId)
    }

    override fun terminate(): StatusResult {
        val tracked = stateStore.load().desktop
        tracked?.pid?.let { processes.terminate(it) }
        stateStore.update(Target.DESKTOP, null)
        return status()
    }

    override fun status(): StatusResult {
        val tracked = stateStore.load().desktop
        val staged = processes.executablePath().toFile().isFile
        val running = tracked?.pid?.let { processes.isAlive(it) } ?: false
        return StatusResult(
            installed = staged,
            running = running,
            pid = tracked?.pid?.takeIf { running },
            appPath = context.layout.relativize(context.layout.stagedDesktopApplication).takeIf { staged },
            signingMode = if (staged) processes.signingMode() else null,
            windowId = tracked?.windowId?.takeIf { running },
            logPath = tracked?.logPath?.takeIf { running },
            lastRunId = tracked?.runId,
        )
    }

    private fun awaitWindow(pid: Long): Long? {
        val deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!processes.isAlive(pid)) {
                throw ControlException(
                    ErrorCode.COMMAND_FAILED,
                    "The desktop application exited during startup.",
                    "Inspect the launch log with `posato-control logs -t desktop`.",
                )
            }
            val window = bridge.windows(pid).filter { it.layer == 0 }.maxByOrNull { it.w * it.h }
            if (window != null) return window.id
            Thread.sleep(WINDOW_POLL_MS)
        }
        context.log("No window appeared within ${WINDOW_TIMEOUT_MS} ms; continuing without a window id")
        return null
    }

    private companion object {
        const val WINDOW_TIMEOUT_MS = 30_000L
        const val WINDOW_POLL_MS = 250L
    }
}

private class DesktopDoctor(
    private val context: RunContext,
    private val bridge: AxBridge,
    private val processes: DesktopProcesses,
) {
    fun checks(status: StatusResult): List<DoctorCheck> = buildList {
        add(permissionChecks())
        add(stagedCheck(status))
        add(runningCheck(status))
        add(databaseCheck())
    }.flatten()

    private fun permissionChecks(): List<DoctorCheck> {
        val permissions = try {
            bridge.permissions()
        } catch (exception: ControlException) {
            return listOf(
                DoctorCheck.fail(
                    "desktop.bridge",
                    "The accessibility bridge could not run: ${exception.message}",
                    "Install Xcode command line tools.",
                ),
            )
        }
        val host = responsibleHost()
        return listOf(
            if (permissions.accessibility) {
                DoctorCheck.pass("desktop.accessibility", "Accessibility access is granted to $host.")
            } else {
                DoctorCheck.fail(
                    "desktop.accessibility",
                    "Accessibility access is not granted to $host.",
                    "System Settings > Privacy & Security > Accessibility: enable $host, then restart it.",
                )
            },
            if (permissions.screenRecording) {
                DoctorCheck.pass("desktop.screenRecording", "Screen Recording access is granted to $host.")
            } else {
                DoctorCheck.fail(
                    "desktop.screenRecording",
                    "Screen Recording access is not granted to $host.",
                    "System Settings > Privacy & Security > Screen & System Audio Recording: enable $host, then restart it.",
                )
            },
        )
    }

    private fun stagedCheck(status: StatusResult): List<DoctorCheck> = listOf(
        if (status.installed) {
            val mode = status.signingMode
            if (mode == "development") {
                DoctorCheck.pass("desktop.staged", "The staged application is development-signed.")
            } else {
                DoctorCheck.fail(
                    "desktop.staged",
                    "The staged application is $mode-signed; the application picker needs development signing.",
                    "Set posato.macos.signingIdentity in local.properties and rerun `build -t desktop`.",
                    Severity.WARN,
                )
            }
        } else {
            DoctorCheck.fail("desktop.staged", "No staged desktop application.", "Run `posato-control build -t desktop`.", Severity.WARN)
        },
    )

    private fun runningCheck(status: StatusResult): List<DoctorCheck> = listOf(
        if (status.running) {
            DoctorCheck.pass("desktop.running", "The tracked desktop process is running (pid ${status.pid}).")
        } else {
            DoctorCheck.pass("desktop.running", "No tracked desktop process is running.")
        },
        DoctorCheck.pass("desktop.foreign", "Untracked Posato desktop processes: ${processes.foreignPids(status.pid).size}."),
    )

    private fun databaseCheck(): List<DoctorCheck> = listOf(
        DoctorCheck.pass(
            "desktop.databases",
            "Local databases present: ${DesktopPaths.databases.count {
                it.toFile().isFile
            }} of ${DesktopPaths.databases.size}.",
        ),
    )

    private fun responsibleHost(): String {
        var handle = ProcessHandle.current().parent().orElse(null)
        while (handle != null) {
            val command = handle.info().command().orElse("")
            val app = command.substringBefore(".app/", missingDelimiterValue = "")
            if (app.isNotEmpty()) return app.substringAfterLast('/') + ".app"
            handle = handle.parent().orElse(null)
        }
        return "the terminal application"
    }
}
