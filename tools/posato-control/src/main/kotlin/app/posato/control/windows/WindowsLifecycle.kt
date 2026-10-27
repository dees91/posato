package app.posato.control.windows

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
import app.posato.control.windows.WindowsGuest.Companion.psQuote
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Installs and runs Posato in the Windows clone. With `install --package <file>.msi` the MSI installs quietly, and
 * `launch` starts the installed `Posato.exe`. Until the Windows package exists, `install` without a package copies
 * the `windowsSpikeJar` build, the Linux application jar with the Windows Compose runtime, and `launch` runs it on the
 * golden VM's JDK; that drives the shared interface but pauses nothing.
 */
class WindowsLifecycle(
    private val context: RunContext,
    private val guest: WindowsGuest,
    private val stateStore: RunStateStore,
) : Lifecycle {
    val spikeJar: Path = context.layout.root.resolve("desktopApp/build/windows-spike/posato-windows-spike.jar")
    val installer = WindowsInstaller(context, guest)

    /** The MSI `install` installs instead of the spike jar. */
    var packageFile: Path? = null

    override fun doctor(): List<DoctorCheck> {
        val golden = runCatching { guest.goldenExists() }
        val goldenCheck = when {
            golden.isFailure -> DoctorCheck.fail(
                "windows.tart",
                golden.exceptionOrNull()?.message ?: "No Tart build with Windows guests.",
                HINT_SETUP,
            )

            golden.getOrDefault(false) -> DoctorCheck.pass("windows.golden", "The Windows golden VM exists.")

            else -> DoctorCheck.fail("windows.golden", "No Windows golden VM.", HINT_SETUP)
        }
        if (golden.isFailure) return listOf(goldenCheck)
        val clone = if (guest.running()) {
            DoctorCheck.pass("windows.clone", "The Windows clone ${WindowsGuest.CLONE} runs.")
        } else {
            DoctorCheck.fail("windows.clone", "The Windows clone is not running.", "Run `windows create`.", Severity.WARN)
        }
        val jar = if (Files.isRegularFile(spikeJar)) {
            DoctorCheck.pass("windows.jar", "The Windows spike jar is built.")
        } else {
            DoctorCheck.fail("windows.jar", "The Windows spike jar is not built.", "Run `build -t windows`, or install an MSI.", Severity.INFO)
        }
        return listOf(goldenCheck, clone, jar)
    }

    override fun build(options: BuildOptions): BuildResult {
        val started = System.currentTimeMillis()
        context.subprocess.run(
            listOf(context.layout.gradlew.toString(), ":desktopApp:windowsSpikeJar", "--quiet"),
            workingDirectory = context.layout.root,
            timeout = Duration.ofMinutes(BUILD_MINUTES),
        ).requireSuccess(ErrorCode.BUILD_FAILED, "Building the Windows spike jar")
        return BuildResult(appPath = context.layout.relativize(spikeJar), durationMs = System.currentTimeMillis() - started)
    }

    override fun install(): StatusResult {
        guest.requireRunning()
        val msi = packageFile
        when {
            msi != null -> installer.install(msi)

            !Files.isRegularFile(spikeJar) -> throw ControlException(
                ErrorCode.APP_NOT_STAGED,
                "No Windows spike jar.",
                "Run `build -t windows`, or pass --package <file>.msi.",
            )

            else -> guest.tools.push(spikeJar, JAR)
        }
        return status()
    }

    override fun launch(options: LaunchOptions): LaunchResult {
        if (options.build) build(BuildOptions())
        guest.requireRunning()
        val installed = installedApplication()
            ?: throw ControlException(ErrorCode.APP_NOT_INSTALLED, "Posato is not installed in the clone.", "Run `install -t windows`.")
        val executable = installed.takeIf { it != JAR }
        guest.tools.installScripts()
        terminateAll()
        if (options.fresh) {
            guest.powershell(
                DATA_DIRECTORIES.joinToString("\n") {
                    "Remove-Item -Recurse -Force \"$it\" -ErrorAction SilentlyContinue"
                },
            )
        }
        val arguments = options.arguments.joinToString(" ")
        // Start-Process goes through the shell, so the new process inherits no handle of the agent's command and the
        // command returns; the jar runs through a hidden cmd that keeps its output in a log.
        val start = if (executable != null) {
            "Start-Process -FilePath ${psQuote(executable)}" + if (arguments.isNotEmpty()) " -ArgumentList ${psQuote(arguments)}" else ""
        } else {
            "\$java = (Get-ChildItem ${psQuote(
                WindowsGuest.TOOLS,
            )} -Directory -Filter 'jdk-21*' | Sort-Object Name)[-1].FullName + '\\bin\\java.exe'\n" +
                "Start-Process cmd.exe -WindowStyle Hidden -ArgumentList ('/c \"\"' + \$java + '\" -jar $JAR $arguments > $LOG 2>&1\"')"
        }
        guest.powershell(start).requireSuccess(ErrorCode.COMMAND_FAILED, "Starting Posato in ${WindowsGuest.CLONE}")
        awaitTree()
        val pid = pid()
        stateStore.update(Target.WINDOWS, LaunchedProcess(pid = pid, startedAt = System.currentTimeMillis(), runId = context.runId))
        return LaunchResult(pid = pid, logPath = if (executable == null) LOG else null)
    }

    /** Posato's window is drawn seconds after the process starts; a launch is done when the probe reads its tree. */
    private fun awaitTree() {
        val deadline = System.currentTimeMillis() + REGISTER_MILLIS
        while (true) {
            val dump = guest.tools.probe("dump")
            if (dump.succeeded) return
            if (pid() == null) throw ControlException(ErrorCode.COMMAND_FAILED, "Posato exited after launch.", "Read $LOG with `windows exec`.")
            if (System.currentTimeMillis() > deadline) {
                throw ControlException(
                    ErrorCode.COMMAND_FAILED,
                    "Posato's window did not appear on the Java Access Bridge within ${REGISTER_MILLIS / MILLIS_PER_SECOND} s: " +
                        dump.stderr.trim().lines().lastOrNull().orEmpty(),
                )
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    override fun terminate(): StatusResult {
        guest.requireRunning()
        terminateAll()
        stateStore.update(Target.WINDOWS, null)
        return status()
    }

    private fun terminateAll() {
        guest.powershell("$POSATO_PROCESSES | Stop-Process -Force -ErrorAction SilentlyContinue")
    }

    override fun status(): StatusResult {
        guest.requireRunning()
        val pid = pid()
        val installed = installedApplication()
        return StatusResult(
            installed = installed != null,
            running = pid != null,
            pid = pid,
            appPath = installed,
            containerPath = DATA_DIRECTORIES.joinToString(";"),
            lastRunId = stateStore.load().windows?.runId,
        )
    }

    fun pid(): Long? = guest.powershell("$POSATO_PROCESSES | Select-Object -First 1 -ExpandProperty Id").stdout.trim().toLongOrNull()

    /**
     * The installed `Posato.exe`, in the install location the MSI registered or in the default folder; otherwise the
     * spike jar when `install` copied it; null when neither is there.
     */
    private fun installedApplication(): String? = guest.powershell(
        "\$entry = Get-ItemProperty ${WindowsInstaller.UNINSTALL_KEYS} -ErrorAction SilentlyContinue | " +
            "Where-Object { \$_.DisplayName -eq ${psQuote(WindowsInstaller.PRODUCT_NAME)} } | Select-Object -First 1\n" +
            "\$candidates = @(); if (\$entry -and \$entry.InstallLocation) { \$candidates += Join-Path \$entry.InstallLocation 'Posato.exe' }\n" +
            "\$candidates += ${psQuote(DEFAULT_EXECUTABLE)}, ${psQuote(JAR)}\n" +
            "\$candidates | Where-Object { Test-Path \$_ } | Select-Object -First 1",
    ).stdout.trim().ifEmpty { null }

    companion object {
        const val DEFAULT_EXECUTABLE = "C:\\Program Files\\Posato\\Posato.exe"
        const val JAR = "${WindowsGuest.TOOLS}\\posato.jar"
        const val LOG = "${WindowsGuest.TOOLS}\\posato.log"

        /** The spike jar keeps its data where the Linux entry point puts it; the Windows host uses %LOCALAPPDATA%. */
        val DATA_DIRECTORIES = listOf("\$env:USERPROFILE\\.local\\share\\Posato", "\$env:LOCALAPPDATA\\Posato")

        /**
         * Posato's processes without WMI, whose provider can hang in the guest: the installed `Posato.exe`, or the
         * golden VM's Java running the spike jar (nothing else in the clone runs that JDK).
         */
        private const val POSATO_PROCESSES = "Get-Process -Name Posato,java,javaw -ErrorAction SilentlyContinue | Where-Object { " +
            "\$_.ProcessName -eq 'Posato' -or \$_.Path -like '${WindowsGuest.TOOLS}\\jdk-*' }"
        private const val HINT_SETUP = "Set up the Windows golden VM as docs/development/unattended-verification.md describes."
        private const val BUILD_MINUTES = 15L
        private const val REGISTER_MILLIS = 90_000L
        private const val POLL_MILLIS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
