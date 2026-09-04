package app.posato.feature.session.ui

import androidx.compose.runtime.Immutable
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.LocalSessionStatus.Active
import app.posato.feature.session.domain.LocalSessionStatus.Ended
import app.posato.feature.session.domain.SessionReview
import app.posato.feature.session.domain.SessionReviewDerivation
import app.posato.feature.session.domain.SessionSetupFailure
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.domain.TargetPolicy

internal enum class SessionOperationFailure { LOAD_FAILED, CORRUPTED_SESSION, START_FAILED, END_FAILED }

internal const val DEFAULT_SETUP_MINUTES: Int = 30

@Immutable
internal data class SessionUiState(
    val status: LocalSessionStatus? = null,
    val operationFailure: SessionOperationFailure? = null,
    val isSettingUp: Boolean = false,
    val durationMinutes: Int = DEFAULT_SETUP_MINUTES,
    val setupFailure: SessionSetupFailure? = null,
    val isReviewing: Boolean = false,
    val isReviewReady: Boolean = false,
    val review: SessionReview = SessionReview(),
    val mappingsAccess: LocalApplicationMappingsAccess? = null,
    val confirmingEarlyEnd: Boolean = false,
    val isStarting: Boolean = false,
    val isEnding: Boolean = false,
    val remainingMillis: Long? = null,
    val formattedPreviewEnd: String? = null,
    val formattedReviewEnd: String? = null,
    val formattedActiveEnd: String? = null,
) {
    override fun toString(): String {
        return "SessionUiState(redacted)"
    }
}

internal data class SessionLoadState(
    val status: LocalSessionStatus? = null,
    val failure: SessionOperationFailure? = null,
) {
    override fun toString(): String {
        return "SessionLoadState(redacted)"
    }
}

internal data class SessionSetupDraft(
    val durationMinutes: Int = DEFAULT_SETUP_MINUTES,
    val failure: SessionSetupFailure? = null,
    val isSettingUp: Boolean = false,
    val isReviewing: Boolean = false,
    val resolvedReviewEnd: Long? = null,
)

internal data class SessionTargetsState(
    val policy: TargetPolicy? = null,
    val mappings: LocalApplicationMappingsLoadResult? = null,
) {
    override fun toString(): String {
        return "SessionTargetsState(redacted)"
    }
}

internal enum class SessionCommand { STARTING, ENDING }

internal fun SessionUiState.canEnterSetup(): Boolean {
    return status != null && status !is LocalSessionStatus.Active && operationFailure == null && !isSettingUp
}

internal fun SessionUiState.canStart(): Boolean {
    return isReviewing && isReviewReady && review.actionRequired?.blocksStart != true && !isStarting &&
        (status is LocalSessionStatus.Inactive || status is LocalSessionStatus.Ended)
}

internal fun SessionUiState.canRequestEarlyEnd(): Boolean {
    return status is LocalSessionStatus.Active && !isEnding && !confirmingEarlyEnd
}

internal fun createSessionUiState(
    load: SessionLoadState,
    draft: SessionSetupDraft,
    targets: SessionTargetsState,
    activeCommand: SessionCommand?,
    confirming: Boolean,
    nowMillis: Long,
    timeFormat: SessionTimeFormat,
): SessionUiState {
    val policy = targets.policy
    val mappings = targets.mappings
    val review = if (policy != null && mappings != null) SessionReviewDerivation.derive(policy, mappings) else SessionReview()
    val active = load.status as? Active
    val lastRecord = when (val status = load.status) {
        is Active -> status.record
        is Ended -> status.record
        else -> null
    }
    val previewEnd = if (draft.isSettingUp && !draft.isReviewing) nowMillis + draft.durationMinutes * MILLIS_PER_MINUTE else null

    return SessionUiState(
        status = load.status,
        operationFailure = load.failure,
        isSettingUp = draft.isSettingUp,
        durationMinutes = draft.durationMinutes,
        setupFailure = draft.failure,
        isReviewing = draft.isReviewing,
        isReviewReady = policy != null && mappings != null,
        review = review,
        mappingsAccess = (mappings as? LocalApplicationMappingsLoadResult.Success)?.access,
        confirmingEarlyEnd = confirming,
        isStarting = activeCommand == SessionCommand.STARTING,
        isEnding = activeCommand == SessionCommand.ENDING,
        remainingMillis = active?.let { (it.record.endEpochMillis - nowMillis).coerceAtLeast(0) },
        formattedPreviewEnd = previewEnd?.let(timeFormat::formatTime),
        formattedReviewEnd = draft.resolvedReviewEnd?.let(timeFormat::formatTime),
        formattedActiveEnd = lastRecord?.let { timeFormat.formatTime(it.endEpochMillis) },
    )
}

private const val MILLIS_PER_MINUTE: Long = 60_000L
