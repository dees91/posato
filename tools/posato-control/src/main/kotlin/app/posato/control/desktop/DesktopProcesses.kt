package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.LaunchedProcess
import app.posato.control.core.RunContext
import app.posato.control.core.TrackedProcess
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists

/** One live process reduced to what process targeting needs: its pid and the executable it runs. */
data class ProcessEntry(
    val pid: Long,
    val command: String,
)

class DesktopProcesses(
    private val context: RunContext,
    private val liveProcesses: () -> List<ProcessEntry> = ::currentProcesses,
) {
    private val executable: Path = context.layout.desktopApplication.resolve("Contents").resolve("MacOS").resolve("Posato")

    fun executablePath(): Path = executable

    /** True only when the tracked pid is alive, started at the recorded instant, and runs the staged executable. */
    fun isTracked(process: LaunchedProcess?): Boolean {
        val handle = TrackedProcess.handleOf(process?.pid, process?.startedAt) ?: return false
        return handle.info().command().map { it == executable.toString() }.orElse(false)
    }

    fun trackedPid(process: LaunchedProcess?): Long? = process?.pid?.takeIf { isTracked(process) }

    fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    fun foreignPids(ownPid: Long?): List<Long> = liveProcesses()
        .filter { it.command == executable.toString() }
        .map { it.pid }
        .filter { it != ownPid }

    /**
     * Resolves the process an element command addresses. A null selector keeps the tracked application, so every
     * command without `--process` behaves exactly as before. Any other outcome is a precondition failure.
     */
    fun resolveTarget(
        selector: String?,
        tracked: LaunchedProcess?
    ): Long = ProcessTargeting.resolve(selector, trackedPid(tracked)) { containedProcesses() }

    /** Every live process whose executable resolves inside the staged bundle, so nothing outside it is addressable. */
    fun containedProcesses(): List<ProcessEntry> {
        val bundle = realPath(context.layout.desktopApplication) ?: return emptyList()
        return liveProcesses().filter { entry ->
            val command = realPath(Path.of(entry.command)) ?: return@filter false
            command != bundle && command.startsWith(bundle)
        }
    }

    private fun realPath(path: Path): Path? = try {
        if (path.exists()) path.toRealPath() else null
    } catch (_: IOException) {
        null
    }

    fun terminate(process: LaunchedProcess?) {
        if (isTracked(process)) TrackedProcess.terminate(process?.pid, process?.startedAt)
    }

    fun signingMode(): String {
        val output = context.subprocess.run(listOf("/usr/bin/codesign", "-dv", "--verbose=2", context.layout.desktopApplication.toString()))
        if (!output.succeeded) return "unsigned"
        return signingModeOf(output.stderr)
    }

    fun requireStaged() {
        if (!executable.toFile().isFile) {
            throw ControlException(
                ErrorCode.APP_NOT_STAGED,
                "The desktop application is missing at ${context.layout.relativize(context.layout.desktopApplication)}.",
                "Run `posato-control build -t desktop` first.",
            )
        }
    }
}

/** Classifies `codesign -dv --verbose=2` output: a Developer ID candidate, a development package, or ad-hoc. */
internal fun signingModeOf(details: String): String {
    val lines = details.lineSequence().map { it.trim() }.toList()
    val team = lines.firstOrNull { it.startsWith("TeamIdentifier=") }?.substringAfter('=')
    return when {
        team.isNullOrEmpty() || team == "not set" -> "adhoc"
        lines.any { it.startsWith("Authority=Developer ID Application:") } -> "developer-id"
        else -> "development"
    }
}

private fun currentProcesses(): List<ProcessEntry> = ProcessHandle.allProcesses()
    .toList()
    .mapNotNull { handle -> handle.info().command().orElse(null)?.let { ProcessEntry(handle.pid(), it) } }
