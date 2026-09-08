package app.posato.feature.enforcement

public enum class EnforcementOutcome {
    APPLIED,
    CLEARED,
    NOTHING_TO_ENFORCE,
    AUTHORIZATION_REQUIRED,
    INCOMPATIBLE,
    UNAVAILABLE,
    UNKNOWN,
    FAILED,
}

public class EnforcementRequest(
    public val domains: List<String>,
    public val mappingIds: List<String>,
    public val sessionId: String,
    public val sessionStartEpochMillis: Long,
    public val sessionEndEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean {
        return other is EnforcementRequest &&
            domains == other.domains &&
            mappingIds == other.mappingIds &&
            sessionId == other.sessionId &&
            sessionStartEpochMillis == other.sessionStartEpochMillis &&
            sessionEndEpochMillis == other.sessionEndEpochMillis
    }

    override fun hashCode(): Int {
        var result = domains.hashCode()
        result = 31 * result + mappingIds.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + sessionStartEpochMillis.hashCode()
        result = 31 * result + sessionEndEpochMillis.hashCode()
        return result
    }

    override fun toString(): String {
        return "EnforcementRequest(redacted)"
    }
}

public class EnforcementApplyReport(
    public val outcome: EnforcementOutcome,
    public val belowPlatformMinimum: Boolean,
    public val repeatsSystemPrompt: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        return other is EnforcementApplyReport &&
            outcome == other.outcome &&
            belowPlatformMinimum == other.belowPlatformMinimum &&
            repeatsSystemPrompt == other.repeatsSystemPrompt
    }

    override fun hashCode(): Int {
        var result = outcome.hashCode()
        result = 31 * result + belowPlatformMinimum.hashCode()
        result = 31 * result + repeatsSystemPrompt.hashCode()
        return result
    }

    override fun toString(): String {
        return "EnforcementApplyReport(redacted)"
    }
}

public interface EnforcementPort {
    public val reapplyRequiresPrompt: Boolean

    public suspend fun apply(request: EnforcementRequest): EnforcementApplyReport

    public suspend fun clear(): EnforcementOutcome

    public suspend fun status(): EnforcementOutcome

    public suspend fun pollSuspendedExpiry(sessionId: String): Boolean {
        return false
    }
}
