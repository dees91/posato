package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.LocalConfiguration
import app.posato.control.core.RepoLayout
import app.posato.control.core.RunContext
import app.posato.control.model.Envelope
import app.posato.control.model.ErrorPayload
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Runs a desktop command inside a Tart guest: `posato-control <command> -t desktop --vm primary ...` becomes the same
 * command for the guest's own driver through `tart exec`, which runs in the logged-in user's Aqua session. The
 * guest's run directory is copied back to `<run>/guest/` and the envelope's paths are rewritten to point there.
 */
object GuestRelay {
    private const val OPTION = "--vm"
    private val HOST_COMMANDS = setOf("vm", "build", "install", "devices")

    /** The VM line a command addresses, or null when it runs on the host as before. */
    fun lineIn(args: List<String>): VmLine? {
        val index = args.indexOf(OPTION)
        if (index < 0 || args.firstOrNull() in HOST_COMMANDS) return null
        val value = args.getOrNull(index + 1) ?: throw ControlException(ErrorCode.USAGE, "$OPTION needs primary, peer, or legacy.")
        return VmLine.parse(value)
    }

    fun forward(
        args: List<String>,
        line: VmLine
    ): Int {
        val layout = RepoLayout.discover()
        val forwarded = args.toMutableList().apply {
            val index = indexOf(OPTION)
            removeAt(index)
            removeAt(index)
        }
        val runId = forwarded.getOrNull(forwarded.indexOf("--run-id") + 1)?.takeIf { "--run-id" in forwarded }
            ?: RunContext.newRunId().also { forwarded.addAll(listOf("--run-id", it)) }
        val context = RunContext(layout, LocalConfiguration.load(layout), runId, layout.runsDirectory, verbose = false, timeout = Tart.EXEC_TIMEOUT)
        return try {
            relay(context, line, forwarded, runId)
        } catch (exception: ControlException) {
            val envelope = Envelope(
                ok = false,
                command = forwarded.firstOrNull() ?: "posato-control",
                runId = runId,
                durationMs = 0,
                error = ErrorPayload(exception.code.name, exception.message ?: exception.code.name, exception.hint),
            )
            println(ControlJson.pretty.encodeToString(Envelope.serializer(), envelope))
            exception.code.exitCode
        }
    }

    private fun relay(
        context: RunContext,
        line: VmLine,
        forwarded: List<String>,
        runId: String
    ): Int {
        if (forwarded.windowed(2).none { (option, value) -> option in setOf("-t", "--target") && value == "desktop" }) {
            throw ControlException(ErrorCode.USAGE, "$OPTION applies to the desktop target.", "Add -t desktop.")
        }
        val lifecycle = VmLifecycle(context)
        lifecycle.requireRunning(line)
        val tart = Tart(context)
        val (arguments, scenario) = scenarioOverStdin(forwarded)
        val script = "export PATH=${shellQuote(Tart.GUEST_JDK_BIN)}:\$PATH; cd ~/posato-run && " +
            "tools/posato-control/build/install/posato-control/bin/posato-control " + arguments.joinToString(" ") { shellQuote(it) }
        val stdin = when (scenario) {
            null -> null
            STANDARD_INPUT -> System.`in`.readBytes().decodeToString()
            else -> Files.readString(Path.of(scenario))
        }
        val output = tart.exec(line.cloneName, script, stdin = stdin, timeout = Duration.ofMinutes(RELAY_TIMEOUT_MINUTES))
        val hostRun = context.layout.runsDirectory.resolve(runId).resolve("guest")
        Files.createDirectories(hostRun)
        tart.pipeOut(
            line.cloneName,
            "cd ~/posato-run/build/verification/runs 2>/dev/null && [ -d ${shellQuote(
                runId,
            )} ] && tar -C ${shellQuote(runId)} -cf - . || tar -cf - -T /dev/null",
            "tar -C ${shellQuote(hostRun.toString())} -xf -",
            "Copying the guest run directory",
        )
        print(relocateGuestPaths(output.stdout, runId))
        System.err.print(output.stderr)
        return output.exitCode
    }

    private const val RELAY_TIMEOUT_MINUTES = 30L
}
