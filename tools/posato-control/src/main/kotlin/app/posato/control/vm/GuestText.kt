package app.posato.control.vm

import app.posato.control.core.RunContext

/**
 * Reads the guest screen as recognized text, so an agent can follow system dialogs without viewing screenshots:
 * a full frame costs far more context than the few lines it needs (`RELEASE-003` retro, 2026-09-25).
 */
class GuestText(
    private val context: RunContext
) {
    fun read(
        line: VmLine,
        contains: String?
    ): List<RecognizedLine> = guestScreen(context, line).read().filter { contains == null || it.text.contains(contains, ignoreCase = true) }

    /** Waits until [text] appears (or, with [absent], disappears); returns the matches seen last. */
    fun waitFor(
        line: VmLine,
        text: String,
        exact: Boolean,
        absent: Boolean,
        timeoutMs: Long
    ): List<RecognizedLine> {
        val screen = guestScreen(context, line)
        if (absent) {
            screen.waitGone(text, timeoutMs, exact)
            return emptyList()
        }
        return screen.waitFor(text, timeoutMs, exact)
    }
}
