package app.posato.control.desktop

import app.posato.control.backend.BuildOptions
import app.posato.control.backend.BuildResult
import app.posato.control.backend.LaunchOptions
import app.posato.control.backend.LaunchResult
import app.posato.control.backend.Lifecycle
import app.posato.control.backend.StatusResult
import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.LaunchedProcess
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target
import app.posato.control.core.TrackedProcess
import app.posato.control.model.DoctorCheck
import app.posato.control.model.Severity
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists

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
        if (options.adopt) return adopt(options)
        if (options.build) build(BuildOptions())
        processes.requireStaged()
        val tracked = stateStore.load().desktop
        processes.terminate(tracked)
        val foreign = processes.foreignPids(processes.trackedPid(tracked))
        if (foreign.isNotEmpty()) {
            throw ControlException(
                ErrorCode.ALREADY_RUNNING,
                "A Posato desktop process that this tool did not start is running (pid ${foreign.first()}).",
                "Quit that instance first; the tool never terminates processes it did not launch.",
            )
        }
        if (options.fresh) evidence.reset(dryRun = false, keepInstall = true)
        val logPath = context.artifactPath("desktop-app.log")
        val pid = if (context.layout.installedApplicationMarker.exists()) {
            launchThroughLaunchServices(options, logPath)
        } else {
            context.subprocess.startDetached(
                listOf(processes.executablePath().toString()) + options.arguments,
                logPath,
                environment = options.environment,
            ).pid()
        }
        val windowId = awaitWindow(pid)
        stateStore.update(
            Target.DESKTOP,
            LaunchedProcess(
                pid = pid,
                startedAt = TrackedProcess.startedAt(pid),
                logPath = logPath.toString(),
                windowId = windowId,
                runId = context.runId,
            ),
        )
        context.recordArtifact(logPath)
        return LaunchResult(pid = pid, logPath = context.layout.relativize(logPath), windowId = windowId)
    }

    /**
     * Opens an installed candidate through LaunchServices, as Finder does, so that the application rather than the
     * guest agent is responsible for it. Privacy decisions such as App Management then apply to the application and its
     * update installer; launched from the agent directly, a denial recorded for the agent makes Sparkle ask for an
     * administrator for every later update (`observed` 2026-09-25).
     */
    private fun launchThroughLaunchServices(
        options: LaunchOptions,
        logPath: Path,
    ): Long {
        java.nio.file.Files.createDirectories(logPath.parent)
        val command = buildList {
            addAll(listOf("/usr/bin/open", "--stdout", logPath.toString(), "--stderr", logPath.toString()))
            options.environment.forEach { (key, value) -> addAll(listOf("--env", "$key=$value")) }
            add(context.layout.desktopApplication.toString())
            if (options.arguments.isNotEmpty()) addAll(listOf("--args") + options.arguments)
        }
        context.subprocess.run(command).requireSuccess(ErrorCode.COMMAND_FAILED, "Opening the installed candidate")
        val deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS
        while (true) {
            val running = processes.foreignPids(null)
            if (running.isNotEmpty()) return singleInstance(running)
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(ErrorCode.COMMAND_FAILED, "The installed candidate did not start through LaunchServices.")
            }
            Thread.sleep(WINDOW_POLL_MS)
        }
    }

    /**
     * Tracks the one running instance this tool did not start, such as the build Sparkle relaunches after an update.
     * Its output was never captured, so the tracked process has no log.
     */
    private fun adopt(options: LaunchOptions): LaunchResult {
        if (options != LaunchOptions(adopt = true)) {
            throw ControlException(ErrorCode.USAGE, "--adopt tracks a running instance and takes no launch options.", "Pass --adopt alone.")
        }
        processes.requireStaged()
        val pid = singleInstance(processes.foreignPids(null))
        val windowId = awaitWindow(pid)
        stateStore.update(
            Target.DESKTOP,
            LaunchedProcess(pid = pid, startedAt = TrackedProcess.startedAt(pid), windowId = windowId, runId = context.runId),
        )
        return LaunchResult(pid = pid, windowId = windowId)
    }

    override fun terminate(): StatusResult {
        processes.terminate(stateStore.load().desktop)
        stateStore.update(Target.DESKTOP, null)
        return status()
    }

    override fun status(): StatusResult {
        val tracked = stateStore.load().desktop
        val staged = processes.executablePath().toFile().isFile
        val running = processes.isTracked(tracked)
        return StatusResult(
            installed = staged,
            running = running,
            pid = tracked?.pid?.takeIf { running },
            appPath = context.layout.relativize(context.layout.desktopApplication).takeIf { staged },
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

/** The only running instance of the desktop application; none or several leave nothing unambiguous to adopt. */
internal fun singleInstance(pids: List<Long>): Long = when (pids.size) {
    0 -> throw ControlException(ErrorCode.APP_NOT_RUNNING, "No desktop application instance is running to adopt.")

    1 -> pids.single()

    else -> throw ControlException(
        ErrorCode.ALREADY_RUNNING,
        "${pids.size} desktop application instances run (${pids.joinToString(", ")}), so none can be adopted unambiguously.",
    )
}

private class DesktopDoctor(
    private val context: RunContext,
    private val bridge: AxBridge,
    private val processes: DesktopProcesses,
) {
    fun checks(status: StatusResult): List<DoctorCheck> = buildList {
        add(permissionChecks())
        add(stagedCheck(status))
        add(DesktopProvisioningChecks.checks(provisioningFacts(status)))
        add(runningCheck(status))
        add(databaseCheck())
    }.flatten()

    /** Reads every host-observable provisioning condition once; the decisions themselves live in DesktopProvisioningChecks. */
    private fun provisioningFacts(status: StatusResult): ProvisioningFacts {
        val identity = context.configuration.value(ConfigurationKey.MACOS_SIGNING_IDENTITY)
        val profilePath = context.configuration.value(ConfigurationKey.MACOS_SYNC_PROVISIONING_PROFILE)
        val profileFile = profilePath?.let { Path.of(it) }
        val decoded = profileFile?.takeIf { it.exists() }?.let { file ->
            val output = context.subprocess.run(listOf("/usr/bin/security", "cms", "-D", "-i", file.toAbsolutePath().toString()))
            if (output.succeeded) SyncProfile.decode(output.stdout) else null
        }
        return ProvisioningFacts(
            signingIdentity = identity,
            signingIdentityInKeychain = identity != null && identityInKeychain(identity),
            signingIdentityTeam = identity?.let { certificateTeam(it) },
            syncProfileConfigured = profilePath != null,
            syncProfileReadable = decoded != null,
            syncProfile = decoded,
            developmentTeam = context.configuration.value(ConfigurationKey.DEVELOPMENT_TEAM),
            staged = status.installed,
            helperExecutablePresent = nested(HELPER_EXECUTABLE).exists(),
            proxyDaemonPresent = nested(PROXY_DAEMON).exists() && nested(PROXY_DAEMON_PLIST).exists(),
            syncCompanionPresent = nested(SYNC_COMPANION_EXECUTABLE).exists(),
            now = Instant.now(),
        )
    }

    private fun identityInKeychain(identity: String): Boolean {
        val output = context.subprocess.run(listOf("/usr/bin/security", "find-identity", "-v", "-p", "codesigning"))
        return output.succeeded && output.stdout.contains(identity)
    }

    /**
     * The team a certificate signs under is its subject's organizational unit, not the identifier inside its common
     * name, so the two are read separately and only the organizational unit is compared.
     */
    private fun certificateTeam(identity: String): String? {
        val certificate = context.subprocess.run(listOf("/usr/bin/security", "find-certificate", "-c", identity, "-p"))
        if (!certificate.succeeded || certificate.stdout.isBlank()) return null
        val subject = context.subprocess.run(
            listOf("/usr/bin/openssl", "x509", "-noout", "-subject"),
            stdin = certificate.stdout,
        )
        if (!subject.succeeded) return null
        return CertificateSubject.team(subject.stdout)
    }

    /** Resolves one nested component of the staged package, whose layout `stageMacOsDevelopmentPackage` produces. */
    private fun nested(relativePath: String): Path = context.layout.desktopApplication.resolve(relativePath)

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
            val configured = context.configuration.value(ConfigurationKey.MACOS_SIGNING_IDENTITY) != null
            when {
                mode == "development" -> DoctorCheck.pass("desktop.staged", "The staged application is development-signed.")

                mode == "developer-id" -> DoctorCheck.pass("desktop.staged", "The installed candidate is Developer ID-signed.")

                // `./gradlew quality` restages without the signing properties, so a configured identity can still leave an ad-hoc package.
                configured -> DoctorCheck.fail(
                    "desktop.staged",
                    "The staged application is $mode-signed even though a signing identity is configured, " +
                        "so it was staged by a Gradle invocation that did not pass it; the application picker needs development signing.",
                    "Rerun `posato-control build -t desktop`, which passes the identity and the companion profile.",
                    Severity.WARN,
                )

                else -> DoctorCheck.fail(
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

    private companion object {
        const val HELPER_EXECUTABLE = "Contents/Helpers/PosatoMacOSHelper.app/Contents/MacOS/PosatoMacOSHelper"
        const val PROXY_DAEMON = "Contents/Helpers/PosatoMacOSHelper.app/Contents/Resources/PosatoProxySettingsDaemon"
        const val PROXY_DAEMON_PLIST = "Contents/Helpers/PosatoMacOSHelper.app/Contents/Library/LaunchDaemons/" +
            "app.posato.macos.proxy-settings.plist"
        const val SYNC_COMPANION_EXECUTABLE = "Contents/Helpers/PosatoMacOSSync.app/Contents/MacOS/PosatoMacOSSync"
    }
}
