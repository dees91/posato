package app.posato.control.linux

import app.posato.control.backend.Evidence
import app.posato.control.backend.LogsResult
import app.posato.control.backend.ResetPlan
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Screenshots, Posato's log, and copies of its databases, read from the clone. */
class LinuxEvidence(
    private val context: RunContext,
    private val guest: LinuxGuest,
) : Evidence {
    override fun screenshot(
        name: String,
        out: Path?,
    ): Path {
        guest.requireRunning()
        return context.recordArtifact(guest.screen.screenshot(out ?: context.artifactPath("screenshots", "$name.png")))
    }

    override fun logs(
        tail: Int,
        streamSeconds: Int?,
    ): LogsResult {
        guest.requireRunning()
        streamSeconds?.let { Thread.sleep(it * MILLIS_PER_SECOND) }
        val text = guest.exec("tail -n $tail /tmp/posato.log 2>/dev/null").stdout
        val file = context.artifactPath("logs", "linux-posato.log")
        Files.writeString(file, text)
        context.recordArtifact(file)
        return LogsResult(context.layout.relativize(file), text.lines().filter { it.isNotBlank() })
    }

    /** Copies of the databases with their journals, taken now, so `db` reads what Posato last wrote. */
    override fun databasePaths(): List<Path> {
        guest.requireRunning()
        val names = guest.exec("ls ${LinuxGuest.DATA_DIRECTORY} 2>/dev/null").stdout.lines().map { it.trim() }
            .filter { it.endsWith(".db") || it.endsWith(".db-journal") || it.endsWith(".db-wal") }
        if (names.none { it.endsWith(".db") }) {
            throw ControlException(ErrorCode.APP_NOT_INSTALLED, "Posato in the clone has no database yet.", "Launch it once so it creates one.")
        }
        names.forEach { name ->
            val encoded = guest.exec("base64 -w0 ${LinuxGuest.DATA_DIRECTORY}/$name").requireSuccess(ErrorCode.COMMAND_FAILED, "Reading $name").stdout
            val destination = context.artifactPath("databases", name)
            Files.write(destination, Base64.getDecoder().decode(encoded.trim()))
            context.recordArtifact(destination)
        }
        return names.filter { it.endsWith(".db") }.map { context.artifactPath("databases", it) }
    }

    override fun reset(
        dryRun: Boolean,
        keepInstall: Boolean,
    ): ResetPlan {
        guest.requireRunning()
        val plan = listOf("${LinuxGuest.DATA_DIRECTORY} in ${LinuxGuest.CLONE}")
        if (dryRun) return ResetPlan(plan, uninstall = !keepInstall, performed = false)
        val backup = runCatching { databasePaths().firstOrNull()?.parent }.getOrNull()
        guest.terminate()
        guest.exec("rm -rf ${LinuxGuest.DATA_DIRECTORY}")
        if (!keepInstall) guest.exec("sudo -n DEBIAN_FRONTEND=noninteractive apt-get remove -y -q posato")
        return ResetPlan(plan, uninstall = !keepInstall, performed = true, backupDirectory = backup?.let(context.layout::relativize))
    }

    override fun cleanup(
        dryRun: Boolean,
        purgeDerivedData: Boolean,
    ): List<String> {
        if (!dryRun && guest.running()) guest.terminate()
        return listOf("end Posato in ${LinuxGuest.CLONE}")
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
