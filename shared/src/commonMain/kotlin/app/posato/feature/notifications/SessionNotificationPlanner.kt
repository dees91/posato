package app.posato.feature.notifications

import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionOrigin

internal sealed interface SessionNotificationAction {
    data class ScheduleEnd(
        val endEpochMillis: Long,
    ) : SessionNotificationAction

    data object CancelEnd : SessionNotificationAction

    data object AnnounceStartedElsewhere : SessionNotificationAction

    data object AskPermission : SessionNotificationAction
}

internal object SessionNotificationPlanner {
    /**
     * Only a pause that began somewhere else is announced; a pause the person started here is not.
     * The end notice is scheduled ahead, so it arrives even when the app is closed or suspended, and
     * it is withdrawn only when the pause ends early. The first status after a launch settles
     * notices left from before without announcing anything.
     */
    fun plan(
        previous: LocalSessionStatus?,
        current: LocalSessionStatus,
        firstObservation: Boolean,
    ): List<SessionNotificationAction> {
        if (current !is LocalSessionStatus.Active) {
            return endActions(previous, current, firstObservation)
        }
        val before = previous as? LocalSessionStatus.Active
        val isNewPause = before?.record?.sessionId != current.record.sessionId
        val endMoved = before?.record?.endEpochMillis != current.record.endEpochMillis
        if (!isNewPause && !endMoved) {
            return emptyList()
        }
        val schedule = SessionNotificationAction.ScheduleEnd(current.record.endEpochMillis)
        return when {
            !isNewPause || firstObservation -> listOf(schedule)
            current.origin == SessionOrigin.ADOPTED -> listOf(schedule, SessionNotificationAction.AnnounceStartedElsewhere)
            else -> listOf(schedule, SessionNotificationAction.AskPermission)
        }
    }

    private fun endActions(
        previous: LocalSessionStatus?,
        current: LocalSessionStatus,
        firstObservation: Boolean,
    ): List<SessionNotificationAction> {
        val expired = current is LocalSessionStatus.Ended && current.kind == SessionEndKind.EXPIRED
        return if (!expired && (firstObservation || previous is LocalSessionStatus.Active)) {
            listOf(SessionNotificationAction.CancelEnd)
        } else {
            emptyList()
        }
    }
}
