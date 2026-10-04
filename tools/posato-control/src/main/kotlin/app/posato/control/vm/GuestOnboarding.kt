package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.model.Scenario
import app.posato.control.model.Step
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.time.Duration

/**
 * First-run onboarding of the development package in a clone, with the helper set up: the onboarding recipe drives the
 * window in the guest while this host answers the Login Items and administrator prompts over VNC as they appear. It
 * replaces a hand-written loop that took up to half an hour to notice a finished setup behind System Settings.
 */
class GuestOnboarding(
    private val context: RunContext,
) {
    private val prompts = VmPrompts(context)
    private val dialogs = GuestDialogs(context)
    private val text = GuestText(context)

    fun run(
        line: VmLine,
        timeoutMs: Long,
    ): JsonObject {
        val recipe = recipe()
        val split = recipe.steps.indexOfFirst { it.name == SETUP_READY }
        val done = recipe.steps.indexOfFirst { it.name == SETUP_DONE }
        val final = recipe.steps.indexOfFirst { it.name == NO_FINISH_SETUP }
        if (split < 0 || done < 0 || final <= done) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "The onboarding recipe lacks $SETUP_READY, $SETUP_DONE or $NO_FINISH_SETUP.")
        }
        VmLifecycle(context).run {
            requireRunning(line)
            requireCurrentPackage(line)
        }
        guest(line, listOf("launch", "-t", "desktop"))
        guestScenario(line, recipe.copy(steps = recipe.steps.subList(0, split + 1)))
        guest(line, listOf("tap", "-t", "desktop", "--text", SET_UP, "--role", "button"))
        answerUntilReady(line, timeoutMs)
        val resumed = recipe.launch.copy(terminateExisting = false)
        guestScenario(line, recipe.copy(launch = resumed, steps = recipe.steps.subList(done + 2, final)))
        // A Login Items approval can lag the window's "ready": finish it the same way while the overview asks for it.
        if (found(line, BACKGROUND_NEEDED)) {
            guest(line, listOf("tap", "-t", "desktop", "--text", FINISH_SETUP, "--role", "button"))
            guest(line, listOf("tap", "-t", "desktop", "--text", SET_UP, "--role", "button"))
            answerUntilReady(line, timeoutMs)
            guest(line, listOf("tap", "-t", "desktop", "--text", "Back to Session", "--role", "button"))
        }
        if (found(line, BACKGROUND_NEEDED)) {
            throw ControlException(ErrorCode.ASSERTION_FAILED, "Setup finished, but This Mac still needs background approval.")
        }
        // The recipe's final readiness assertion runs only after the late approval had its chance.
        guestScenario(line, recipe.copy(launch = resumed, steps = recipe.steps.subList(final, recipe.steps.size)))
        return ControlJson.pretty.parseToJsonElement("""{"line":"${line.id}","ready":true}""").jsonObject
    }

    private fun answerUntilReady(
        line: VmLine,
        timeoutMs: Long,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (text.read(line, HELPER_ROW).isNotEmpty()) attempt { prompts.answer(line, GuestPrompt.BACKGROUND, PROMPT_TIMEOUT_MS) }
            if (dialogs.list(line).any { it.owner == SECURITY_AGENT }) {
                attempt { prompts.click(line, PASSWORD, exact = false, index = 0, timeoutMs = PROMPT_TIMEOUT_MS) }
                attempt { prompts.answer(line, GuestPrompt.ADMIN, PROMPT_TIMEOUT_MS) }
            }
            // The window is read through accessibility, so a System Settings window in front of it cannot hide it.
            if (found(line, READY)) return
            if (found(line, TRY_AGAIN)) guest(line, listOf("tap", "-t", "desktop", "--text", TRY_AGAIN, "--role", "button"))
            Thread.sleep(POLL_MILLIS)
        }
        throw ControlException(ErrorCode.WAIT_TIMEOUT, "Setup did not report \"$READY\" within ${timeoutMs / MILLIS_PER_SECOND} s.")
    }

    /** A prompt that is already gone, or answered in the meantime, is not a failure of the loop. */
    private fun attempt(action: () -> Unit): Boolean {
        return try {
            action()
            true
        } catch (exception: ControlException) {
            // A missing Keychain secret or a broken VNC session is not a prompt that went away.
            if (exception.code != ErrorCode.ELEMENT_NOT_FOUND && exception.code != ErrorCode.WAIT_TIMEOUT) throw exception
            false
        }
    }

    private fun found(
        line: VmLine,
        label: String,
    ): Boolean {
        val envelope = guest(line, listOf("find", "-t", "desktop", "--text-contains", label), failOnError = false)
        return envelope["ok"]?.jsonPrimitive?.boolean == true && envelope["result"].toString().contains("\"path\"")
    }

    private fun recipe(): Scenario {
        val path = context.layout.toolDirectory.resolve("fixtures").resolve("scenarios").resolve(RECIPE)
        return ControlJson.lenient.decodeFromString(Scenario.serializer(), Files.readString(path))
    }

    private fun guestScenario(
        line: VmLine,
        scenario: Scenario,
    ) {
        guest(line, listOf("run", "-t", "desktop", "--scenario", "-"), ControlJson.pretty.encodeToString(Scenario.serializer(), scenario))
    }

    /** Runs a guest posato-control command, as `--vm` does, and returns its envelope. */
    private fun guest(
        line: VmLine,
        args: List<String>,
        stdin: String? = null,
        failOnError: Boolean = true,
    ): JsonObject {
        val script = "export PATH=${shellQuote(Tart.GUEST_JDK_BIN)}:\$PATH; cd ~/posato-run && " +
            "tools/posato-control/build/install/posato-control/bin/posato-control " + args.joinToString(" ") { shellQuote(it) }
        val output = Tart(context).exec(line.cloneName, script, stdin = stdin, timeout = Duration.ofMinutes(GUEST_TIMEOUT_MINUTES))
        val envelope = runCatching { ControlJson.lenient.parseToJsonElement(output.stdout.substringBefore("\nUsage:")).jsonObject }
            .getOrElse { throw ControlException(ErrorCode.COMMAND_FAILED, "The guest's ${args.first()} printed no envelope.") }
        if (failOnError && envelope["ok"]?.jsonPrimitive?.boolean != true) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "The guest's ${args.first()} failed: ${envelope["error"]}")
        }
        return envelope
    }

    private companion object {
        const val RECIPE = "mac-unified-onboarding-desktop.json"
        const val SETUP_READY = "setup-ready"
        const val SETUP_DONE = "setup-done"
        const val NO_FINISH_SETUP = "no-finish-setup"
        const val SET_UP = "Set up Posato"
        const val READY = "This Mac is ready."
        const val TRY_AGAIN = "Try again"
        const val FINISH_SETUP = "Finish setup"
        const val BACKGROUND_NEEDED = "Background approval needed"
        const val HELPER_ROW = "PosatoMacOSHelper"
        const val SECURITY_AGENT = "SecurityAgent"
        const val PASSWORD = "Password"
        const val PROMPT_TIMEOUT_MS = 20_000L
        const val POLL_MILLIS = 8_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val GUEST_TIMEOUT_MINUTES = 10L
    }
}
