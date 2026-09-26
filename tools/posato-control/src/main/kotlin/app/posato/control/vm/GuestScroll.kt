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
