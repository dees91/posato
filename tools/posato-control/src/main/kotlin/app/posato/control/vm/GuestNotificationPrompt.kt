package app.posato.control.vm

import app.posato.control.core.RunContext

/**
 * Answers the macOS notification permission banners of Posato and its helper, one after another: each banner's
 * Options menu, which holds Allow, appears only under the pointer.
 */
class GuestNotificationPrompt(
    private val context: RunContext
) {
    fun allow(
        line: VmLine,
        timeoutMs: Long
    ) {
        val screen = guestScreen(context, line)
        screen.waitFor(BANNER_TEXT, timeoutMs)
        val deadline = System.currentTimeMillis() + timeoutMs
        var answered = 0
        while (System.currentTimeMillis() < deadline) {
            val lines = screen.read()
            val banner = lines.filter { it.text.contains(BANNER_TEXT) }.firstOrNull { candidate ->
                lines.any { line -> line.text.contains(APPLICATION_NAME) && kotlin.math.abs(line.centerY - candidate.centerY) < BANNER_HEIGHT }
            } ?: break
            allowBanner(screen, banner)
            answered += 1
        }
        if (answered == 0) throw notOnScreen("$APPLICATION_NAME notification permission")
        screen.waitGone(BANNER_TEXT, timeoutMs)
    }

    private fun allowBanner(
        screen: GuestScreen,
        banner: RecognizedLine
    ) {
        val bannersBefore = screen.read().count { it.text.contains(BANNER_TEXT) }
        screen.session { client -> client.pointer(banner.centerX, banner.centerY, 0) }
        val options = screen.waitFor(OPTIONS_TEXT, HOVER_TIMEOUT_MS).minBy { kotlin.math.abs(it.centerY - banner.centerY) }
        screen.session { client -> client.click(options.centerX, options.centerY) }
        val allow = screen.waitFor(ALLOW_TEXT, HOVER_TIMEOUT_MS, exact = true).first()
        screen.session { client -> client.click(allow.centerX, allow.centerY) }
        val settled = System.currentTimeMillis() + HOVER_TIMEOUT_MS
        while (screen.read().count { it.text.contains(BANNER_TEXT) } >= bannersBefore && System.currentTimeMillis() < settled) {
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val BANNER_TEXT = "Notifications may include"
        const val APPLICATION_NAME = "Posato"
        const val BANNER_HEIGHT = 120
        const val OPTIONS_TEXT = "Options"
        const val ALLOW_TEXT = "Allow"
        const val HOVER_TIMEOUT_MS = 10_000L
        const val POLL_MS = 500L
    }
}
