package app.posato.feature.session.domain

import app.posato.feature.sync.domain.SessionId

internal enum class SessionOrigin {
    LOCAL,
    ADOPTED,
}

internal sealed interface StoredSessionIntent {
    data class StartSession(
        val sessionId: SessionId,
        val startEpochMillis: Long,
        val mandatoryEndEpochMillis: Long,
    ) : StoredSessionIntent {
        override fun toString(): String {
            return "StoredSessionIntent.StartSession(redacted)"
        }
    }

    data class EndSession(
        val sessionId: SessionId,
    ) : StoredSessionIntent
}

internal data class SessionSyncWrite(
    val workspaceId: ByteArray,
    val intents: List<StoredSessionIntent>,
) {
    override fun equals(other: Any?): Boolean {
        return other is SessionSyncWrite &&
            workspaceId.contentEquals(other.workspaceId) &&
            intents == other.intents
    }

    override fun hashCode(): Int {
        return 31 * workspaceId.contentHashCode() + intents.hashCode()
    }

    override fun toString(): String {
        return "SessionSyncWrite(redacted)"
    }
}

internal data class SequencedSessionIntent(
    val sequence: Long,
    val workspaceId: ByteArray,
    val intent: StoredSessionIntent,
) {
    override fun equals(other: Any?): Boolean {
        return other is SequencedSessionIntent &&
            sequence == other.sequence &&
            workspaceId.contentEquals(other.workspaceId) &&
            intent == other.intent
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + workspaceId.contentHashCode()
        result = 31 * result + intent.hashCode()
        return result
    }

    override fun toString(): String {
        return "SequencedSessionIntent(redacted)"
    }
}
