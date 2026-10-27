package app.posato.control.windows

import app.posato.control.backend.Evidence
import app.posato.control.backend.LogsResult
import app.posato.control.backend.ResetPlan
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.windows.WindowsGuest.Companion.psQuote
import java.nio.file.Files
import java.nio.file.Path

/** Screenshots, Posato's log, and copies of its databases, read from the Windows clone. */
class WindowsEvidence(
    private val context: RunContext,
    private val guest: WindowsGuest,
    private val lifecycle: WindowsLifecycle,
) : Evidence {
    override fun screenshot(
        name: String,
        out: Path?,
    ): Path {
        guest.requireRunning()
        return context.recordArtifact(guest.tools.screenshot(out ?: context.artifactPath("screenshots", "$name.png")))
    }

    override fun logs(
        tail: Int,
        streamSeconds: Int?,
    ): LogsResult {
        guest.requireRunning()
        streamSeconds?.let { Thread.sleep(it * MILLIS_PER_SECOND) }
        val text = guest.powershell("Get-Content ${psQuote(WindowsLifecycle.LOG)} -Tail $tail -ErrorAction SilentlyContinue").stdout
        val file = context.artifactPath("logs", "windows-posato.log")
        Files.writeString(file, text)
        context.recordArtifact(file)
        return LogsResult(context.layout.relativize(file), text.lines().filter { it.isNotBlank() })
    }

    /** Copies of the databases with their journals, taken now, so `db` reads what Posato last wrote. */
    override fun databasePaths(): List<Path> {
        guest.requireRunning()
        val list = WindowsLifecycle.DATA_DIRECTORIES.joinToString(",") { "\"$it\"" }
        val files = guest.powershell(
            "Get-ChildItem $list -File -ErrorAction SilentlyContinue | " +
                "Where-Object { \$_.Name -match '\\.db(-journal|-wal)?\$' } | ForEach-Object { \$_.FullName }",
        ).stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (files.none { it.endsWith(".db") }) {
            throw ControlException(ErrorCode.APP_NOT_INSTALLED, "Posato in the clone has no database yet.", "Launch it once so it creates one.")
        }
        files.forEach { file ->
            val destination = context.artifactPath("databases", file.substringAfterLast('\\'))
            Files.write(destination, guest.tools.pull(file))
            context.recordArtifact(destination)
        }
        return files.filter { it.endsWith(".db") }.map { context.artifactPath("databases", it.substringAfterLast('\\')) }
    }

    override fun reset(
        dryRun: Boolean,
        keepInstall: Boolean,
    ): ResetPlan {
        guest.requireRunning()
        val plan = WindowsLifecycle.DATA_DIRECTORIES.map { "$it in ${WindowsGuest.CLONE}" }
        if (dryRun) return ResetPlan(plan, uninstall = !keepInstall, performed = false)
        val backup = runCatching { databasePaths().firstOrNull()?.parent }.getOrNull()
        lifecycle.terminate()
        guest.powershell(WindowsLifecycle.DATA_DIRECTORIES.joinToString("\n") { "Remove-Item -Recurse -Force \"$it\" -ErrorAction SilentlyContinue" })
        if (!keepInstall) {
            val installer = lifecycle.installer
            if (installer.productCode(WindowsInstaller.PRODUCT_NAME) != null) installer.uninstall(null, WindowsInstaller.PRODUCT_NAME)
            guest.powershell("Remove-Item ${psQuote(WindowsLifecycle.JAR)} -ErrorAction SilentlyContinue")
        }
        return ResetPlan(plan, uninstall = !keepInstall, performed = true, backupDirectory = backup?.let(context.layout::relativize))
    }

    override fun cleanup(
        dryRun: Boolean,
        purgeDerivedData: Boolean,
    ): List<String> {
        if (!dryRun && guest.running()) lifecycle.terminate()
        return listOf("end Posato in ${WindowsGuest.CLONE}")
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
