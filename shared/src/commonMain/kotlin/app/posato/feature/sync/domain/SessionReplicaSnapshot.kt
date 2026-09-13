package app.posato.feature.sync.domain

internal class SessionReplicaSnapshot(
    val context: SyncContext,
    val revision: Long,
    val projection: SyncProjection,
    val terminalExpiryFacts: Set<SessionId>,
) {
    override fun toString(): String {
        return "SessionReplicaSnapshot(redacted)"
    }
}
