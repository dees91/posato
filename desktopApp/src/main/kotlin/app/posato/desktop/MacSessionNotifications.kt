package app.posato.desktop

import app.posato.feature.notifications.NotificationPermission
import app.posato.feature.notifications.SessionNotificationPlatform
import kotlin.concurrent.thread

internal object MacNotificationsNative {
    init {
        check(MacPresenceNative.isLoaded())
    }

    @JvmStatic
    external fun permission(): Int

    @JvmStatic
    external fun requestPermission(): Int

    @JvmStatic
    external fun schedule(
        identifier: String,
        title: String,
        body: String,
        seconds: Double,
    )

    @JvmStatic
    external fun post(
        title: String,
        body: String,
    )

    @JvmStatic
    external fun cancel(identifier: String)

    @JvmStatic
    external fun readFlag(key: String): Int

    @JvmStatic
    external fun writeFlag(
        key: String,
        value: Boolean,
    )
}

internal object MacSessionNotifications : SessionNotificationPlatform {
    override val receivedPauseNeedsResume: Boolean = true

    override fun permission(handler: (NotificationPermission) -> Unit) {
        thread(name = "notification-permission", isDaemon = true) { handler(permissionOf(MacNotificationsNative.permission())) }
    }

    override fun requestPermission(handler: (NotificationPermission) -> Unit) {
        thread(name = "notification-request", isDaemon = true) { handler(permissionOf(MacNotificationsNative.requestPermission())) }
    }

    override fun scheduleEnd(
        atEpochMillis: Long,
        title: String,
        body: String,
    ) {
        val seconds = (atEpochMillis - System.currentTimeMillis()) / MILLIS_PER_SECOND
        MacNotificationsNative.schedule(END_IDENTIFIER, title, body, seconds)
    }

    override fun cancelEnd() {
        MacNotificationsNative.cancel(END_IDENTIFIER)
    }

    override fun post(
        title: String,
        body: String,
    ) {
        MacNotificationsNative.post(title, body)
    }

    override fun isEnabled(): Boolean {
        return MacNotificationsNative.readFlag(ENABLED_KEY) != 0
    }

    override fun setEnabled(enabled: Boolean) {
        MacNotificationsNative.writeFlag(ENABLED_KEY, enabled)
    }

    override fun wasPermissionAsked(): Boolean {
        return MacNotificationsNative.readFlag(ASKED_KEY) == 1
    }

    override fun markPermissionAsked() {
        MacNotificationsNative.writeFlag(ASKED_KEY, true)
    }

    private fun permissionOf(code: Int): NotificationPermission {
        return when (code) {
            0 -> NotificationPermission.NOT_DETERMINED
            1 -> NotificationPermission.ALLOWED
            else -> NotificationPermission.DENIED
        }
    }

    private const val END_IDENTIFIER: String = "app.posato.macos.session.end"
    private const val ENABLED_KEY: String = "PosatoPauseNotificationsEnabled"
    private const val ASKED_KEY: String = "PosatoPauseNotificationsAsked"
    private const val MILLIS_PER_SECOND: Double = 1_000.0
}
