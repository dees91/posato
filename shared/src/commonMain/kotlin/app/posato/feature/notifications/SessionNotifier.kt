package app.posato.feature.notifications

import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.host.ScheduleNotices
import app.posato.feature.schedules.host.ScheduledPause
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.generated.resources.Res
import app.posato.generated.resources.notification_pause_over_body
import app.posato.generated.resources.notification_pause_over_title
import app.posato.generated.resources.notification_pause_started_body
import app.posato.generated.resources.notification_pause_started_resume_body
import app.posato.generated.resources.notification_pause_started_title
import app.posato.generated.resources.notification_schedule_setup_body
import app.posato.generated.resources.notification_schedule_setup_title
import app.posato.generated.resources.notification_schedule_started_body
import app.posato.generated.resources.notification_schedule_started_title
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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
    private val scheduled: ScheduledPauses,
    clock: SessionClock,
    timeFormat: SessionTimeFormat,
) {
    private val notices = SessionNotices(platform, ResourceNoticeTexts(timeFormat, clock), clock::currentEpochMillis)

    public val settings: StateFlow<SessionNotificationSettings> = notices.settings

    public val available: Boolean = platform !== UnavailableSessionNotifications

    public suspend fun run() {
        // Where the monitor extension owns a scheduled pause's notices, the app plans only the manual session's end.
        val pauses = if (scheduled.ownsNotices) scheduled.pause else flowOf(null)
        notices.follow(owner.status, pauses, scheduled::markAnnounced)
    }

    public suspend fun setEnabled(enabled: Boolean) {
        notices.setEnabled(enabled)
        // A monitor that posts while the app is closed reads the switch from the host's next table.
        scheduled.refresh()
    }

    public suspend fun refreshPermission() {
        notices.refreshPermission()
    }

    internal suspend fun onScheduleSaved() {
        notices.askAfterScheduleSaved()
    }

    /** The person asked for notices from Schedules, so the system is asked now. */
    internal suspend fun askNow() {
        notices.askNow()
    }
}

private sealed interface NoticeEvent {
    data class Manual(
        val status: LocalSessionStatus,
    ) : NoticeEvent

    data class Scheduled(
        val pause: ScheduledPause?,
    ) : NoticeEvent
}

internal data class NoticeText(
    val title: String,
    val body: String,
)

internal interface SessionNoticeTexts {
    suspend fun pauseOver(): NoticeText

    suspend fun startedElsewhere(needsResume: Boolean): NoticeText

    suspend fun scheduledStarted(
        name: String,
        endEpochMillis: Long,
    ): NoticeText

    suspend fun scheduledSetupRequired(): NoticeText
}

internal class ResourceNoticeTexts(
    private val timeFormat: SessionTimeFormat,
    private val clock: SessionClock,
) : SessionNoticeTexts {
    override suspend fun scheduledStarted(
        name: String,
        endEpochMillis: Long,
    ): NoticeText {
        val until = timeFormat.formatTime(endEpochMillis, clock.currentEpochMillis())
        return NoticeText(
            getString(Res.string.notification_schedule_started_title),
            getString(Res.string.notification_schedule_started_body, name, until),
        )
    }

    override suspend fun scheduledSetupRequired(): NoticeText {
        return NoticeText(
            getString(Res.string.notification_schedule_setup_title),
            getString(Res.string.notification_schedule_setup_body),
        )
    }

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
    private val texts: SessionNoticeTexts,
    now: () -> Long = { 0L },
) {
    private val mutableSettings = MutableStateFlow(SessionNotificationSettings(platform.isEnabled(), permission = null))
    private var current: LocalSessionStatus? = null
    private val ends = CombinedPauseEnds(now)

    val settings: StateFlow<SessionNotificationSettings> = mutableSettings.asStateFlow()

    /**
     * The permission prompt runs beside the status collector, so an unanswered prompt never holds up
     * a withdrawn or announced notice. Once the person allows notices, the running pause's end is
     * scheduled again, because a request made before permission may not have been kept. The permission
     * read runs beside the collector too, so a pause that starts while it is pending is still seen as new.
     */
    suspend fun follow(
        statuses: Flow<LocalSessionStatus?>,
        scheduled: Flow<ScheduledPause?> = emptyFlow(),
        announced: suspend (Set<OccurrenceKey>, Int) -> Unit = { _, _ -> },
    ) {
        coroutineScope {
            launch { refreshPermission() }
            var previous: LocalSessionStatus? = null
            var firstObservation = true
            val events = merge(statuses.filterNotNull().map { NoticeEvent.Manual(it) }, scheduled.map { NoticeEvent.Scheduled(it) })
            events.collect { event ->
                when (event) {
                    is NoticeEvent.Manual -> {
                        val actions = SessionNotificationPlanner.plan(previous, event.status, firstObservation)
                        previous = event.status
                        current = event.status
                        ends.manualEnd = (event.status as? LocalSessionStatus.Active)?.record?.endEpochMillis
                        firstObservation = false
                        actions.mapNotNull(ends::onManual).forEach { action ->
                            if (action == SessionNotificationAction.AskPermission) launch { askOnce() } else perform(action)
                        }
                    }

                    is NoticeEvent.Scheduled -> {
                        ends.onScheduled(event.pause)?.let { perform(it) }
                        event.pause?.let { announceScheduled(it, announced) }
                    }
                }
            }
        }
    }

    /** A scheduled start is announced once the restrictions hold; a setup notice once per occurrence. */
    private suspend fun announceScheduled(
        pause: ScheduledPause,
        announced: suspend (Set<OccurrenceKey>, Int) -> Unit,
    ) {
        if (!platform.isEnabled()) {
            return
        }
        if (pause.unannounced.isNotEmpty()) {
            val text = texts.scheduledStarted(pause.name, pause.endEpochMillis)
            platform.post(text.title, text.body)
            announced(pause.unannounced, ScheduleNotices.STARTED)
        }
        if (pause.setupUnannounced.isNotEmpty()) {
            val text = texts.scheduledSetupRequired()
            platform.post(text.title, text.body)
            announced(pause.setupUnannounced, ScheduleNotices.SETUP_REQUIRED)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        platform.setEnabled(enabled)
        mutableSettings.update { it.copy(enabled = enabled) }
        val active = current as? LocalSessionStatus.Active
        val end = ends.effectiveEnd
        if (!enabled) {
            platform.cancelEnd()
        } else if (end != null) {
            perform(SessionNotificationAction.ScheduleEnd(end))
        }
        // The system is asked only once, and only after a pause started on this device.
        if (enabled && active?.origin == SessionOrigin.LOCAL && !platform.wasPermissionAsked()) {
            askThenReschedule()
        }
    }

    suspend fun askNow() {
        askThenReschedule()
    }

    /** A schedule saved on this device asks like a first local pause: once, and only while notices are on. */
    suspend fun askAfterScheduleSaved() {
        askOnce()
    }

    suspend fun refreshPermission() {
        val permission = suspendCancellableCoroutine { continuation -> platform.permission { continuation.resume(it) } }
        mutableSettings.update { it.copy(permission = permission) }
    }

    private suspend fun perform(action: SessionNotificationAction) {
        when (action) {
            is SessionNotificationAction.ScheduleEnd -> if (platform.isEnabled()) {
                val text = texts.pauseOver()
                if (platform.isEnabled() && endsAt(action.endEpochMillis)) {
                    platform.scheduleEnd(action.endEpochMillis, text.title, text.body)
                }
            }

            SessionNotificationAction.CancelEnd -> platform.cancelEnd()

            SessionNotificationAction.AnnounceStartedElsewhere -> if (platform.isEnabled()) {
                val text = texts.startedElsewhere(platform.receivedPauseNeedsResume)
                if (platform.isEnabled()) {
                    platform.post(text.title, text.body)
                }
            }

            SessionNotificationAction.AskPermission -> askOnce()
        }
    }

    private suspend fun askOnce() {
        if (!platform.isEnabled() || platform.wasPermissionAsked()) {
            return
        }
        askThenReschedule()
    }

    /** A request made before permission may not have been kept, so an allowed answer schedules the running pause's end again. */
    private suspend fun askThenReschedule() {
        askPermission()
        val end = ends.effectiveEnd
        if (settings.value.permission == NotificationPermission.ALLOWED && end != null) {
            perform(SessionNotificationAction.ScheduleEnd(end))
        }
    }

    /** Loading the text suspends, so the pause may have ended or moved before the notice is scheduled. */
    private fun endsAt(endEpochMillis: Long): Boolean {
        return ends.effectiveEnd == endEpochMillis
    }

    private suspend fun askPermission() {
        platform.markPermissionAsked()
        val permission = suspendCancellableCoroutine { continuation -> platform.requestPermission { continuation.resume(it) } }
        mutableSettings.update { it.copy(permission = permission) }
    }
}
