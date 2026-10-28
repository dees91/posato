package app.posato.control.windows

import app.posato.control.backend.BuildOptions
import app.posato.control.backend.BuildResult
import app.posato.control.backend.LaunchOptions
import app.posato.control.backend.LaunchResult
import app.posato.control.backend.Lifecycle
import app.posato.control.backend.StatusResult
import app.posato.control.core.CONTROL_IDS_MARKER
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
 * Builds, installs, and runs Posato in the Windows clone. `build` collects the `windowsPackageInputs` on the host and
 * packages them into an MSI in the running clone with `package-msi.ps1`, since jpackage builds only for the machine it
 * runs on; the MSI lands in `desktopApp/build/windows/msi`. `install` installs that MSI, or the one `--package` names,
 * quietly, and `launch` starts the installed `Posato.exe`.
 */
class WindowsLifecycle(
    private val context: RunContext,
    private val guest: WindowsGuest,
    private val stateStore: RunStateStore,
) : Lifecycle {
    private val packageInputs: Path = context.layout.root.resolve("desktopApp/build/windows/package")
    private val msiFolder: Path = context.layout.root.resolve("desktopApp/build/windows/msi")
    val installer = WindowsInstaller(context, guest)

    /** The MSI `install` installs instead of the last one `build` made. */
    var packageFile: Path? = null

    /** The version `build` packages instead of the app's, such as a higher one for a major-upgrade check. */
    var packageVersion: String? = null

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
        val msi = builtMsi()?.let { DoctorCheck.pass("windows.msi", "The Windows MSI ${it.fileName} is built.") }
            ?: DoctorCheck.fail("windows.msi", "No Windows MSI is built.", HINT_BUILD, Severity.INFO)
        return listOf(goldenCheck, clone, msi)
    }

    override fun build(options: BuildOptions): BuildResult {
        val started = System.currentTimeMillis()
        guest.requireRunning()
        context.subprocess.run(
            listOf(context.layout.gradlew.toString(), ":desktopApp:windowsPackageInputs", "--quiet"),
            workingDirectory = context.layout.root,
            timeout = Duration.ofMinutes(BUILD_MINUTES),
        ).requireSuccess(ErrorCode.BUILD_FAILED, "Building the Windows package inputs")
        guest.powershell("Remove-Item -Recurse -Force ${psQuote(BUILD_FOLDER)} -ErrorAction SilentlyContinue")
        Files.list(packageInputs).use { files -> files.filter(Files::isRegularFile).toList() }.forEach { file ->
            guest.tools.push(file, "$BUILD_INPUTS\\${file.fileName}")
        }
        val version = packageVersion?.let { " -Version ${psQuote(it)}" }.orEmpty()
        val script = "\$jdk = (Get-ChildItem ${psQuote(WindowsGuest.TOOLS)} -Directory -Filter 'jdk-21*' | Sort-Object Name)[-1].FullName\n" +
            "& ${psQuote("$BUILD_INPUTS\\package-msi.ps1")} -Output ${psQuote(BUILD_OUTPUT)} -JavaHome \$jdk$version 2>&1 | Out-String -Width 400\n" +
            "if (-not \$?) { exit 1 }"
        val output = guest.powershell(script, timeout = Duration.ofMinutes(PACKAGE_MINUTES))
        val guestMsi = output.stdout.lines().map(String::trim).lastOrNull { it.endsWith(".msi", ignoreCase = true) }
        if (!output.succeeded || guestMsi == null) {
            val log = context.artifactPath("logs", "package-msi.log")
            Files.writeString(log, output.stdout + output.stderr)
            throw ControlException(
                ErrorCode.BUILD_FAILED,
                "Packaging the MSI in ${WindowsGuest.CLONE} failed.",
                "Read ${context.layout.relativize(log)}.",
            )
        }
        Files.createDirectories(msiFolder)
        val msi = msiFolder.resolve(guestMsi.substringAfterLast('\\'))
        Files.write(msi, guest.tools.pull(guestMsi))
        return BuildResult(appPath = context.layout.relativize(msi), durationMs = System.currentTimeMillis() - started)
    }

    override fun install(): StatusResult {
        guest.requireRunning()
        val msi = packageFile ?: builtMsi() ?: throw ControlException(
            ErrorCode.APP_NOT_STAGED,
            "No Windows MSI is built.",
            "Run `build -t windows`, or pass --package <file>.msi.",
        )
        installer.install(msi)
        return status()
    }

    /** The newest MSI that `build` copied out of the clone. */
    private fun builtMsi(): Path? {
        if (!Files.isDirectory(msiFolder)) return null
        return Files.list(msiFolder).use { files ->
            files.filter { it.fileName.toString().endsWith(".msi") }.toList().maxByOrNull { Files.getLastModifiedTime(it) }
        }
    }

    override fun launch(options: LaunchOptions): LaunchResult {
        if (options.build) build(BuildOptions())
        guest.requireRunning()
        val executable = installedApplication()
            ?: throw ControlException(ErrorCode.APP_NOT_INSTALLED, "Posato is not installed in the clone.", "Run `install -t windows`.")
        guest.tools.installScripts()
        terminateAll()
        if (options.fresh) {
            guest.powershell(
                DATA_DIRECTORIES.joinToString("\n") {
                    "Remove-Item -Recurse -Force \"$it\" -ErrorAction SilentlyContinue"
                },
            )
        }
        // Posato exposes its control ids to accessibility only where this marker exists.
        guest.powershell("New-Item -ItemType File -Force -Path \"\$env:USERPROFILE\\$CONTROL_IDS_MARKER\" | Out-Null")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Marking ${WindowsGuest.CLONE} as a verification host")
        val arguments = options.arguments.joinToString(" ")
        // Start-Process goes through the shell, so the new process inherits no handle of the agent's command and the
        // command returns (its own redirection would inherit them, and the command would wait for Posato). A hidden cmd
        // keeps Posato's output in the logs that `logs` reads.
        val inner = "\"$executable\" $arguments > \"$LOG\" 2> \"$ERROR_LOG\""
        if (guest.tools.uacOn) {
            // With UAC on, Posato starts as a person's does: not elevated.
            guest.tools.startLimited(inner).requireSuccess(ErrorCode.COMMAND_FAILED, "Starting Posato in ${WindowsGuest.CLONE}")
        } else {
            val start = "Start-Process cmd.exe -WindowStyle Hidden -ArgumentList ${psQuote("/c \"$inner\"")}"
            guest.powershell(start).requireSuccess(ErrorCode.COMMAND_FAILED, "Starting Posato in ${WindowsGuest.CLONE}")
        }
        awaitTree()
        val pid = pid()
        stateStore.update(Target.WINDOWS, LaunchedProcess(pid = pid, startedAt = System.currentTimeMillis(), runId = context.runId))
        return LaunchResult(pid = pid, logPath = LOG)
    }

    /** Posato's window is drawn seconds after the process starts; a launch is done when the probe reads its tree. */
    private fun awaitTree() {
        val started = System.currentTimeMillis()
        val deadline = started + REGISTER_MILLIS
        while (true) {
            val dump = guest.tools.probe("dump")
            if (dump.succeeded) return
            // The hidden cmd starts Posato a moment after Start-Process returns, so no process yet is not an exit.
            if (System.currentTimeMillis() - started > START_GRACE_MILLIS && pid() == null) {
                throw ControlException(ErrorCode.COMMAND_FAILED, "Posato exited after launch.", "Run `logs -t windows`.")
            }
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

    /** The installed `Posato.exe`, in the install location the MSI registered or in the default folder; null when neither. */
    private fun installedApplication(): String? = guest.powershell(
        "\$entry = Get-ItemProperty ${WindowsInstaller.UNINSTALL_KEYS} -ErrorAction SilentlyContinue | " +
            "Where-Object { \$_.DisplayName -eq ${psQuote(WindowsInstaller.PRODUCT_NAME)} } | Select-Object -First 1\n" +
            "\$candidates = @(); if (\$entry -and \$entry.InstallLocation) { \$candidates += Join-Path \$entry.InstallLocation 'Posato.exe' }\n" +
            "\$candidates += ${psQuote(DEFAULT_EXECUTABLE)}\n" +
            "\$candidates | Where-Object { Test-Path \$_ } | Select-Object -First 1",
    ).stdout.trim().ifEmpty { null }

    companion object {
        const val DEFAULT_EXECUTABLE = "C:\\Program Files\\Posato\\Posato.exe"

        /** Where Posato's standard output and error go when `launch` starts it; a login launch writes neither. */
        const val LOG = "${WindowsGuest.TOOLS}\\posato.log"
        const val ERROR_LOG = "${WindowsGuest.TOOLS}\\posato-error.log"

        /** Posato for Windows keeps its database, app choices, and instance lock in %LOCALAPPDATA%\\Posato. */
        val DATA_DIRECTORIES = listOf("\$env:LOCALAPPDATA\\Posato")

        /** The installed `Posato.exe`, read without WMI, whose provider can hang in the guest. */
        private const val POSATO_PROCESSES = "Get-Process -Name Posato -ErrorAction SilentlyContinue"
        private const val BUILD_FOLDER = "C:\\Windows\\Temp\\posato-build"
        private const val BUILD_INPUTS = "$BUILD_FOLDER\\inputs"
        private const val BUILD_OUTPUT = "$BUILD_FOLDER\\out"
        private const val PACKAGE_MINUTES = 20L
        private const val HINT_BUILD = "Run `build -t windows` with the clone running, or pass --package."
        private const val HINT_SETUP = "Set up the Windows golden VM as docs/development/unattended-verification.md describes."
        private const val BUILD_MINUTES = 15L
        private const val REGISTER_MILLIS = 90_000L
        private const val START_GRACE_MILLIS = 15_000L
        private const val POLL_MILLIS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
