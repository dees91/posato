package app.posato.control.vm

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.readKeychainSecret
import java.nio.file.Path

/** System dialogs answered over VNC; each is located by its text, not by fixed coordinates alone. */
enum class GuestPrompt(
    val id: String
) {
    ADMIN("admin"),
    BACKGROUND("background"),
    TOGGLE("toggle"),
    ACCOUNT_PASSWORD("account-password"),
    MAC_PASSWORD("mac-password"),
    DEVICE_PASSCODE("device-passcode"),
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
        timeoutMs: Long,
        rowText: String? = null
    ) {
        val screen = guestScreen(context, line)
        when (prompt) {
            GuestPrompt.ADMIN -> {
                answerAdmin(screen, timeoutMs)
            }

            GuestPrompt.BACKGROUND -> {
                toggleRow(screen, BACKGROUND_ROW, timeoutMs)
            }

            GuestPrompt.TOGGLE -> {
                toggleRow(screen, rowText ?: throw missingRow(), timeoutMs)
            }

            GuestPrompt.ACCOUNT_PASSWORD -> {
                answerAccountPassword(screen, timeoutMs)
            }

            GuestPrompt.MAC_PASSWORD -> {
                answerAdmin(screen, timeoutMs, MAC_PASSWORD_REQUEST)
            }

            GuestPrompt.DEVICE_PASSCODE -> {
                answerSecret(screen, timeoutMs, DEVICE_PASSCODE_REQUEST) {
                    readKeychainSecret(
                        context,
                        ConfigurationKey.DEVICE_PASSCODE_KEYCHAIN_SERVICE,
                        ConfigurationKey.DEVICE_PASSCODE_KEYCHAIN_ACCOUNT,
                        "the test iPhone passcode",
                    )
                }
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
        val screen = guestScreen(context, line)
        val match = screen.waitFor(text, timeoutMs, exact).getOrNull(index) ?: throw notOnScreen(text)
        screen.session { client -> client.click(match.centerX, match.centerY) }
    }

    /**
     * Drags the [fromIndex]th recognized line equal to [from] onto the [toIndex]th equal to [to], as a person would in
     * Finder. Matches are ordered top to bottom, so a window title precedes an icon label with the same text.
     */
    fun drag(
        line: VmLine,
        from: String,
        fromIndex: Int,
        to: String,
        toIndex: Int,
        timeoutMs: Long
    ) {
        val screen = guestScreen(context, line)
        val source = screen.waitFor(from, timeoutMs, exact = true).sortedBy { it.y }.getOrNull(fromIndex) ?: throw notOnScreen(from)
        val target = screen.waitFor(to, timeoutMs, exact = true).sortedBy { it.y }.getOrNull(toIndex) ?: throw notOnScreen(to)
        screen.session { client -> client.drag(source.centerX, source.centerY, target.centerX, target.centerY) }
    }

    fun press(
        line: VmLine,
        chord: String
    ) = guestScreen(context, line).session { client -> client.press(chord) }

    fun screenshot(
        line: VmLine,
        name: String
    ): Path {
        return context.recordArtifact(guestScreen(context, line).screenshot(context.artifactPath("screenshots", "$name.png")))
    }

    /** Switches on the toggle of a System Settings list row (Login Items, Privacy panes) and authenticates. */
    private fun toggleRow(
        screen: GuestScreen,
        rowText: String,
        timeoutMs: Long
    ) {
        // Text recognition can merge a row's icon into its label ("exee tart-guest-agent"), so match a fragment.
        val row = screen.waitFor(rowText, timeoutMs).firstOrNull { it.centerX < screen.width * SETTINGS_CONTENT_RIGHT }
            ?: throw notOnScreen(rowText)
        screen.session { client -> client.click((client.width * LOGIN_ITEM_TOGGLE_X).toInt(), row.centerY) }
        answerAdmin(screen, timeoutMs)
    }

    private fun missingRow() = ControlException(ErrorCode.USAGE, "toggle needs --row.", "Name the System Settings row, such as tart-guest-agent.")

    /** The Apple Account password macOS asks for when a guest's iCloud session needs to be renewed. */
    private fun answerAccountPassword(
        screen: GuestScreen,
        timeoutMs: Long
    ) = answerSecret(screen, timeoutMs, ACCOUNT_REQUEST) {
        readKeychainSecret(
            context,
            ConfigurationKey.VM_ACCOUNT_KEYCHAIN_SERVICE,
            ConfigurationKey.VM_ACCOUNT_KEYCHAIN_ACCOUNT,
            "the test Apple Account password",
        )
    }

    /**
     * Waits for a dialog showing [request], types the secret into its focused field, and confirms. The iCloud Keychain
     * escrow dialog asks for the passcode of a trusted device, here the test iPhone.
     */
    private fun answerSecret(
        screen: GuestScreen,
        timeoutMs: Long,
        request: String,
        secret: () -> String
    ) {
        screen.waitFor(request, timeoutMs)
        screen.session { client ->
            client.type(secret())
            client.press("return")
        }
        screen.waitGone(request, timeoutMs)
    }

    /** Types the guest administrator password into a SecurityAgent or iCloud Keychain request showing [request]. */
    private fun answerAdmin(
        screen: GuestScreen,
        timeoutMs: Long,
        request: String = ADMIN_REQUEST
    ) {
        screen.waitFor(request, timeoutMs)
        screen.session { client ->
            client.type(vmAdminPassword(context))
            client.press("return")
        }
        screen.waitGone(request, timeoutMs)
    }

    private companion object {
        const val ADMIN_REQUEST = "password to allow this"
        const val ACCOUNT_REQUEST = "Enter the Apple Account password"
        const val MAC_PASSWORD_REQUEST = "Enter Mac Password"
        const val DEVICE_PASSCODE_REQUEST = "passcode you use to unlock"
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

private const val FRAME = "frame.png"

/** The running clone's screen; frames for text recognition go to a scratch file under the line's directory. */
internal fun guestScreen(
    context: RunContext,
    line: VmLine
): GuestScreen {
    VmLifecycle(context).requireRunning(line)
    return GuestScreen(context, vmEndpoint(context, line, 0), vmDirectory(context, line).resolve(FRAME))
}

internal fun notOnScreen(text: String) = ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The guest screen shows no '$text'.")
