package app.posato.control.vm

import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import java.nio.file.Path
import java.time.Duration

/** The `tart` command line for Tart macOS guests; guest commands run through `tart exec` and `tart-guest-agent`. */
class Tart(
    private val context: RunContext
) {
    fun list(): List<TartVm> = parseTartList(run(listOf(TART, "list", "--format", "json"), "Listing Tart VMs").stdout)

    fun clone(
        source: String,
        name: String
    ) {
        run(listOf(TART, "clone", source, name), "Cloning $source", CLONE_TIMEOUT)
    }

    fun delete(name: String) {
        run(listOf(TART, "delete", name), "Deleting $name")
    }

    fun stop(name: String) {
        context.subprocess.run(listOf(TART, "stop", name), timeout = STOP_TIMEOUT)
    }

    /** Boots headless with Virtualization's own VNC server; the address, with its password, goes only to [log]. */
    fun start(
        name: String,
        jdk: Path,
        log: Path
    ): Process = context.subprocess.startDetached(
        listOf(TART, "run", name, "--no-graphics", "--vnc-experimental", "--dir=$JDK_SHARE:$jdk:ro"),
        log,
    )

    /** Runs a `/bin/sh` script in the logged-in user's Aqua session of the guest. */
    fun exec(
        name: String,
        script: String,
        stdin: String? = null,
        timeout: Duration = EXEC_TIMEOUT
    ): ProcessOutput {
        val command = buildList {
            addAll(listOf(TART, "exec"))
            if (stdin != null) add("-i")
            addAll(listOf(name, "/bin/sh", "-c", script))
        }
        return context.subprocess.run(command, stdin = stdin, timeout = timeout)
    }

    /** Streams `hostScript`'s output into `guestScript` running in the guest, for example a tar archive. */
    fun pipe(
        name: String,
        hostScript: String,
        guestScript: String,
        what: String
    ) {
        val script = "set -o pipefail; $hostScript | $TART exec -i ${shellQuote(name)} /bin/sh -c ${shellQuote(guestScript)}"
        run(listOf("/bin/sh", "-c", script), what, COPY_TIMEOUT)
    }

    /** Streams `guestScript`'s output from the guest into `hostScript`. */
    fun pipeOut(
        name: String,
        guestScript: String,
        hostScript: String,
        what: String
    ) {
        val script = "set -o pipefail; $TART exec ${shellQuote(name)} /bin/sh -c ${shellQuote(guestScript)} | $hostScript"
        run(listOf("/bin/sh", "-c", script), what, COPY_TIMEOUT)
    }

    private fun run(
        command: List<String>,
        what: String,
        timeout: Duration = EXEC_TIMEOUT
    ): ProcessOutput = context.subprocess.run(command, timeout = timeout).requireSuccess(ErrorCode.COMMAND_FAILED, what)

    companion object {
        const val TART = "tart"
        const val JDK_SHARE = "jdk"
        const val GUEST_JDK_BIN = "/Volumes/My Shared Files/jdk/Contents/Home/bin"
        val EXEC_TIMEOUT: Duration = Duration.ofMinutes(10)
        private val CLONE_TIMEOUT: Duration = Duration.ofMinutes(5)
        private val COPY_TIMEOUT: Duration = Duration.ofMinutes(10)
        private val STOP_TIMEOUT: Duration = Duration.ofMinutes(2)
    }
}
