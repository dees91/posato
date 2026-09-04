package app.posato.feature.session.domain

internal fun interface SessionClock {
    fun currentEpochMillis(): Long
}
