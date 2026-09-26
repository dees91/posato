package app.posato.control.vm

import app.posato.control.core.RunContext

/** Scrolls the view under the recognized [text] with the mouse wheel, which reaches Compose scroll areas. */
class GuestScroll(
    private val context: RunContext
) {
    fun scroll(
        line: VmLine,
        text: String,
        clicks: Int,
        timeoutMs: Long
    ) {
        val screen = guestScreen(context, line)
        val match = screen.waitFor(text, timeoutMs, exact = false).firstOrNull() ?: throw notOnScreen(text)
        screen.session { client -> client.scroll(match.centerX, match.centerY, clicks) }
    }
}

/** Answers the macOS notification permission banner: its Options menu, which holds Allow, appears only under the pointer. */
class GuestNotificationPrompt(
    private val context: RunContext
) {
    fun allow(
        line: VmLine,
        timeoutMs: Long
    ) {
        val screen = guestScreen(context, line)
        val banner = screen.waitFor(BANNER_TEXT, timeoutMs).first()
        screen.session { client -> client.pointer(banner.centerX, banner.centerY, 0) }
        val options = screen.waitFor(OPTIONS_TEXT, HOVER_TIMEOUT_MS).minBy { kotlin.math.abs(it.centerY - banner.centerY) }
        screen.session { client -> client.click(options.centerX, options.centerY) }
        val allow = screen.waitFor(ALLOW_TEXT, HOVER_TIMEOUT_MS, exact = true).first()
        screen.session { client -> client.click(allow.centerX, allow.centerY) }
        screen.waitGone(BANNER_TEXT, timeoutMs)
    }

    private companion object {
        const val BANNER_TEXT = "Notifications may include"
        const val OPTIONS_TEXT = "Options"
        const val ALLOW_TEXT = "Allow"
        const val HOVER_TIMEOUT_MS = 10_000L
    }
}
