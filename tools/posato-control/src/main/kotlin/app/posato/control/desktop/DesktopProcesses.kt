package app.posato.control.desktop

import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import java.nio.file.Path

class DesktopProcesses(
    private val context: RunContext
) {
    private val executable: Path = context.layout.stagedDesktopApplication.resolve("Contents").resolve("MacOS").resolve("Posato")

    fun executablePath(): Path = executable

    fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    fun foreignPids(ownPid: Long?): List<Long> = ProcessHandle.allProcesses()
        .filter { handle -> handle.info().command().map { it == executable.toString() }.orElse(false) }
        .map { it.pid() }
        .filter { it != ownPid }
        .toList()

    fun terminate(pid: Long) {
        val handle = ProcessHandle.of(pid).orElse(null) ?: return
        handle.destroy()
        val deadline = System.currentTimeMillis() + GRACE_MS
        while (handle.isAlive && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        if (handle.isAlive) handle.destroyForcibly()
    }

    fun signingMode(): String {
        val output = context.subprocess.run(listOf("/usr/bin/codesign", "-dv", "--verbose=2", context.layout.stagedDesktopApplication.toString()))
        if (!output.succeeded) return "unsigned"
        val team = output.stderr.lineSequence().firstOrNull { it.startsWith("TeamIdentifier=") }?.substringAfter('=')?.trim()
        return if (team.isNullOrEmpty() || team == "not set") "adhoc" else "development"
    }

    fun requireStaged() {
        if (!executable.toFile().isFile) {
            throw app.posato.control.core.ControlException(
                ErrorCode.APP_NOT_STAGED,
                "The staged desktop application is missing at ${context.layout.relativize(context.layout.stagedDesktopApplication)}.",
                "Run `posato-control build -t desktop` first.",
            )
        }
    }

    private companion object {
        const val GRACE_MS = 5_000L
        const val POLL_MS = 100L
    }
}
