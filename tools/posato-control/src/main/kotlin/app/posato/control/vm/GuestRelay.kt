package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.LocalConfiguration
import app.posato.control.core.RepoLayout
import app.posato.control.core.RunContext
import app.posato.control.model.Envelope
import app.posato.control.model.ErrorPayload
import app.posato.control.model.Scenario
import app.posato.control.model.humanLines
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Runs a desktop command inside a Tart guest: `posato-control <command> -t desktop --vm primary ...` becomes the same
 * command for the guest's own driver through `tart exec`, which runs in the logged-in user's Aqua session. The
 * guest's run directory is copied back to `<run>/guest/`, the envelope's paths are rewritten to point there, and the
 * envelope is kept there as `envelope.json`.
 */
object GuestRelay {
    private const val OPTION = "--vm"
    private val HOST_COMMANDS = setOf("vm", "build", "install", "devices")

    /** The VM line a command addresses, or null when it runs on the host as before. */
    fun lineIn(args: List<String>): VmLine? {
        val index = args.indexOf(OPTION)
        if (index < 0 || args.firstOrNull() in HOST_COMMANDS) return null
        val value = args.getOrNull(index + 1) ?: throw ControlException(ErrorCode.USAGE, "$OPTION needs primary, peer, legacy, or ventura.")
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
        val runId = try {
            relayRunId(forwarded) ?: RunContext.newRunId().also { forwarded.addAll(listOf(RUN_ID_OPTION, it)) }
        } catch (exception: ControlException) {
            return report(forwarded, "none", exception)
        }
        val context = RunContext(layout, LocalConfiguration.load(layout), runId, layout.runsDirectory, verbose = false, timeout = Tart.EXEC_TIMEOUT)
        return try {
            relay(context, line, forwarded, runId)
        } catch (exception: ControlException) {
            report(forwarded, runId, exception)
        }
    }

    /** Prints a refusal as the envelope the command itself would have printed, and returns its exit code. */
    private fun report(
        forwarded: List<String>,
        runId: String,
        exception: ControlException,
    ): Int {
        val envelope = Envelope(
            ok = false,
            command = forwarded.firstOrNull() ?: "posato-control",
            runId = runId,
            durationMs = 0,
            result = exception.result,
            error = ErrorPayload(exception.code.name, exception.message ?: exception.code.name, exception.hint),
        )
        if (HUMAN_OPTION in forwarded) {
            envelope.humanLines().forEach(::println)
        } else {
            println(ControlJson.pretty.encodeToString(Envelope.serializer(), envelope))
        }
        return exception.code.exitCode
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
        requireGuestRoom(context.layout.root)
        requireCurrentTooling(context, line)
        val (arguments, scenario) = scenarioOverStdin(forwarded)
        // Read once from either source, so the guard decides on the same text that the guest runs.
        val stdin = when (scenario) {
            null -> null
            STANDARD_INPUT -> System.`in`.readBytes().decodeToString()
            else -> Files.readString(Path.of(scenario))
        }
        if (startsPackage(forwarded, stdin)) lifecycle.requireCurrentPackage(line)
        val output = if (checksICloudKeychain(arguments)) {
            GuestICloudFlow(context, line).run(arguments) { sliced -> runInGuest(context, line, sliced, stdin, runId) }
        } else {
            if (resumesICloudKeychain(arguments)) GuestICloudFlow(context, line).resumeIfPaused()
            runInGuest(context, line, arguments, stdin, runId)
        }
        print(output.envelope)
        System.err.print(output.stderr)
        return output.exitCode
    }

    private fun runInGuest(
        context: RunContext,
        line: VmLine,
        arguments: List<String>,
        stdin: String?,
        runId: String
    ): GuestOutput {
        val tart = Tart(context)
        val script = "export PATH=${shellQuote(Tart.GUEST_JDK_BIN)}:\$PATH; cd ~/posato-run && " +
            "tools/posato-control/build/install/posato-control/bin/posato-control " + arguments.joinToString(" ") { shellQuote(it) }
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
        // A summary or a failure keeps the step results only in this report, so its evidence paths move with the copy too.
        val report = hostRun.resolve(STEP_RESULTS)
        if (Files.isRegularFile(report)) Files.writeString(report, relocateGuestPaths(Files.readString(report), runId))
        val envelope = relocateGuestPaths(output.stdout, runId)
        // The envelope is the evidence of commands such as observe, which write nothing else into their run directory.
        if (envelope.isNotBlank()) Files.writeString(hostRun.resolve("envelope.json"), envelope)
        return GuestOutput(output.exitCode, envelope, output.stderr)
    }

    private const val RELAY_TIMEOUT_MINUTES = 30L
    private const val STEP_RESULTS = "run-result.json"
}

/** Commands that run a single-step scenario, which launches Posato when no tracked instance is running. */
private val ELEMENT_COMMANDS = setOf("tap", "type", "press", "wait", "update-consent")

/** Commands that skip the scenario for a selected process without an element query. */
private val PROCESS_ONLY_COMMANDS = setOf("type", "wait")

/** The options that make `QueryOptions.toQuery()` nonempty; the role options alone only qualify an anchor. */
private val QUERY_OPTIONS = listOf("--id", "--text", "--text-contains", "--role", "--index", "--path", "--within-text", "--near-text")

/**
 * Whether a command may start the development package in the guest: a launch, a scenario unless its own
 * `launch.skip` is set, the flows, and the element commands. Typing into or waiting on another process without an
 * element query never runs a scenario, so it leaves Posato alone.
 */
internal fun startsPackage(
    forwarded: List<String>,
    scenario: String?
): Boolean = when (val command = forwarded.firstOrNull()) {
    "launch" -> "--adopt" !in forwarded
    "flow" -> true
    "run" -> scenario == null || !skipsLaunch(scenario)
    in ELEMENT_COMMANDS -> !(command in PROCESS_ONLY_COMMANDS && forwarded.selectsProcess() && QUERY_OPTIONS.none { forwarded.hasOption(it) })
    else -> false
}

private fun List<String>.hasOption(name: String): Boolean = any { it == name || it.startsWith("$name=") }

/** Mirrors `ProcessOptions.selector()`, which ignores a blank value. */
private fun List<String>.selectsProcess(): Boolean = withIndex().any { (index, word) ->
    val value = if (word == "--process") getOrNull(index + 1) else word.removePrefix("--process=").takeIf { it != word }
    !value.isNullOrBlank()
}

/** An unreadable scenario counts as launching; the guest then reports why it is invalid. */
private fun skipsLaunch(scenario: String): Boolean = try {
    ControlJson.lenient.decodeFromString(Scenario.serializer(), scenario).launch.skip
} catch (_: SerializationException) {
    false
}

/**
 * The run id a relayed command names, as `--run-id <id>` or `--run-id=<id>`, or null when the relay chooses one. A
 * second `--run-id` is refused: the guest would keep only one of them and the evidence would land under the other.
 */
internal fun relayRunId(args: List<String>): String? {
    val ids = args.withIndex().mapNotNull { (index, word) ->
        when {
            word == RUN_ID_OPTION -> args.getOrNull(index + 1)
                ?: throw ControlException(ErrorCode.USAGE, "$RUN_ID_OPTION needs a value.")

            word.startsWith("$RUN_ID_OPTION=") -> word.removePrefix("$RUN_ID_OPTION=")

            else -> null
        }
    }
    if (ids.size > 1) {
        throw ControlException(ErrorCode.USAGE, "$RUN_ID_OPTION is given ${ids.size} times: ${ids.joinToString()}.", "Pass one run id.")
    }
    return ids.singleOrNull()
}

private const val RUN_ID_OPTION = "--run-id"
