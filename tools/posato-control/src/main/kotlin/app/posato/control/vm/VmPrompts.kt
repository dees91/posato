package app.posato.control.vm

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.readKeychainSecret
import java.awt.Color
import java.awt.image.BufferedImage
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
    ICLOUD_LATER("icloud-later"),
    AUTOMATION_ALLOW("automation-allow"),
    AUTOMATION_DENY("automation-deny"),
    KEEP_SAFARI("keep-safari"),
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
            GuestPrompt.KEEP_SAFARI -> {
                keepSafari(screen, timeoutMs)
            }

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

            GuestPrompt.AUTOMATION_ALLOW, GuestPrompt.AUTOMATION_DENY -> {
                val question = "wants access to control"
                screen.waitFor(question, timeoutMs)
                screen.waitFor("Posato replaces a blocked Safari or", timeoutMs)
                val label = if (prompt == GuestPrompt.AUTOMATION_ALLOW) "Allow" else "Don't Allow"
                val button = screen.waitFor(label, timeoutMs, exact = true).first()
                screen.session { client -> client.click(button.centerX, button.centerY) }
                screen.waitGone(question, timeoutMs)
            }

            GuestPrompt.ICLOUD_LATER -> {
                dismissConnectAlert(screen, timeoutMs)
            }

            GuestPrompt.PICKER_BYPASS -> {
                allowPickerBypass(screen, timeoutMs)
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
        val (source, target) = if (fromIndex == 0 && toIndex == 0) {
            screen.waitForDrag(from, to, timeoutMs)
        } else {
            val source = screen.waitFor(from, timeoutMs, exact = true).sortedBy { it.y }.getOrNull(fromIndex) ?: throw notOnScreen(from)
            val target = screen.waitFor(to, timeoutMs, exact = true).sortedBy { it.y }.getOrNull(toIndex) ?: throw notOnScreen(to)
            source to target
        }
        screen.session { client -> client.drag(source.centerX, source.centerY, target.centerX, target.centerY) }
    }

    /** Types [text] into the focused guest field as hardware key events. */
    fun type(
        line: VmLine,
        text: String
    ) = guestScreen(context, line).session { client -> client.type(text) }

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

    /**
     * Switches on the toggle of a System Settings list row (Login Items, Privacy panes) and authenticates, leaving a
     * switch that is already on alone. A click flips the switch, so onboarding, which answers this on every pass while
     * the row shows, switched the helper off again after approving it: setup then asked to try again and finished
     * with "Background approval needed" in every run (`observed` 2026-10-08).
     */
    private fun toggleRow(
        screen: GuestScreen,
        rowText: String,
        timeoutMs: Long
    ) {
        // Text recognition can merge a row's icon into its label ("exee tart-guest-agent"), so match a fragment.
        val row = screen.waitFor(rowText, timeoutMs).firstOrNull { it.centerX < screen.width * SETTINGS_CONTENT_RIGHT }
            ?: throw notOnScreen(rowText)
        val alreadyOn = screen.session { client ->
            val x = (client.width * LOGIN_ITEM_TOGGLE_X).toInt()
            switchIsOn(client.capture(), x, row.centerY).also { on -> if (!on) client.click(x, row.centerY) }
        }
        if (alreadyOn) {
            context.log("The switch for $rowText is already on; leaving it.")
            return
        }
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
        Thread.sleep(SHEET_SETTLE_MS)
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
        Thread.sleep(SHEET_SETTLE_MS)
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

        /** Fractions of the framebuffer width for the golden VM's System Settings window (Login Items toggle column). */
        const val LOGIN_ITEM_TOGGLE_X = 0.716
        const val SETTINGS_CONTENT_RIGHT = 0.76
        const val ACTIVATION_MS = 1_000L

        /**
         * A sheet's title is recognized while it still slides in, before its field takes focus; typing then went
         * nowhere and Return never submitted the Mac password (`observed` 2026-09-25).
         */
        const val SHEET_SETTLE_MS = 1_500L
    }
}

private const val FRAME = "frame.png"

/** Half the width of the strip read across a switch, as a fraction of the framebuffer width. */
private const val SWITCH_REACH = 0.01

/** How much brighter the knob's side reads than the track's, out of 255. */
private const val KNOB_CONTRAST = 16

/**
 * Whether the switch around ([x], [y]) is on, read from where its white knob sits: right when on, left when off. The
 * knob holds its place whatever the track's color, which is gray for an enabled switch in an inactive window and
 * follows the accent color otherwise. A read that shows neither side brighter counts as off, so the switch is clicked
 * as before.
 */
private fun switchIsOn(
    frame: BufferedImage,
    x: Int,
    y: Int
): Boolean {
    val reach = (frame.width * SWITCH_REACH).toInt()
    if (y !in 0 until frame.height || x - reach < 0 || x + reach >= frame.width) return false

    fun brightness(columns: IntRange): Int {
        val values = columns.map { column -> Color(frame.getRGB(column, y)).let { (it.red + it.green + it.blue) / 3 } }
        // The median ignores the pointer, which can cross the strip after a click.
        return values.sorted()[values.size / 2]
    }
    val left = brightness(x - reach until x - reach / 3)
    val right = brightness(x + reach / 3..x + reach)
    return right - left > KNOB_CONTRAST
}

/** macOS 26 asks whether the guest agent may bypass the private window picker after screen captures. */
internal const val PICKER_BYPASS_QUESTION = "bypass the system private"

/** The alert Resume Data Sync can raise over its account password sheet; macOS renders the apostrophe either way. */
internal const val ICLOUD_CONNECT_ALERT = "connect to iCloud"

/** The running clone's screen; frames for text recognition go to a scratch file under the line's directory. */
internal fun guestScreen(
    context: RunContext,
    line: VmLine
): GuestScreen {
    VmLifecycle(context).requireRunning(line)
    return GuestScreen(context, vmEndpoint(line, 0), vmDirectory(line).resolve(FRAME))
}

internal fun notOnScreen(text: String) = ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The guest screen shows no '$text'.")

private fun allowPickerBypass(
    screen: GuestScreen,
    timeoutMs: Long
) {
    screen.waitFor(PICKER_BYPASS_QUESTION, timeoutMs)
    val allow = screen.waitFor(BYPASS_ALLOW, timeoutMs, exact = true).first()
    screen.session { client -> client.click(allow.centerX, allow.centerY) }
    screen.waitGone(PICKER_BYPASS_QUESTION, timeoutMs)
}

private const val BYPASS_ALLOW = "Allow"

/**
 * Resume Data Sync can raise "This Mac can't connect to iCloud" over the account password sheet it opens; Later
 * reveals the sheet (`observed` 2026-10-04). A first click may only activate the alert, so it is pressed until gone.
 */
private fun dismissConnectAlert(
    screen: GuestScreen,
    timeoutMs: Long
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    screen.waitFor(ICLOUD_CONNECT_ALERT, timeoutMs)
    while (true) {
        val lines = screen.read()
        if (lines.none { it.matches(ICLOUD_CONNECT_ALERT, exact = false) }) return
        val later = lines.firstOrNull { it.matches(CONNECT_LATER, exact = true) }
        if (later != null) screen.session { client -> client.click(later.centerX, later.centerY) }
        if (System.currentTimeMillis() >= deadline) {
            throw ControlException(ErrorCode.WAIT_TIMEOUT, "The alert '$ICLOUD_CONNECT_ALERT' stayed on the guest screen.")
        }
        Thread.sleep(CONNECT_ALERT_POLL_MS)
    }
}

private const val CONNECT_LATER = "Later"
private const val CONNECT_ALERT_POLL_MS = 1_000L

private fun keepSafari(
    screen: GuestScreen,
    timeoutMs: Long
) {
    screen.waitFor("Do you want to change your", timeoutMs)
    screen.waitFor("default web browser", timeoutMs)
    val button = screen.waitFor("Keep \"Safari\"", timeoutMs, exact = true).first()
    screen.session { client -> client.click(button.centerX, button.centerY) }
    screen.waitGone("Do you want to change your", timeoutMs)
}
