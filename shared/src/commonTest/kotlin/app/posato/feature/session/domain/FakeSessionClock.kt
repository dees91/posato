package app.posato.feature.session.domain

internal class FakeSessionClock(
    var nowEpochMillis: Long,
) : SessionClock {
    override fun currentEpochMillis(): Long {
        return nowEpochMillis
    }
}
