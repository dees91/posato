package app.posato.control.android

import app.posato.control.backend.Evidence
import app.posato.control.backend.LogsResult
import app.posato.control.backend.ResetPlan
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Screenshots, logcat, and copies of Posato's databases, read through `run-as` on the debuggable build. */
class AndroidEvidence(
    private val context: RunContext,
    private val device: AndroidDevice,
) : Evidence {
    override fun screenshot(
        name: String,
        out: Path?,
    ): Path = context.recordArtifact(device.screenshot(out ?: context.artifactPath("screenshots", "$name.png")))

    override fun logs(
        tail: Int,
        streamSeconds: Int?,
    ): LogsResult {
        val filter = device.app.pid()?.let { "--pid=$it" } ?: ""
        val output = device.adb("logcat", "-d", "-t", "$tail", *listOfNotNull(filter.ifBlank { null }).toTypedArray()).stdout
        val streamed = streamSeconds?.let { seconds ->
            context.subprocess.captureFor(listOf(AndroidDevice.ADB, "-s", device.serial, "logcat", "-T", "1"), Duration.ofSeconds(seconds.toLong()))
        }.orEmpty()
        val file = context.artifactPath("logs", "android-logcat.txt")
        Files.writeString(file, output + streamed)
        context.recordArtifact(file)
        return LogsResult(context.layout.relativize(file), (output + streamed).lines().filter { it.isNotBlank() }.takeLast(tail))
    }

    /** Copies of the databases with their journals, taken now, so `db` reads what Posato last wrote. */
    override fun databasePaths(): List<Path> {
        device.requireReady()
        val names = device.shell("run-as ${AndroidDevice.PACKAGE} ls databases").stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (names.none { it.endsWith(".db") }) {
            throw ControlException(
                ErrorCode.APP_NOT_INSTALLED,
                "Posato on ${device.serial} has no database yet.",
                "Launch it once so it creates one.",
            )
        }
        names.forEach { name -> pull(name, context.artifactPath("databases", name)) }
        return names.filter { it.endsWith(".db") }.map { context.artifactPath("databases", it) }
    }

    private fun pull(
        name: String,
        destination: Path,
    ) {
        val process = ProcessBuilder(AndroidDevice.ADB, "-s", device.serial, "exec-out", "run-as", AndroidDevice.PACKAGE, "cat", "databases/$name")
            .redirectOutput(destination.toFile())
            .start()
        process.waitFor()
        context.recordArtifact(destination)
    }

    override fun reset(
        dryRun: Boolean,
        keepInstall: Boolean,
    ): ResetPlan {
        device.requireReady()
        val plan = listOf("${AndroidDevice.PACKAGE} data on ${device.serial}")
        if (dryRun) return ResetPlan(plan, uninstall = !keepInstall, performed = false)
        val backup = runCatching { databasePaths().firstOrNull()?.parent }.getOrNull()
        if (keepInstall) device.app.clearData() else device.app.uninstall()
        return ResetPlan(plan, uninstall = !keepInstall, performed = true, backupDirectory = backup?.let(context.layout::relativize))
    }

    override fun cleanup(
        dryRun: Boolean,
        purgeDerivedData: Boolean,
    ): List<String> {
        if (!dryRun) device.app.terminate()
        return listOf("force-stop ${AndroidDevice.PACKAGE} on ${device.serial}")
    }
}
