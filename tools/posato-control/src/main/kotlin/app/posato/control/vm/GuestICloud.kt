package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext

/** Whether a guest's iCloud Keychain syncs, as System Settings reports it. */
enum class ICloudKeychainState(
    val id: String
) {
    SYNCING("syncing"),
    PAUSED("paused"),
    SIGNED_OUT("signed-out"),
    UNKNOWN("unknown"),
}

/**
 * Reads the iCloud Keychain state from System Settings' sidebar. A paused keychain shows the notice
 * "Some iCloud Data Isn't Syncing"; a signed-in guest otherwise shows its Apple Account row.
 */
internal fun iCloudKeychainState(lines: List<RecognizedLine>): ICloudKeychainState = when {
    lines.any { it.shows(PAUSED_NOTICE) } -> ICloudKeychainState.PAUSED
    lines.any { it.shows(SIGN_IN_ROW) } -> ICloudKeychainState.SIGNED_OUT
    lines.any { it.shows(ACCOUNT_ROW) } -> ICloudKeychainState.SYNCING
    else -> ICloudKeychainState.UNKNOWN
}

/** The next dialog [GuestICloud.resume] answers, or null while the guest is still working or done. */
internal fun pendingICloudPrompt(lines: List<RecognizedLine>): GuestPrompt? = when {
    lines.any { it.shows(ACCOUNT_REQUEST) } -> GuestPrompt.ACCOUNT_PASSWORD
    lines.any { it.shows(MAC_PASSWORD_REQUEST) } -> GuestPrompt.MAC_PASSWORD
    lines.any { it.shows(DEVICE_PASSCODE_REQUEST) } -> GuestPrompt.DEVICE_PASSCODE
    else -> null
}

/**
 * A new golden VM or device signing in to the test account can pause iCloud Keychain on the other guests. A paused
 * keychain never delivers the Posato workspace key, which surfaces much later as "Waiting for the workspace key"
 * (`observed` 2026-09-25, `RELEASE-003`). This checks the state before a test and repairs it through Resume Data Sync.
 */
class GuestICloud(
    private val context: RunContext
) {
    private val tart = Tart(context)

    fun check(
        line: VmLine,
        timeoutMs: Long
    ): ICloudKeychainState {
        val screen = openSettings(line, timeoutMs)
        val state = readState(screen, timeoutMs)
        quitSettings(line)
        return state
    }

    /** Resumes a paused keychain, answering the account, Mac password, and passcode dialogs; returns the final state. */
    fun resume(
        line: VmLine,
        timeoutMs: Long
    ): ICloudKeychainState {
        val screen = openSettings(line, timeoutMs)
        if (readState(screen, timeoutMs) != ICloudKeychainState.PAUSED) {
            quitSettings(line)
            return check(line, timeoutMs)
        }
        val prompts = VmPrompts(context)
        prompts.click(line, NOTICE_ROW, exact = false, index = 0, timeoutMs = timeoutMs)
        prompts.click(line, RESUME_BUTTON, exact = true, index = 0, timeoutMs = timeoutMs)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val lines = screen.read()
            val prompt = pendingICloudPrompt(lines)
            when {
                prompt != null -> prompts.answer(line, prompt, timeoutMs)

                lines.none { it.shows(PAUSED_NOTICE) } -> break

                System.currentTimeMillis() >= deadline -> throw ControlException(
                    ErrorCode.WAIT_TIMEOUT,
                    "iCloud Keychain in ${line.cloneName} still reports '$PAUSED_NOTICE'.",
                    "Inspect the guest with `vm screenshot`; Apple may ask for the trusted phone number (`vm type --secret phone`).",
                )
            }
            Thread.sleep(POLL_MS)
        }
        quitSettings(line)
        return check(line, timeoutMs)
    }

    /**
     * Reads until two reads a poll apart agree on a known state. The sidebar draws the account row before the
     * notices below it, so a single read right after the window appears could miss a paused keychain; any read
     * that shows the notice wins.
     */
    private fun readState(
        screen: GuestScreen,
        timeoutMs: Long
    ): ICloudKeychainState {
        val deadline = System.currentTimeMillis() + timeoutMs
        var previous: ICloudKeychainState? = null
        while (true) {
            val state = iCloudKeychainState(screen.read())
            val settled = state == previous && state != ICloudKeychainState.UNKNOWN
            if (state == ICloudKeychainState.PAUSED || settled || System.currentTimeMillis() >= deadline) return state
            previous = state
            Thread.sleep(POLL_MS)
        }
    }

    private fun openSettings(
        line: VmLine,
        timeoutMs: Long
    ): GuestScreen {
        tart.exec(line.cloneName, "/usr/bin/open -b $SETTINGS_BUNDLE")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Opening System Settings in ${line.cloneName}")
        val screen = guestScreen(context, line)
        screen.waitFor(ACCOUNT_ROW, timeoutMs)
        return screen
    }

    /** Quits System Settings and waits for it to exit, so that a following check opens a fresh window. */
    private fun quitSettings(line: VmLine) {
        tart.exec(line.cloneName, "/usr/bin/osascript -e 'quit app id \"$SETTINGS_BUNDLE\"'")
        val deadline = System.currentTimeMillis() + QUIT_TIMEOUT_MS
        while (tart.exec(line.cloneName, "/usr/bin/pgrep -x 'System Settings'").exitCode == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val SETTINGS_BUNDLE = "com.apple.systempreferences"
        const val RESUME_BUTTON = "Resume Data Sync"
        const val POLL_MS = 2_000L
        const val QUIT_TIMEOUT_MS = 20_000L
    }
}

/** Contains [text], ignoring case and treating the typographic apostrophe macOS renders as a plain one. */
private fun RecognizedLine.shows(text: String): Boolean = this.text.replace('\u2019', '\'').contains(text, ignoreCase = true)

private const val PAUSED_NOTICE = "Isn't Syncing"

/** The notice's first sidebar line, free of the apostrophe text recognition may render either way. */
private const val NOTICE_ROW = "Some iCloud Data"
private const val ACCOUNT_ROW = "Apple Account"

/** A signed-out sidebar reads "Sign in with your Apple Account", which also contains [ACCOUNT_ROW]. */
private const val SIGN_IN_ROW = "Sign in"
private const val ACCOUNT_REQUEST = "Enter the Apple Account password"
private const val MAC_PASSWORD_REQUEST = "Enter Mac Password"
private const val DEVICE_PASSCODE_REQUEST = "passcode you use to unlock"
