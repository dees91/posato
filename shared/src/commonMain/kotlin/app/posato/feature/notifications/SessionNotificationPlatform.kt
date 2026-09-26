package app.posato.feature.notifications

public enum class NotificationPermission {
    NOT_DETERMINED,
    ALLOWED,
    DENIED,
}

/**
 * The device's local notification center. Nothing leaves the device: notices are scheduled and
 * posted locally, and the preference and the asked-once marker live in the device's own defaults.
 */
public interface SessionNotificationPlatform {
    public val receivedPauseNeedsResume: Boolean

    public fun permission(handler: (NotificationPermission) -> Unit)

    public fun requestPermission(handler: (NotificationPermission) -> Unit)

    public fun scheduleEnd(
        atEpochMillis: Long,
        title: String,
        body: String,
    )

    public fun cancelEnd()

    public fun post(
        title: String,
        body: String,
    )

    public fun isEnabled(): Boolean

    public fun setEnabled(enabled: Boolean)

    public fun wasPermissionAsked(): Boolean

    public fun markPermissionAsked()
}

public object UnavailableSessionNotifications : SessionNotificationPlatform {
    override val receivedPauseNeedsResume: Boolean = false

    override fun permission(handler: (NotificationPermission) -> Unit) {
        handler(NotificationPermission.DENIED)
    }

    override fun requestPermission(handler: (NotificationPermission) -> Unit) {
        handler(NotificationPermission.DENIED)
    }

    override fun scheduleEnd(
        atEpochMillis: Long,
        title: String,
        body: String,
    ) = Unit

    override fun cancelEnd() = Unit

    override fun post(
        title: String,
        body: String,
    ) = Unit

    override fun isEnabled(): Boolean {
        return false
    }

    override fun setEnabled(enabled: Boolean) = Unit

    override fun wasPermissionAsked(): Boolean {
        return true
    }

    override fun markPermissionAsked() = Unit
}
