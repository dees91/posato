package app.posato.control.linux

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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Builds the Linux package inputs on the host, packages and installs them in the clone, and runs Posato there. The
 * clone itself comes from `linux create`, as a macOS clone comes from `vm create`.
 */
class LinuxLifecycle(
    private val context: RunContext,
    private val guest: LinuxGuest,
    private val stateStore: RunStateStore,
) : Lifecycle {
    val packageInputs: Path = context.layout.root.resolve("desktopApp/build/linux/package")

    /** A built `.deb` to install instead of packaging the inputs in the clone, such as a release candidate. */
    var packageFile: Path? = null

    override fun doctor(): List<DoctorCheck> {
        val clone = if (guest.running()) {
            DoctorCheck.pass("linux.clone", "The Linux clone ${LinuxGuest.CLONE} runs.")
        } else {
            DoctorCheck.fail("linux.clone", "The Linux clone is not running.", "Run `linux create`; stop any macOS clone first.", Severity.WARN)
        }
        val golden = if (guest.goldenExists()) {
            DoctorCheck.pass("linux.golden", "The Linux golden VM exists.")
        } else {
            DoctorCheck.fail("linux.golden", "No Linux golden VM.", "Create it as docs/development/unattended-verification.md describes.")
        }
        val inputs = if (Files.isRegularFile(packageInputs.resolve("posato.jar"))) {
            DoctorCheck.pass("linux.package", "The Linux package inputs are built.")
        } else {
            DoctorCheck.fail("linux.package", "The Linux package inputs are not built.", "Run `build -t linux`.", Severity.WARN)
        }
        return listOf(golden, clone, inputs) + if (guest.running()) guestChecks() else emptyList()
    }

    private fun guestChecks(): List<DoctorCheck> {
        val installed = guest.exec("dpkg -s posato >/dev/null 2>&1 && echo yes").stdout.contains("yes")
        val helper = guest.exec("systemctl is-active posato-helper").stdout.trim()
        return listOf(
            if (installed) {
                DoctorCheck.pass("linux.installed", "Posato is installed in the clone.")
            } else {
                DoctorCheck.fail("linux.installed", "Posato is not installed in the clone.", "Run `install -t linux`.", Severity.WARN)
            },
            if (helper == "active") {
                DoctorCheck.pass("linux.helper", "The pausing service runs.")
            } else {
                DoctorCheck.fail("linux.helper", "The pausing service is $helper.", "Onboarding installs it: `flow onboard -t linux`.", Severity.INFO)
            },
        )
    }

    override fun build(options: BuildOptions): BuildResult {
        val started = System.currentTimeMillis()
        context.subprocess.run(
            listOf(context.layout.gradlew.toString(), ":desktopApp:linuxPackageInputs", "--quiet"),
            workingDirectory = context.layout.root,
            timeout = Duration.ofMinutes(BUILD_MINUTES),
        ).requireSuccess(ErrorCode.BUILD_FAILED, "Building the Linux package inputs")
        return BuildResult(appPath = context.layout.relativize(packageInputs), durationMs = System.currentTimeMillis() - started)
    }

    override fun install(): StatusResult {
        guest.requireRunning()
        packageFile?.let(guest::installPackage) ?: guest.install(packageInputs)
        return status()
    }

    override fun launch(options: LaunchOptions): LaunchResult {
        if (options.build) build(BuildOptions())
        guest.requireRunning()
        if (!installed()) throw ControlException(ErrorCode.APP_NOT_INSTALLED, "Posato is not installed in the clone.", "Run `install -t linux`.")
        guest.terminate()
        if (options.fresh) guest.exec("rm -rf ${LinuxGuest.DATA_DIRECTORY}")
        guest.accessibility.launch()
        val pid = pid()
        stateStore.update(Target.LINUX, LaunchedProcess(pid = pid, startedAt = System.currentTimeMillis(), runId = context.runId))
        return LaunchResult(pid = pid)
    }

    override fun terminate(): StatusResult {
        guest.requireRunning()
        guest.terminate()
        stateStore.update(Target.LINUX, null)
        return status()
    }

    override fun status(): StatusResult {
        guest.requireRunning()
        val pid = pid()
        return StatusResult(
            installed = installed(),
            running = pid != null,
            pid = pid,
            appPath = context.layout.relativize(packageFile ?: packageInputs),
            containerPath = LinuxGuest.DATA_DIRECTORY,
            lastRunId = stateStore.load().linux?.runId,
        )
    }

    fun pid(): Long? = guest.exec("pgrep -x posato | head -1").stdout.trim().toLongOrNull()

    private fun installed(): Boolean = guest.exec("dpkg -s posato >/dev/null 2>&1 && echo yes").stdout.contains("yes")

    private companion object {
        const val BUILD_MINUTES = 15L
    }
}
