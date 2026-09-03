package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.LaunchedProcess
import app.posato.control.core.RunContext
import app.posato.control.core.TrackedProcess
import java.nio.file.Path

class DesktopProcesses(
    private val context: RunContext
) {
    private val executable: Path = context.layout.stagedDesktopApplication.resolve("Contents").resolve("MacOS").resolve("Posato")

    fun executablePath(): Path = executable

    /** True only when the tracked pid is alive, started at the recorded instant, and runs the staged executable. */
    fun isTracked(process: LaunchedProcess?): Boolean {
        val handle = TrackedProcess.handleOf(process?.pid, process?.startedAt) ?: return false
        return handle.info().command().map { it == executable.toString() }.orElse(false)
    }

    fun trackedPid(process: LaunchedProcess?): Long? = process?.pid?.takeIf { isTracked(process) }

    fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    fun foreignPids(ownPid: Long?): List<Long> = ProcessHandle.allProcesses()
        .filter { handle -> handle.info().command().map { it == executable.toString() }.orElse(false) }
        .map { it.pid() }
        .filter { it != ownPid }
        .toList()

    fun terminate(process: LaunchedProcess?) {
        if (isTracked(process)) TrackedProcess.terminate(process?.pid, process?.startedAt)
    }

    fun signingMode(): String {
        val output = context.subprocess.run(listOf("/usr/bin/codesign", "-dv", "--verbose=2", context.layout.stagedDesktopApplication.toString()))
        if (!output.succeeded) return "unsigned"
        val team = output.stderr.lineSequence().firstOrNull { it.startsWith("TeamIdentifier=") }?.substringAfter('=')?.trim()
        return if (team.isNullOrEmpty() || team == "not set") "adhoc" else "development"
    }

    fun requireStaged() {
        if (!executable.toFile().isFile) {
            throw ControlException(
                ErrorCode.APP_NOT_STAGED,
                "The staged desktop application is missing at ${context.layout.relativize(context.layout.stagedDesktopApplication)}.",
                "Run `posato-control build -t desktop` first.",
            )
        }
    }
}
