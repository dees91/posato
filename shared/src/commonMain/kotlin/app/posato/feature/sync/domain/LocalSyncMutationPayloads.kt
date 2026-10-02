package app.posato.feature.sync.domain

import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.normalizeApplicationPolicyNameNfc

internal fun LocalSyncMutation.toPayload(): SyncOperationPayload? {
    return when (this) {
        is LocalSyncMutation.PresentDomain -> {
            SyncOperationPayload.DomainPresent(domain, setId)
        }

        is LocalSyncMutation.RemoveDomain -> {
            SyncOperationPayload.DomainAbsent(domain, setId)
        }

        is LocalSyncMutation.PutPauseSet,
        is LocalSyncMutation.RemovePauseSet,
        is LocalSyncMutation.ChoosePauseSetDefault,
        LocalSyncMutation.EnablePauseSets -> {
            toPauseSetPayload()
        }

        is LocalSyncMutation.StartSession -> {
            SyncOperationPayload.SessionStart(sessionId, startEpochMillis, mandatoryEndEpochMillis, setId)
                .takeIf { payload ->
                    val duration = payload.mandatoryEndEpochMillis - payload.startEpochMillis
                    payload.startEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS &&
                        payload.mandatoryEndEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS &&
                        duration in 1..SyncFormatLimits.MAX_SESSION_DURATION_MILLIS
                }
        }

        is LocalSyncMutation.EndSession -> {
            SyncOperationPayload.SessionEnd(sessionId)
        }

        is LocalSyncMutation.PutSchedule -> {
            val normalized = normalizeApplicationPolicyNameNfc(name.trim())
            SyncOperationPayload.SchedulePut(scheduleId, normalized, weekdays, startMinute, endMinute, enabled, setId)
                .takeIf { payload -> scheduleId.value.isUuidV4() && ScheduleWireRules.isValid(payload) }
        }

        is LocalSyncMutation.RemoveSchedule -> {
            SyncOperationPayload.ScheduleRemove(scheduleId).takeIf { scheduleId.value.isUuidV4() }
        }

        is LocalSyncMutation.SkipOccurrence -> {
            SyncOperationPayload.ScheduleSkip(occurrence)
                .takeIf { occurrence.isAuthorable(authorLocalDate) }
        }

        is LocalSyncMutation.EndOccurrence -> {
            SyncOperationPayload.ScheduleOccurrenceEnd(occurrence)
                .takeIf { occurrence.isAuthorable(authorLocalDate) }
        }
    }
}

private fun LocalSyncMutation.toPauseSetPayload(): SyncOperationPayload? {
    return when (this) {
        is LocalSyncMutation.PutPauseSet -> {
            SyncOperationPayload.PauseSetPut(setId, normalizeApplicationPolicyNameNfc(name.trim()))
                .takeIf { payload -> ScheduleWireRules.isValidName(payload.name) }
        }

        is LocalSyncMutation.RemovePauseSet -> {
            SyncOperationPayload.PauseSetRemove(setId)
        }

        is LocalSyncMutation.ChoosePauseSetDefault -> {
            SyncOperationPayload.PauseSetDefault(setId)
        }

        LocalSyncMutation.EnablePauseSets -> {
            SyncOperationPayload.PauseSetsEnabled
        }

        else -> {
            null
        }
    }
}

/** A writer never authors a fact for a date that is not real or is more than 400 days after its own local date. */
private fun ScheduleOccurrenceRef.isAuthorable(authorLocalDate: ScheduleDate): Boolean {
    return scheduleId.value.isUuidV4() &&
        ScheduleWireRules.isValidDate(date) &&
        date.epochDay - authorLocalDate.epochDay <= ScheduleLimits.MAX_FACT_DAYS_AHEAD
}
