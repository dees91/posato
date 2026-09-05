package app.posato.control.desktop

import app.posato.control.backend.Evidence
import app.posato.control.backend.LogsResult
import app.posato.control.backend.ResetPlan
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.core.Target
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readLines

class DesktopEvidence(
    private val context: RunContext,
    private val bridge: AxBridge,
    private val processes: DesktopProcesses,
    private val stateStore: RunStateStore,
    private val processSelector: String? = null,
) : Evidence {
    override fun screenshot(
        name: String,
        out: Path?
    ): Path {
        val tracked = stateStore.load().desktop
        val pid = processes.resolveTarget(processSelector, tracked)
        requireScreenRecording()
        // The tracked application is captured at layer 0 as before; another process may own a panel above that layer.
        val addressesTracked = pid == tracked?.pid
        val candidates = bridge.windows(pid).let { windows -> if (addressesTracked) windows.filter { it.layer == 0 } else windows }
        val windowId = candidates.maxByOrNull { it.w * it.h }?.id
            ?: throw ControlException(ErrorCode.APP_NOT_RUNNING, "${addressed()} has no visible window.")
        val destination = out ?: context.artifactPath("screenshots", "$name.png")
        Files.createDirectories(destination.toAbsolutePath().parent)
        context.subprocess.run(listOf("/usr/sbin/screencapture", "-x", "-o", "-l", windowId.toString(), destination.toString()))
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Capturing the window")
        return context.recordArtifact(destination)
    }

    private fun addressed(): String = processSelector?.let { "The selected process '$it'" } ?: "The desktop application"

    private fun requireScreenRecording() {
        if (bridge.permissions().screenRecording) return
        throw ControlException(
            ErrorCode.TCC_SCREEN_RECORDING_DENIED,
            "Screen Recording access is not granted, so window capture would produce an empty image.",
            "System Settings > Privacy & Security > Screen & System Audio Recording: enable the terminal application, then restart it.",
        )
    }

    override fun logs(
        tail: Int,
        streamSeconds: Int?
    ): LogsResult {
        if (streamSeconds != null) {
            throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "Log streaming is only available on the Simulator.",
                "Use `logs -t desktop --tail N` to read the captured launch log.",
            )
        }
        val logPath = stateStore.load().desktop?.logPath?.let { Path.of(it) }
            ?: return LogsResult(null, emptyList())
        val lines = if (logPath.exists()) logPath.readLines().takeLast(tail) else emptyList()
        return LogsResult(context.layout.relativize(logPath), lines)
    }

    override fun databasePaths(): List<Path> = DesktopPaths.databases

    override fun reset(
        dryRun: Boolean,
        keepInstall: Boolean
    ): ResetPlan {
        val deletions = DesktopPaths.databases.flatMap { database ->
            listOf(database) + DesktopPaths.sidecarSuffixes.map { suffix -> database.resolveSibling(database.fileName.toString() + suffix) }
        }.filter { it.exists() }
        if (dryRun) return ResetPlan(deletions.map { it.toString() }, uninstall = false, performed = false)
        processes.terminate(stateStore.load().desktop)
        stateStore.update(Target.DESKTOP, null)
        val backup = context.artifactPath("backup", "desktop")
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
        val tracked = stateStore.load().desktop ?: return emptyList()
        val actions = listOfNotNull(processes.trackedPid(tracked)?.let { "terminate desktop pid $it" })
        if (!dryRun) {
            processes.terminate(tracked)
            stateStore.update(Target.DESKTOP, null)
        }
        return actions
    }
}
