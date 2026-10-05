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

/**
 * The next dialog [GuestICloud.resume] answers, or null while the guest is still working or done. A picker-bypass
 * request covers the screen and the connect alert covers the account password sheet, so each is answered before
 * anything under it.
 */
internal fun pendingICloudPrompt(lines: List<RecognizedLine>): GuestPrompt? = when {
    lines.any { it.shows(PICKER_BYPASS_QUESTION) } -> GuestPrompt.PICKER_BYPASS
    lines.any { it.shows(ICLOUD_CONNECT_ALERT) } -> GuestPrompt.ICLOUD_LATER
    lines.any { it.shows(ACCOUNT_REQUEST) } -> GuestPrompt.ACCOUNT_PASSWORD
    lines.any { it.shows(MAC_PASSWORD_REQUEST) } -> GuestPrompt.MAC_PASSWORD
    lines.any { it.shows(DEVICE_PASSCODE_REQUEST) } -> GuestPrompt.DEVICE_PASSCODE
    else -> null
}

/**
 * The refusal for a keychain that cannot deliver the workspace key, or null when it may: a paused keychain or a
 * signed-out guest. An unknown state is let through, since the reading itself can fail.
 */
internal fun keychainRefusal(
    line: VmLine,
    state: ICloudKeychainState
): ControlException? = when (state) {
    ICloudKeychainState.SIGNED_OUT -> ControlException(
        ErrorCode.ICLOUD_KEYCHAIN_PAUSED,
        "${line.cloneName} is not signed in to an Apple Account, so iCloud Keychain cannot sync.",
        "Sign the golden VM in to the test account as described in docs/development/unattended-verification.md.",
    )

    ICloudKeychainState.PAUSED -> ControlException(
        ErrorCode.ICLOUD_KEYCHAIN_PAUSED,
        "iCloud Keychain is paused in ${line.cloneName} (\"Some iCloud Data Isn't Syncing\"); workspace keys will not arrive.",
        "Run `posato-control vm icloud --line ${line.id} --resume`, then repair the golden VM the same way.",
    )

    ICloudKeychainState.SYNCING, ICloudKeychainState.UNKNOWN -> null
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
        return inSettings(line) {
            readState(line, openSettings(line, timeoutMs), timeoutMs)
        }
    }

    /** Resumes a paused keychain, answering the account, Mac password, and passcode dialogs; returns the final state. */
    fun resume(
        line: VmLine,
        timeoutMs: Long
    ): ICloudKeychainState {
        inSettings(line) {
            val screen = openSettings(line, timeoutMs)
            if (readState(line, screen, timeoutMs) == ICloudKeychainState.PAUSED) resumeDataSync(line, screen, timeoutMs)
        }
        return check(line, timeoutMs)
    }

    private fun resumeDataSync(
        line: VmLine,
        screen: GuestScreen,
        timeoutMs: Long
    ) {
        val prompts = VmPrompts(context)
        click(line, screen, NOTICE_ROW, exact = false, timeoutMs = timeoutMs)
        click(line, screen, RESUME_BUTTON, exact = true, timeoutMs = timeoutMs)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val lines = screen.read()
            val prompt = pendingICloudPrompt(lines)
            when {
                prompt != null -> prompts.answer(line, prompt, timeoutMs)

                lines.none { it.shows(PAUSED_NOTICE) } -> return

                System.currentTimeMillis() >= deadline -> throw ControlException(
                    ErrorCode.WAIT_TIMEOUT,
                    "iCloud Keychain in ${line.cloneName} still reports '$PAUSED_NOTICE'.",
                    "Inspect the guest with `vm screenshot`; Apple may ask for the trusted phone number (`vm type --secret phone`).",
                )
            }
            Thread.sleep(POLL_MS)
        }
    }

    /**
     * Runs [block] with System Settings and quits it afterwards, also when a step times out: a window left in front
     * covers the application that the next command drives.
     */
    private fun <T> inSettings(
        line: VmLine,
        block: () -> T
    ): T {
        try {
            return block()
        } finally {
            quitSettings(line)
        }
    }

    /**
     * Reads until two reads a poll apart agree on a known state. The sidebar draws the account row before the
     * notices below it, so a single read right after the window appears could miss a paused keychain; any read
     * that shows the notice wins.
     */
    private fun readState(
        line: VmLine,
        screen: GuestScreen,
        timeoutMs: Long
    ): ICloudKeychainState {
        val deadline = System.currentTimeMillis() + timeoutMs
        var previous: ICloudKeychainState? = null
        while (true) {
            val lines = screen.read()
            if (pendingICloudPrompt(lines) == GuestPrompt.PICKER_BYPASS) {
                VmPrompts(context).answer(line, GuestPrompt.PICKER_BYPASS, timeoutMs)
                continue
            }
            val state = iCloudKeychainState(lines)
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
        await(line, screen, ACCOUNT_ROW, exact = false, timeoutMs = timeoutMs)
        return screen
    }

    private fun click(
        line: VmLine,
        screen: GuestScreen,
        text: String,
        exact: Boolean,
        timeoutMs: Long
    ) {
        val match = await(line, screen, text, exact, timeoutMs).first()
        screen.session { client -> client.click(match.centerX, match.centerY) }
    }

    /**
     * Waits for [text] as [GuestScreen.waitFor] does, answering a picker-bypass request first whenever one covers the
     * screen: macOS 26 raises it after screen captures, and the text under it never appears until it is gone.
     */
    private fun await(
        line: VmLine,
        screen: GuestScreen,
        text: String,
        exact: Boolean,
        timeoutMs: Long
    ): List<RecognizedLine> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val lines = screen.read()
            val matches = lines.filter { it.matches(text, exact) }
            when {
                pendingICloudPrompt(lines) == GuestPrompt.PICKER_BYPASS -> VmPrompts(context).answer(line, GuestPrompt.PICKER_BYPASS, timeoutMs)

                matches.isNotEmpty() -> return matches

                System.currentTimeMillis() >= deadline -> throw ControlException(
                    ErrorCode.WAIT_TIMEOUT,
                    "No text '$text' appeared on the guest screen within ${timeoutMs / MILLIS_PER_SECOND} s.",
                )

                else -> Thread.sleep(POLL_MS)
            }
        }
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
        const val MILLIS_PER_SECOND = 1_000L
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
