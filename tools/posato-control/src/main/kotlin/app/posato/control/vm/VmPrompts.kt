package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import java.nio.file.Path

/** System dialogs answered over VNC; each is located by its text, not by fixed coordinates alone. */
enum class GuestPrompt(
    val id: String
) {
    ADMIN("admin"),
    BACKGROUND("background"),
    GATEKEEPER("gatekeeper"),
    PICKER_BYPASS("picker-bypass"),
    ;

    companion object {
        fun parse(value: String): GuestPrompt = entries.firstOrNull { it.id == value }
            ?: throw ControlException(ErrorCode.USAGE, "Unknown prompt '$value'.", "Use ${entries.joinToString(", ") { it.id }}.")
    }
}

/**
 * Answers macOS system dialogs in a running clone through Virtualization's VNC server, which delivers hardware
 * input that SecurityAgent, System Settings, and Gatekeeper accept. Labels assume an English (US) guest.
 */
class VmPrompts(
    private val context: RunContext
) {
    fun answer(
        line: VmLine,
        prompt: GuestPrompt,
        timeoutMs: Long
    ) {
        val screen = screen(line)
        when (prompt) {
            GuestPrompt.ADMIN -> {
                answerAdmin(screen, timeoutMs)
            }

            GuestPrompt.BACKGROUND -> {
                val row = screen.waitFor(BACKGROUND_ROW, timeoutMs, exact = true).firstOrNull { it.centerX < screen.width * SETTINGS_CONTENT_RIGHT }
                    ?: throw notOnScreen(BACKGROUND_ROW)
                screen.session { client -> client.click((client.width * LOGIN_ITEM_TOGGLE_X).toInt(), row.centerY) }
                answerAdmin(screen, timeoutMs)
            }

            GuestPrompt.GATEKEEPER -> {
                screen.waitFor(GATEKEEPER_QUESTION, timeoutMs)
                val open = screen.waitFor(GATEKEEPER_OPEN, timeoutMs, exact = true).first()
                screen.session { client ->
                    client.click(open.centerX, open.centerY)
                    Thread.sleep(ACTIVATION_MS)
                    client.click(open.centerX, open.centerY)
                }
                screen.waitGone(GATEKEEPER_QUESTION, timeoutMs)
            }

            GuestPrompt.PICKER_BYPASS -> {
                screen.waitFor(BYPASS_QUESTION, timeoutMs)
                val allow = screen.waitFor(BYPASS_ALLOW, timeoutMs, exact = true).first()
                screen.session { client -> client.click(allow.centerX, allow.centerY) }
                screen.waitGone(BYPASS_QUESTION, timeoutMs)
            }
        }
    }

    /** Clicks the [index]th recognized line containing [text], or equal to it when [exact]. */
    fun click(
        line: VmLine,
        text: String,
        exact: Boolean,
        index: Int,
        timeoutMs: Long
    ) {
        val screen = screen(line)
        val match = screen.waitFor(text, timeoutMs, exact).getOrNull(index) ?: throw notOnScreen(text)
        screen.session { client -> client.click(match.centerX, match.centerY) }
    }

    fun press(
        line: VmLine,
        chord: String
    ) = screen(line).session { client -> client.press(chord) }

    fun screenshot(
        line: VmLine,
        name: String
    ): Path {
        return context.recordArtifact(screen(line).screenshot(context.artifactPath("screenshots", "$name.png")))
    }

    private fun answerAdmin(
        screen: GuestScreen,
        timeoutMs: Long
    ) {
        screen.waitFor(ADMIN_REQUEST, timeoutMs)
        screen.session { client ->
            client.type(vmAdminPassword(context))
            client.press("return")
        }
        screen.waitGone(ADMIN_REQUEST, timeoutMs)
    }

    private fun screen(line: VmLine): GuestScreen {
        VmLifecycle(context).requireRunning(line)
        return GuestScreen(context, vmEndpoint(context, line, 0), vmDirectory(context, line).resolve(FRAME))
    }

    private fun notOnScreen(text: String) = ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The guest screen shows no '$text'.")

    private companion object {
        const val FRAME = "frame.png"
        const val ADMIN_REQUEST = "password to allow this"
        const val BACKGROUND_ROW = "PosatoMacOSHelper"
        const val GATEKEEPER_QUESTION = "Are you sure"
        const val GATEKEEPER_OPEN = "Open"
        const val BYPASS_QUESTION = "bypass the system private"
        const val BYPASS_ALLOW = "Allow"

        /** Fractions of the framebuffer width for the golden VM's System Settings window (Login Items toggle column). */
        const val LOGIN_ITEM_TOGGLE_X = 0.716
        const val SETTINGS_CONTENT_RIGHT = 0.76
        const val ACTIVATION_MS = 1_000L
    }
}
