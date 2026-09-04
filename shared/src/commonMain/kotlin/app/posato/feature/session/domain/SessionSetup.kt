package app.posato.feature.session.domain

import app.posato.feature.sync.domain.SyncFormatLimits

internal object SessionLimits {
    const val MIN_DURATION_MINUTES: Int = 5
    const val MAX_DURATION_MILLIS: Long = SyncFormatLimits.MAX_SESSION_DURATION_MILLIS
    const val MAX_DURATION_MINUTES: Int = (MAX_DURATION_MILLIS / MILLIS_PER_MINUTE).toInt()
    const val MIN_DURATION_MILLIS: Long = MIN_DURATION_MINUTES * MILLIS_PER_MINUTE
}

internal enum class SessionSetupFailure { TOO_SHORT, TOO_LONG, NOT_IN_FUTURE }

internal sealed interface SessionSetupResult {
    data class Valid(
        val endEpochMillis: Long,
    ) : SessionSetupResult

    data class Invalid(
        val reason: SessionSetupFailure,
    ) : SessionSetupResult
}

internal object SessionSetup {
    fun validateDuration(
        minutes: Int,
        nowEpochMillis: Long,
    ): SessionSetupResult {
        return when {
            minutes < SessionLimits.MIN_DURATION_MINUTES -> {
                SessionSetupResult.Invalid(SessionSetupFailure.TOO_SHORT)
            }

            minutes > SessionLimits.MAX_DURATION_MINUTES -> {
                SessionSetupResult.Invalid(SessionSetupFailure.TOO_LONG)
            }

            else -> {
                SessionSetupResult.Valid(nowEpochMillis + minutes * MILLIS_PER_MINUTE)
            }
        }
    }

    fun validateEndTime(
        endEpochMillis: Long,
        nowEpochMillis: Long,
    ): SessionSetupResult {
        return when {
            endEpochMillis <= nowEpochMillis -> {
                SessionSetupResult.Invalid(SessionSetupFailure.NOT_IN_FUTURE)
            }

            endEpochMillis - nowEpochMillis > SessionLimits.MAX_DURATION_MILLIS -> {
                SessionSetupResult.Invalid(SessionSetupFailure.TOO_LONG)
            }

            endEpochMillis - nowEpochMillis < SessionLimits.MIN_DURATION_MILLIS -> {
                SessionSetupResult.Invalid(SessionSetupFailure.TOO_SHORT)
            }

            else -> {
                SessionSetupResult.Valid(endEpochMillis)
            }
        }
    }
}

private const val MILLIS_PER_MINUTE: Long = 60_000L
