package app.posato.feature.notifications

import app.posato.feature.schedules.host.ScheduledPause

/**
 * One end notice for the combined pause: the manual session and the scheduled pause that restricts this
 * device. It is scheduled for the latest end and moved when that end moves; a part that ends inside the
 * other never withdraws it, and only a pause that stops before its end does.
 */
internal class CombinedPauseEnds(
    private val now: () -> Long,
) {
    var manualEnd: Long? = null
    private var scheduled: ScheduledPause? = null
    private var scheduledFor: Long? = null

    val effectiveEnd: Long?
        get() {
            val restricting = scheduled?.takeIf { it.restricts }?.endEpochMillis
            return listOfNotNull(manualEnd, restricting).maxOrNull()
        }

    /** Translates the manual planner's end actions into the combined pause's. */
    fun onManual(action: SessionNotificationAction): SessionNotificationAction? {
        return when (action) {
            is SessionNotificationAction.ScheduleEnd -> scheduleFor(effectiveEnd ?: action.endEpochMillis)

            SessionNotificationAction.CancelEnd -> effectiveEnd?.let(::scheduleFor) ?: run {
                scheduledFor = null
                SessionNotificationAction.CancelEnd
            }

            else -> action
        }
    }

    fun onScheduled(pause: ScheduledPause?): SessionNotificationAction? {
        val before = scheduled
        scheduled = pause
        val end = effectiveEnd
        return when {
            end != null && end != scheduledFor -> {
                scheduleFor(end)
            }

            end == null && before?.restricts == true && pause?.restricts != true -> {
                scheduledFor = null
                // A scheduled pause that reached its end leaves its notice to arrive; one ended early withdraws it.
                if (now() < before.endEpochMillis - END_GRACE_MILLIS) SessionNotificationAction.CancelEnd else null
            }

            else -> {
                null
            }
        }
    }

    private fun scheduleFor(end: Long): SessionNotificationAction {
        scheduledFor = end
        return SessionNotificationAction.ScheduleEnd(end)
    }
}

private const val END_GRACE_MILLIS: Long = 5_000L
