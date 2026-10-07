package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.model.Envelope
import app.posato.control.model.humanLines
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.IOException

/** How long `flow icloud` waits for its outcome unless `--timeout-seconds` says otherwise. */
const val ICLOUD_FLOW_TIMEOUT_SECONDS = 300L

private const val TIMEOUT_OPTION = "--timeout-seconds"

/** The global option for plain text, which the host renders itself for a relayed command it reads the envelope of. */
internal const val HUMAN_OPTION = "--human"
private const val LINK_ACTION = "link"
private const val REMOVE_ACTION = "remove"

/** What one relayed guest command printed and returned. */
internal data class GuestOutput(
    val exitCode: Int,
    val envelope: String,
    val stderr: String,
)

/**
 * Whether forwarded arguments run `flow icloud link`, the one action that waits for a key from iCloud Keychain. A
 * removal never does, and gating it would leave a clone whose keychain cannot be resumed unable to remove its workspace.
 */
internal fun checksICloudKeychain(arguments: List<String>): Boolean {
    if (arguments.take(2) != listOf("flow", "icloud")) return false
    return arguments.drop(2).firstOrNull { it == LINK_ACTION || it == REMOVE_ACTION } == LINK_ACTION
}

/**
 * Whether forwarded arguments run `flow icloud remove`. A clone's keychain pauses a few minutes after it boots, and a
 * removal against a paused keychain waits out its whole timeout, so the host resumes the keychain first without
 * refusing when that fails.
 */
internal fun resumesICloudKeychain(arguments: List<String>): Boolean {
    if (arguments.take(2) != listOf("flow", "icloud")) return false
    return arguments.drop(2).firstOrNull { it == LINK_ACTION || it == REMOVE_ACTION } == REMOVE_ACTION
}

/** The whole wait `flow icloud` was asked for, or null when its value is not a number and the guest should say so. */
internal fun flowTimeoutSeconds(arguments: List<String>): Long? {
    val index = arguments.indexOfFirst { it == TIMEOUT_OPTION || it.startsWith("$TIMEOUT_OPTION=") }
    if (index < 0) return ICLOUD_FLOW_TIMEOUT_SECONDS
    val word = arguments[index]
    val value = if (word == TIMEOUT_OPTION) arguments.getOrNull(index + 1) else word.substringAfter("=")
    return value?.toLongOrNull()
}

/**
 * The guest's arguments for one [seconds] slice: every `--timeout-seconds` replaced by the slice, and without
 * `--human`, since the host tells a timed-out slice from the guest's JSON envelope.
 */
internal fun withFlowTimeout(
    arguments: List<String>,
    seconds: Long
): List<String> {
    val kept = mutableListOf<String>()
    var skipValue = false
    withoutHuman(arguments).forEach { word ->
        when {
            skipValue -> skipValue = false
            word == TIMEOUT_OPTION -> skipValue = true
            word.startsWith("$TIMEOUT_OPTION=") -> Unit
            else -> kept.add(word)
        }
    }
    return kept + listOf(TIMEOUT_OPTION, seconds.toString())
}

private fun withoutHuman(arguments: List<String>): List<String> {
    return arguments.filterNot { it == HUMAN_OPTION }
}

private fun parseEnvelope(text: String): Envelope? = try {
    ControlJson.lenient.decodeFromString(Envelope.serializer(), text)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** Whether the guest's flow ran out of time, the one outcome after which the keychain is checked again. */
internal fun guestTimedOut(envelope: String): Boolean = parseEnvelope(envelope)?.error?.code == ErrorCode.WAIT_TIMEOUT.name

private const val PRESSES = "presses"

/** The presses a slice reported, also one that timed out; zero when its output is not an envelope with them. */
internal fun slicePresses(envelope: String): Int {
    val result = parseEnvelope(envelope)?.result as? JsonObject
    return (result?.get(PRESSES) as? JsonPrimitive)?.intOrNull ?: 0
}

/** The final slice's envelope with the presses of the slices before it added, so the result counts every press. */
internal fun withEarlierPresses(
    envelope: String,
    earlier: Int
): String {
    if (earlier == 0) return envelope
    val parsed = parseEnvelope(envelope) ?: return envelope
    val result = parsed.result as? JsonObject ?: return envelope
    val counted = JsonObject(result + (PRESSES to JsonPrimitive(slicePresses(envelope) + earlier)))
    return ControlJson.pretty.encodeToString(Envelope.serializer(), parsed.copy(result = counted)) + "\n"
}

/**
 * `flow icloud link` waits inside the guest for a key that a paused iCloud Keychain never delivers, and a device that
 * signs in to the test account can pause a clone's keychain in the middle of a run. Only the host reads System
 * Settings over VNC, so it checks the keychain before the flow and again whenever a slice of the wait runs out,
 * resumes a paused keychain, and stops with `ICLOUD_KEYCHAIN_PAUSED` when that fails.
 */
internal class GuestICloudFlow(
    private val context: RunContext,
    private val line: VmLine,
) {
    private val iCloud = GuestICloud(context)
    private var resumed = false

    fun run(
        arguments: List<String>,
        invoke: (List<String>) -> GuestOutput
    ): GuestOutput {
        val human = HUMAN_OPTION in arguments
        var state = requireSyncing()
        val total = flowTimeoutSeconds(arguments) ?: return present(withKeychain(invoke(withoutHuman(arguments)), state), human)
        val deadline = System.currentTimeMillis() + total * MILLIS_PER_SECOND
        var earlierPresses = 0
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()) / MILLIS_PER_SECOND
            val output = invoke(withFlowTimeout(arguments, remaining.coerceIn(1, SLICE_SECONDS)))
            if (!guestTimedOut(output.envelope)) {
                val counted = output.copy(envelope = withEarlierPresses(output.envelope, earlierPresses))
                return present(withKeychain(counted, state), human)
            }
            earlierPresses += slicePresses(output.envelope)
            if (System.currentTimeMillis() >= deadline) throw timedOut(total, state, earlierPresses)
            context.log("flow icloud is still waiting after a ${SLICE_SECONDS}s slice; checking iCloud Keychain in ${line.cloneName}.")
            state = try {
                requireSyncing()
            } catch (exception: ControlException) {
                throw ControlException(
                    exception.code,
                    exception.message ?: exception.code.name,
                    exception.hint,
                    exception,
                    linkResult(earlierPresses),
                )
            }
        }
    }

    /** Resumes a paused keychain before a removal; a removal still runs when the keychain cannot be read or resumed. */
    fun resumeIfPaused() {
        try {
            if (iCloud.check(line, CHECK_TIMEOUT_MS) == ICloudKeychainState.PAUSED) iCloud.resume(line, RESUME_TIMEOUT_MS)
        } catch (exception: ControlException) {
            context.log("Resuming iCloud Keychain in ${line.cloneName} before the removal failed: ${exception.message}")
        } catch (exception: IOException) {
            context.log("Reading the screen of ${line.cloneName} before the removal failed: ${exception.message}")
        }
    }

    /** Reads the keychain, resumes it when paused, and refuses when it still cannot deliver the workspace key. */
    private fun requireSyncing(): ICloudKeychainState {
        val checked = try {
            iCloud.check(line, CHECK_TIMEOUT_MS)
        } catch (exception: ControlException) {
            context.log("Reading iCloud Keychain in ${line.cloneName} failed: ${exception.message}")
            ICloudKeychainState.UNKNOWN
        } catch (exception: IOException) {
            context.log("Reading the screen of ${line.cloneName} failed: ${exception.message}")
            ICloudKeychainState.UNKNOWN
        }
        val state = if (checked == ICloudKeychainState.PAUSED) resume() else checked
        keychainRefusal(line, state)?.let { throw it }
        return state
    }

    private fun resume(): ICloudKeychainState {
        val state = try {
            iCloud.resume(line, RESUME_TIMEOUT_MS)
        } catch (exception: ControlException) {
            throw resumeFailed(exception)
        } catch (exception: IOException) {
            throw resumeFailed(exception)
        }
        resumed = resumed || state != ICloudKeychainState.PAUSED
        return state
    }

    private fun timedOut(
        total: Long,
        state: ICloudKeychainState,
        presses: Int
    ) = ControlException(
        ErrorCode.WAIT_TIMEOUT,
        "flow icloud did not finish within $total s; iCloud Keychain in ${line.cloneName} reads ${state.id}.",
        "A workspace key can take about 20 minutes to reach a new device; rerun with a longer --timeout-seconds. " +
            "The guest's last envelope is guest/envelope.json in the run directory.",
        result = linkResult(presses),
    )

    private fun resumeFailed(exception: Exception) = ControlException(
        ErrorCode.ICLOUD_KEYCHAIN_PAUSED,
        "iCloud Keychain is paused in ${line.cloneName} and Resume Data Sync did not finish: ${exception.message}",
        "Run `posato-control vm icloud --line ${line.id} --resume` and check the guest with `vm screenshot --line ${line.id}`, " +
            "then run the flow again.",
        exception,
    )

    /** What a link that did not finish had pressed, which its error envelope reports as a finished one would. */
    private fun linkResult(presses: Int) = JsonObject(mapOf("action" to JsonPrimitive(LINK_ACTION), PRESSES to JsonPrimitive(presses)))

    /** Adds the keychain state to a successful flow's result, as `vm create` reports it. */
    private fun withKeychain(
        output: GuestOutput,
        state: ICloudKeychainState
    ): GuestOutput {
        val envelope = parseEnvelope(output.envelope) ?: return output
        val result = envelope.result as? JsonObject ?: return output
        val reported = JsonObject(result + ("iCloudKeychain" to JsonPrimitive(state.id)) + ("iCloudResumed" to JsonPrimitive(resumed)))
        return output.copy(envelope = ControlJson.pretty.encodeToString(Envelope.serializer(), envelope.copy(result = reported)) + "\n")
    }

    /** Prints the final envelope as the guest would have with `--human`, which the slices did not pass on. */
    private fun present(
        output: GuestOutput,
        human: Boolean
    ): GuestOutput {
        if (!human) return output
        val envelope = parseEnvelope(output.envelope) ?: return output
        return output.copy(envelope = envelope.humanLines().joinToString("\n", postfix = "\n"))
    }

    private companion object {
        const val SLICE_SECONDS = 120L
        const val CHECK_TIMEOUT_MS = 60_000L
        const val RESUME_TIMEOUT_MS = 90_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
