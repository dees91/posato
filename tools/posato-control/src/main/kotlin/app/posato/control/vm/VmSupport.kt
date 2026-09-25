package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TartVm(
    val name: String,
    val running: Boolean,
)

data class VncEndpoint(
    val host: String,
    val port: Int,
    val password: String,
)

/** Reads `tart list --format json`. */
internal fun parseTartList(json: String): List<TartVm> = ControlJson.lenient.parseToJsonElement(json).jsonArray.map { element ->
    val vm = element.jsonObject
    TartVm(vm.getValue("Name").jsonPrimitive.content, vm["Running"]?.jsonPrimitive?.boolean ?: false)
}

/**
 * Refuses a clone that would break the environment. A golden VM and its own clone share a provisioning identifier,
 * and when both run macOS permanently gives one of them a new identifier, which the registered development
 * profile no longer covers. Apple's Virtualization framework also runs at most two macOS guests at once.
 */
internal fun refuseClone(
    vms: List<TartVm>,
    golden: String,
    clone: String
) {
    val source = vms.firstOrNull { it.name == golden }
    val reason = when {
        source == null -> "The golden VM '$golden' does not exist."
        source.running -> "The golden VM '$golden' is running; a clone beside it would change one provisioning identity."
        vms.any { it.name == clone } -> "A VM named '$clone' already exists."
        vms.count { it.running } >= MAX_RUNNING_GUESTS -> "Two macOS guests already run, the most Virtualization allows."
        else -> null
    }
    reason?.let { throw ControlException(ErrorCode.VM_UNAVAILABLE, it, "Stop or destroy the listed VM first (`posato-control vm destroy`).") }
}

private val VNC_URL = Regex("""vnc://:([^@\s]+)@([^:\s]+):(\d+)""")

/** The endpoint `tart run --no-graphics --vnc-experimental` prints once the server listens. */
internal fun parseVncEndpoint(output: String): VncEndpoint? = VNC_URL.find(output)?.let { match ->
    val (password, host, port) = match.destructured
    VncEndpoint(host, port.toInt(), password)
}

/** Single-quotes a word for `/bin/sh`. */
internal fun shellQuote(word: String): String = "'" + word.replace("'", "'\\''") + "'"

/** The scenario source that means the host's own standard input. */
internal const val STANDARD_INPUT = "-"

/**
 * The guest cannot read host files, so a scenario file argument becomes `-` and its text travels on standard input.
 * Returns the rewritten arguments and the scenario to send: a host path, [STANDARD_INPUT] when the scenario already
 * arrives on the host's standard input, or null.
 */
internal fun scenarioOverStdin(args: List<String>): Pair<List<String>, String?> {
    val index = args.indexOf("--scenario")
    val source = args.getOrNull(index + 1)?.takeIf { index >= 0 } ?: return args to null
    if (source == STANDARD_INPUT) return args to STANDARD_INPUT
    return args.toMutableList().also { it[index + 1] = STANDARD_INPUT } to source
}

/** Evidence the guest writes under its run directory is copied to `<run>/guest/` on the host. */
internal fun relocateGuestPaths(
    envelope: String,
    runId: String
): String = envelope.replace("build/verification/runs/$runId/", "build/verification/runs/$runId/guest/")

private const val MAX_RUNNING_GUESTS = 2
