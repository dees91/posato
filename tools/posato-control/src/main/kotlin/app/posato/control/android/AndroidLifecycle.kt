package app.posato.control.android

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
import java.nio.file.Path
import java.time.Duration

/** Builds, installs, and runs Posato on the Android target; `install` gives the grants chosen with `--grants`. */
class AndroidLifecycle(
    private val context: RunContext,
    private val device: AndroidDevice,
    private val stateStore: RunStateStore,
) : Lifecycle {
    var grants: AndroidGrants = AndroidGrants.ALL

    /** A built APK to install instead of the debug APK, such as a release-signed candidate. */
    var packageFile: Path? = null

    override fun doctor() = AndroidDoctor(context, device).checks()

    override fun build(options: BuildOptions): BuildResult {
        val started = System.currentTimeMillis()
        context.subprocess.run(
            listOf(context.layout.gradlew.toString(), ":androidApp:assembleDebug", "--quiet"),
            workingDirectory = context.layout.root,
            timeout = Duration.ofMinutes(BUILD_MINUTES),
        ).requireSuccess(ErrorCode.BUILD_FAILED, "Building the Android debug APK")
        return BuildResult(appPath = context.layout.relativize(device.apk), durationMs = System.currentTimeMillis() - started)
    }

    override fun install(): StatusResult {
        device.requireReady()
        device.app.install(grants, packageFile)
        return status()
    }

    override fun launch(options: LaunchOptions): LaunchResult {
        if (options.build) build(BuildOptions())
        device.requireReady()
        if (!device.app.installed()) {
            throw ControlException(
                ErrorCode.APP_NOT_INSTALLED,
                "Posato is not installed on ${device.serial}.",
                "Run `install -t android`.",
            )
        }
        device.app.terminate()
        if (options.fresh) device.app.clearData()
        device.app.launch()
        val pid = device.app.pid()
        stateStore.update(
            Target.ANDROID,
            LaunchedProcess(pid = pid, startedAt = System.currentTimeMillis(), udid = device.serial, runId = context.runId),
        )
        return LaunchResult(pid = pid, udid = device.serial)
    }

    override fun terminate(): StatusResult {
        device.app.terminate()
        stateStore.update(Target.ANDROID, null)
        return status()
    }

    override fun status(): StatusResult {
        device.requireReady()
        val pid = device.app.pid()
        return StatusResult(
            installed = device.app.installed(),
            running = pid != null,
            pid = pid,
            udid = device.serial,
            appPath = context.layout.relativize(device.apk),
            lastRunId = stateStore.load().android?.runId,
        )
    }

    private companion object {
        const val BUILD_MINUTES = 15L
    }
}
