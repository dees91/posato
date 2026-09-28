package app.posato.control.vm

import app.posato.control.core.RunContext

/** Answers the macOS notification permission banner: its Options menu, which holds Allow, appears only under the pointer. */
class GuestNotificationPrompt(
    private val context: RunContext
) {
    fun allow(
        line: VmLine,
        timeoutMs: Long
    ) {
        val screen = guestScreen(context, line)
        val lines = screen.waitFor(BANNER_TEXT, timeoutMs)
        val banner = lines.first()
        val named = screen.read().any { line ->
            line.text.contains(APPLICATION_NAME) && kotlin.math.abs(line.centerY - banner.centerY) < BANNER_HEIGHT
        }
        if (!named) throw notOnScreen("$APPLICATION_NAME notification permission")
        screen.session { client -> client.pointer(banner.centerX, banner.centerY, 0) }
        val options = screen.waitFor(OPTIONS_TEXT, HOVER_TIMEOUT_MS).minBy { kotlin.math.abs(it.centerY - banner.centerY) }
        screen.session { client -> client.click(options.centerX, options.centerY) }
        val allow = screen.waitFor(ALLOW_TEXT, HOVER_TIMEOUT_MS, exact = true).first()
        screen.session { client -> client.click(allow.centerX, allow.centerY) }
        screen.waitGone(BANNER_TEXT, timeoutMs)
    }

    private companion object {
        const val BANNER_TEXT = "Notifications may include"
        const val APPLICATION_NAME = "Posato"
        const val BANNER_HEIGHT = 120
        const val OPTIONS_TEXT = "Options"
        const val ALLOW_TEXT = "Allow"
        const val HOVER_TIMEOUT_MS = 10_000L
    }
}
