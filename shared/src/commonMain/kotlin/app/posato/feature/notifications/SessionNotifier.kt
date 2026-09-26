package app.posato.feature.notifications

import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.generated.resources.Res
import app.posato.generated.resources.notification_pause_over_body
import app.posato.generated.resources.notification_pause_over_title
import app.posato.generated.resources.notification_pause_started_body
import app.posato.generated.resources.notification_pause_started_resume_body
import app.posato.generated.resources.notification_pause_started_title
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.resume

public data class SessionNotificationSettings(
    val enabled: Boolean,
    val permission: NotificationPermission?,
)

@Inject
@SingleIn(AppScope::class)
public class SessionNotifier internal constructor(
    private val owner: SessionTransitionOwner,
    platform: SessionNotificationPlatform,
) {
    private val notices = SessionNotices(platform)

    public val settings: StateFlow<SessionNotificationSettings> = notices.settings

    public val available: Boolean = platform !== UnavailableSessionNotifications

    public suspend fun run() {
        notices.follow(owner.status)
    }

    public suspend fun setEnabled(enabled: Boolean) {
        notices.setEnabled(enabled)
    }

    public suspend fun refreshPermission() {
        notices.refreshPermission()
    }
}

internal data class NoticeText(
    val title: String,
    val body: String,
)

internal interface SessionNoticeTexts {
    suspend fun pauseOver(): NoticeText

    suspend fun startedElsewhere(needsResume: Boolean): NoticeText
}

internal object ResourceNoticeTexts : SessionNoticeTexts {
    override suspend fun pauseOver(): NoticeText {
        return NoticeText(getString(Res.string.notification_pause_over_title), getString(Res.string.notification_pause_over_body))
    }

    override suspend fun startedElsewhere(needsResume: Boolean): NoticeText {
        val body = if (needsResume) Res.string.notification_pause_started_resume_body else Res.string.notification_pause_started_body
        return NoticeText(getString(Res.string.notification_pause_started_title), getString(body))
    }
}

internal class SessionNotices(
    private val platform: SessionNotificationPlatform,
    private val texts: SessionNoticeTexts = ResourceNoticeTexts,
) {
    private val mutableSettings = MutableStateFlow(SessionNotificationSettings(platform.isEnabled(), permission = null))
    private var current: LocalSessionStatus? = null

    val settings: StateFlow<SessionNotificationSettings> = mutableSettings.asStateFlow()

    /**
     * The permission prompt runs beside the status collector, so an unanswered prompt never holds up
     * a withdrawn or announced notice. Once the person allows notices, the running pause's end is
     * scheduled again, because a request made before permission may not have been kept.
     */
    suspend fun follow(statuses: Flow<LocalSessionStatus?>) {
        coroutineScope {
            refreshPermission()
            var previous: LocalSessionStatus? = null
            var firstObservation = true
            statuses.filterNotNull().collect { status ->
                val actions = SessionNotificationPlanner.plan(previous, status, firstObservation)
                previous = status
                current = status
                firstObservation = false
                actions.forEach { action ->
                    if (action == SessionNotificationAction.AskPermission) {
                        launch { askOnce() }
                    } else {
                        perform(action)
                    }
                }
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        platform.setEnabled(enabled)
        mutableSettings.update { it.copy(enabled = enabled) }
        val active = current as? LocalSessionStatus.Active
        if (!enabled) {
            platform.cancelEnd()
        } else if (active != null) {
            perform(SessionNotificationAction.ScheduleEnd(active.record.endEpochMillis))
        }
        if (enabled && settings.value.permission == NotificationPermission.NOT_DETERMINED) {
            askPermission()
        }
    }

    suspend fun refreshPermission() {
        val permission = suspendCancellableCoroutine { continuation -> platform.permission { continuation.resume(it) } }
        mutableSettings.update { it.copy(permission = permission) }
    }

    private suspend fun perform(action: SessionNotificationAction) {
        when (action) {
            is SessionNotificationAction.ScheduleEnd -> if (platform.isEnabled()) {
                val text = texts.pauseOver()
                if (platform.isEnabled()) {
                    platform.scheduleEnd(action.endEpochMillis, text.title, text.body)
                }
            }

            SessionNotificationAction.CancelEnd -> platform.cancelEnd()

            SessionNotificationAction.AnnounceStartedElsewhere -> if (platform.isEnabled()) {
                val text = texts.startedElsewhere(platform.receivedPauseNeedsResume)
                platform.post(text.title, text.body)
            }

            SessionNotificationAction.AskPermission -> askOnce()
        }
    }

    private suspend fun askOnce() {
        if (!platform.isEnabled() || platform.wasPermissionAsked()) {
            return
        }
        askPermission()
        val active = current as? LocalSessionStatus.Active
        if (settings.value.permission == NotificationPermission.ALLOWED && active != null) {
            perform(SessionNotificationAction.ScheduleEnd(active.record.endEpochMillis))
        }
    }

    private suspend fun askPermission() {
        platform.markPermissionAsked()
        val permission = suspendCancellableCoroutine { continuation -> platform.requestPermission { continuation.resume(it) } }
        mutableSettings.update { it.copy(permission = permission) }
    }
}
